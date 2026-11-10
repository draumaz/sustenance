package xyz.draumaz.sustenance.util

import com.google.ai.client.generativeai.type.QuotaExceededException
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class FoodNutrients(
    val foodItem: String,
    val servingSize: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val saturatedFat: Double,
    val sodium: Double,
    /** Gemini's notes on the values it had to guess; empty when the item didn't come from Gemini. */
    val explanation: List<String> = emptyList(),
)

class GeminiManager(
    private val apiKey: String,
    private val modelName: String = modelName(DEFAULT_MODEL)
) {
    companion object {
        /** Default model setting, without the "gemini-" prefix (that's how it's entered in Settings). */
        const val DEFAULT_MODEL = "3.5-flash-lite"

        private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta"

        /** Full model name for a model setting, e.g. "3.5-flash-lite" → "gemini-3.5-flash-lite". */
        fun modelName(setting: String): String {
            val trimmed = setting.trim().ifEmpty { DEFAULT_MODEL }
            return if (trimmed.startsWith("gemini-")) trimmed else "gemini-$trimmed"
        }
    }

    // Food logging talks about bodies and portions, which the default filters sometimes trip on.
    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    )

    private val model = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            // Every field is required and non-null, so the model can't skip the explanation or leave unlisted nutrients out.
            responseSchema = Schema.obj(
                "food",
                "Nutrients for the pictured food",
                Schema.str("food_item", "Name of the food"),
                Schema.str("serving_size", "Serving weight in grams, e.g. '150g'"),
                Schema.double("calories", "kcal"),
                Schema.double("protein", "grams"),
                Schema.double("carbs", "grams"),
                Schema.double("fat", "grams"),
                Schema.double("fiber", "grams"),
                Schema.double("sugar", "grams"),
                Schema.double("saturated_fat", "grams"),
                Schema.double("sodium", "milligrams"),
                Schema.arr("explanation", "One point per estimated value", Schema.str("point", "One brief sentence")),
            )
        },
        safetySettings = safetySettings,
    )

    // The food model is locked to the nutrient JSON schema, so the day summary needs its own
    // model and schema. Lazy: most instances only ever analyse photos.
    private val summaryModel by lazy {
        GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                // Split into parts so the card can lay them out, rather than one wall of text.
                responseSchema = Schema.obj(
                    "summary",
                    "A kind summary of the user's day",
                    Schema.str("headline", "One short, warm sentence summing up the day"),
                    Schema.arr("points", "One per goal that is over or short, naming the foods that caused it", Schema.str("point", "One brief sentence")),
                    Schema.arr("tomorrow", "One or two gentle, concrete suggestions for the next day", Schema.str("tip", "One brief sentence")),
                )
            },
            safetySettings = safetySettings,
        )
    }

    suspend fun verifyModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = model.generateContent("ping")
            if (response.text != null) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Empty response"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("GeminiManager", "Verification failed", e)
            val msg = e.localizedMessage ?: e.message ?: e.javaClass.simpleName
            Result.failure(Exception(msg))
        }
    }

    suspend fun analyzeFoodImages(bitmaps: List<Bitmap>, additionalInfo: String? = null): Result<FoodNutrients> {
        // Batch mode can be submitted with typed text and no photos. With nothing to see, the
        // image prompt leaves the model guessing, so look the food up instead.
        if (bitmaps.isEmpty() && !additionalInfo.isNullOrBlank()) return analyzeFoodText(additionalInfo.trim())
        return analyzeImages(bitmaps, additionalInfo)
    }

    private suspend fun analyzeImages(bitmaps: List<Bitmap>, additionalInfo: String?): Result<FoodNutrients> = withContext(Dispatchers.IO) {
        try {
            val scaledBitmaps = bitmaps.map { bitmap ->
                // Large enough to keep nutrition label text legible.
                val maxDim = 1024
                if ((bitmap.width > maxDim) || (bitmap.height > maxDim)) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val (w, h) = if (ratio > 1) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                    bitmap.scale(w, h, filter = true)
                } else bitmap
            }

            val response = model.generateContent(
                content {
                    scaledBitmaps.forEach { image(it) }
                    var prompt = """
                        Return the nutrients for the food in the image(s) as JSON: food_item, serving_size (weight in grams, e.g. '150g'), calories (kcal), protein, carbs, fat, fiber, sugar, saturated_fat (all in grams), sodium (in milligrams), explanation. Use plain numbers for nutrient values.

                        Nutrition labels come first. If any image shows a nutrition facts label, copy every value printed on it exactly, for the serving size printed on it. Never estimate, round or "correct" a value the label shows. Convert units only where needed (kJ to kcal only if kcal is missing; salt in grams to sodium in mg by multiplying by 400).

                        Every nutrient field must have a real value. If the label leaves a nutrient out (often fiber, sugar, saturated fat or sodium), estimate it for that serving from the food, its ingredients and similar products. Don't return 0 just because a value isn't printed; use 0 only when the food genuinely contains none.

                        Without a label, estimate everything from what you see.

                        explanation is an array of plain-text points (no markdown), one per value you estimated rather than read from a label. Never include values read from a label. Each point is one brief sentence naming the specific evidence (restaurant or brand, ingredient, visual size cue, typical recipe) and the resulting number, following patterns like '[Source]'s [ingredient] is usually [preparation], putting [nutrient] around [amount].' or '[Object] looks about [size], so roughly [amount] total.' Only mention a restaurant or brand if it is visible in the image or given in the context. Without a label every value is estimated, so the explanation must never be empty: cover at least the portion size, calories and each macro. Leave it empty only when every value came from a label.
                    """.trimIndent()
                    if (!additionalInfo.isNullOrBlank()) {
                        prompt += "\n\nContext: $additionalInfo"
                    }
                    text(prompt)
                }
            )
            
            scaledBitmaps.forEachIndexed { index, scaled ->
                if (scaled != bitmaps[index]) scaled.recycle()
            }
            
            val text = try {
                response.text
            } catch (e: Exception) {
                val candidate = response.candidates.firstOrNull()
                val reason = candidate?.finishReason
                val msg = e.localizedMessage ?: e.message ?: e.toString()
                return@withContext Result.failure(Exception(
                    if (msg.contains("Unexpected Response", ignoreCase = true)) "No response from Gemini"
                    else if (reason != null) "Gemini error ($reason): $msg" else "Gemini error: $msg"
                ))
            } ?: return@withContext Result.failure(Exception("No response from Gemini"))
            
            parseNutrients(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("GeminiManager", "Analysis failed", e)
            if (e.isQuotaExceeded()) return@withContext Result.failure(GeminiQuotaException())
            val msg = e.localizedMessage ?: e.message
            val displayError = if (!msg.isNullOrBlank() && !msg.contains("Unexpected Response", ignoreCase = true)) msg else "No response from Gemini"
            Result.failure(Exception(displayError))
        }
    }

    /** Parses the nutrient JSON, tolerating prose or code fences around it. */
    private fun parseNutrients(text: String): Result<FoodNutrients> {
        val jsonStart = text.indexOf("{")
        val jsonEnd = text.lastIndexOf("}")
        if (jsonStart == -1 || jsonEnd < jsonStart) {
            return Result.failure(Exception("No response from Gemini"))
        }
        val json = JSONObject(text.substring(jsonStart, jsonEnd + 1))
        return Result.success(FoodNutrients(
            foodItem = json.optString("food_item", "Unknown Food"),
            servingSize = json.optString("serving_size", "1 serving"),
            calories = json.optDouble("calories", 0.0),
            protein = json.optDouble("protein", 0.0),
            carbs = json.optDouble("carbs", 0.0),
            fat = json.optDouble("fat", 0.0),
            fiber = json.optDouble("fiber", 0.0),
            sugar = json.optDouble("sugar", 0.0),
            saturatedFat = json.optDouble("saturated_fat", 0.0),
            sodium = json.optDouble("sodium", 0.0),
            explanation = parseExplanation(json),
        ))
    }

    /**
     * Nutrients for a food described only in text, e.g. "c4 cereal killer". Most such entries
     * name a packaged product or chain menu item whose real nutrition facts are published, so
     * the model gets Google Search grounding to find them instead of estimating.
     *
     * Grounding has its own quota, separate from (and on the free tier far smaller than) plain
     * requests, and some models don't offer it at all. So if the grounded request is refused,
     * retry without search: the model still knows many products' facts, and that beats telling
     * the user their quota is gone while photo logging still works. Only the retry's error is
     * reported.
     */
    private suspend fun analyzeFoodText(description: String): Result<FoodNutrients> = withContext(Dispatchers.IO) {
        try {
            val text = try {
                generateText(foodTextPrompt(description, canSearch = true), withSearch = true)
            } catch (e: GeminiHttpException) {
                Log.w("GeminiManager", "Grounded lookup refused (HTTP ${e.status}), retrying without search: ${e.message}")
                generateText(foodTextPrompt(description, canSearch = false), withSearch = false)
            }
            parseNutrients(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("GeminiManager", "Text analysis failed", e)
            if (e is GeminiHttpException && e.isQuota) return@withContext Result.failure(GeminiQuotaException())
            val msg = e.localizedMessage ?: e.message
            Result.failure(Exception(if (msg.isNullOrBlank()) "No response from Gemini" else msg))
        }
    }

    private fun foodTextPrompt(description: String, canSearch: Boolean): String {
        val lookup = if (canSearch) {
            "search for its official nutrition facts (the brand's own site, its product label, or a reputable nutrition database) and copy the values exactly. Never estimate, round or \"correct\" a value you found. If sources differ, prefer the brand's own and the user's region."
        } else {
            "use its official published nutrition facts as you know them (the brand's label or website) rather than estimating from similar products."
        }
        val source = if (canSearch) "Values from C4's published nutrition facts for one 16 fl oz can." else "Values from C4's published nutrition facts for one 16 fl oz can, from memory (not looked up)."
        return """
            The user typed this to log something they ate or drank: "$description"

            First work out exactly what it is. Entries are often terse, lowercase, misspelled or shorthand, and usually name a brand plus a product or flavour: "c4 cereal killer" means the Cereal Killer flavour of the C4 energy drink, "big mac" means the McDonald's burger. Read the words as a brand and product name before reading them literally.

            If it is a packaged product or a restaurant/chain item, $lookup Use one serving as sold (one can, one bar, one menu item) unless the user gives a quantity or size (e.g. "2 cans", "large", "half"); then scale the published values to it.

            Only estimate when the food is generic or homemade, or no published facts exist; then estimate from typical recipes and portions. If the published facts leave a nutrient out (often fiber, saturated fat or sodium), estimate just that one for the serving. Don't return 0 just because a value isn't published; use 0 only when the food genuinely contains none.

            Reply with only one JSON object, no markdown and no other text, with these fields:
            food_item: the full name, with brand and flavour, e.g. "C4 Energy Cereal Killer".
            serving_size: the serving's weight in grams, e.g. "473g" (for drinks, 1 ml is about 1 g).
            calories (kcal), protein, carbs, fat, fiber, sugar, saturated_fat (all grams), sodium (milligrams): plain numbers.
            explanation: an array of brief plain-text sentences. If you used published facts, the first names the source and serving, e.g. "$source" Then add one point per value you estimated, naming the evidence and the resulting number. Never leave it empty.
        """.trimIndent()
    }

    /**
     * One generateContent call over REST, returning the reply text. The legacy SDK has no Google
     * Search tool, hence REST. No responseSchema either: not every model accepts one alongside
     * tools, so the prompt asks for JSON and [parseNutrients] digs it out of the reply.
     * Throws [GeminiHttpException] when the API refuses the request.
     */
    private fun generateText(prompt: String, withSearch: Boolean): String {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("safetySettings", JSONArray().apply {
                listOf("HARASSMENT", "HATE_SPEECH", "SEXUALLY_EXPLICIT", "DANGEROUS_CONTENT").forEach {
                    put(JSONObject().put("category", "HARM_CATEGORY_$it").put("threshold", "BLOCK_NONE"))
                }
            })
        if (withSearch) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val connection = URL("$API_BASE/models/$modelName:generateContent").openConnection() as HttpURLConnection
        val (status, responseText) = try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            // Searching and reading pages takes noticeably longer than a plain answer.
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }

        if (status !in 200..299) {
            val error = try { JSONObject(responseText).optJSONObject("error") } catch (_: Exception) { null }
            val msg = error?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP $status"
            throw GeminiHttpException(status, error?.optString("status").orEmpty(), msg)
        }

        // Grounded answers can come back split across several text parts.
        val candidate = JSONObject(responseText).optJSONArray("candidates")?.optJSONObject(0)
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
        val text = parts?.let { arr ->
            (0 until arr.length()).joinToString("") { arr.optJSONObject(it)?.optString("text").orEmpty() }
        }.orEmpty()
        if (text.isBlank()) {
            val reason = candidate?.optString("finishReason").orEmpty()
            throw Exception(if (reason.isNotBlank() && reason != "STOP") "Gemini error ($reason)" else "No response from Gemini")
        }
        return text
    }

    /** Reads the explanation array, tolerating a plain string in case the model ignores the format. */
    private fun parseExplanation(json: JSONObject): List<String> {
        val points = json.optJSONArray("explanation")
            ?.let { arr -> List(arr.length()) { arr.optString(it) } }
            ?: json.optString("explanation").lines()
        return points
            .map { it.trim().trimStart('-', '•', '*').trim().replaceFirstChar { c -> c.titlecase() } }
            .filter { it.isNotEmpty() }
    }

    /**
     * Writes the short, kind day summary shown at the top of Insights. [dayReport] is the plain
     * text facts (goals, totals, food logs) built by the Insights screen; [language] is the
     * English name of the language to answer in. [finishedDay] when the facts are yesterday's,
     * so the tips are for today rather than tomorrow.
     */
    suspend fun summarizeDay(dayReport: String, language: String, finishedDay: Boolean): Result<DaySummaryText> = withContext(Dispatchers.IO) {
        try {
            val day = if (finishedDay) {
                "yesterday, a finished day (nothing is logged today yet). Talk about it in the past tense, and make the suggestions for today"
            } else "the user's day so far"
            val next = if (finishedDay) "today" else "tomorrow"
            val prompt = """
                You are a warm, encouraging nutrition coach inside a food tracking app. Using only the facts below about $day, summarise it as JSON.

                headline: one short sentence (under 15 words) capturing the day overall.
                points: one sentence per goal they went over or fell short of, naming the logged foods that caused it and roughly how much each contributed. Skip goals that are fine. Empty if every goal is on track.
                tomorrow: one or two concrete, gentle suggestions for doing better $next. If everything went well, one suggestion to keep it up.

                Tone: kind, reassuring and never judgemental or alarming. One day doesn't define progress, so frame misses as easy to adjust, not as failures. Talk to the user directly ("you"). Never give medical advice or mention weight loss unless the facts do.

                Plain text in every field: no markdown, no emoji. Give amounts as a number and unit, e.g. "300 kcal" or "12 g". Write in $language.

                Facts:
                $dayReport
            """.trimIndent()
            val text = summaryModel.generateContent(prompt).text
                ?: return@withContext Result.failure(Exception("No response from Gemini"))
            val json = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
            fun list(name: String) = json.optJSONArray(name)
                ?.let { arr -> List(arr.length()) { arr.optString(it).replace("**", "").trim() } }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val summary = DaySummaryText(
                headline = json.optString("headline").replace("**", "").trim(),
                points = list("points"),
                tomorrow = list("tomorrow"),
            )
            if (summary.headline.isEmpty() && summary.points.isEmpty() && summary.tomorrow.isEmpty()) {
                Result.failure(Exception("No response from Gemini"))
            } else Result.success(summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("GeminiManager", "Day summary failed", e)
            Result.failure(if (e.isQuotaExceeded()) GeminiQuotaException() else e)
        }
    }

    suspend fun analyzeFoodImage(bitmap: Bitmap, additionalInfo: String? = null): Result<FoodNutrients> = analyzeFoodImages(listOf(bitmap), additionalInfo)
}

/** Gemini's day summary for Insights, in the parts the card lays out. */
data class DaySummaryText(
    val headline: String,
    /** One per goal that's over or short, with what caused it. */
    val points: List<String>,
    /** Suggestions for tomorrow. */
    val tomorrow: List<String>,
)

/**
 * The key has run out of Gemini quota (HTTP 429 / RESOURCE_EXHAUSTED), almost always the free
 * tier's daily cap. Kept apart from other failures so the UI can explain it instead of echoing
 * the raw error.
 */
class GeminiQuotaException : Exception("Gemini quota exceeded")

/** The REST API refused a request; [apiStatus] is Google's status string, e.g. RESOURCE_EXHAUSTED. */
private class GeminiHttpException(val status: Int, val apiStatus: String, message: String) : Exception(message) {
    val isQuota get() = status == 429 || apiStatus == "RESOURCE_EXHAUSTED"
}

/** The SDK usually throws [QuotaExceededException], but some paths only carry the status text. */
private fun Throwable.isQuotaExceeded(): Boolean {
    if (this is QuotaExceededException) return true
    val msg = message.orEmpty()
    return msg.contains("RESOURCE_EXHAUSTED") || msg.contains("429")
}
