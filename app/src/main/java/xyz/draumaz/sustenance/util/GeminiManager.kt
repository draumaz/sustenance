package xyz.draumaz.sustenance.util

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    apiKey: String,
    modelName: String = modelName(DEFAULT_MODEL)
) {
    companion object {
        /** Default model setting, without the "gemini-" prefix (that's how it's entered in Settings). */
        const val DEFAULT_MODEL = "3.5-flash-lite"

        /** Full model name for a model setting, e.g. "3.5-flash-lite" → "gemini-3.5-flash-lite". */
        fun modelName(setting: String): String {
            val trimmed = setting.trim().ifEmpty { DEFAULT_MODEL }
            return if (trimmed.startsWith("gemini-")) trimmed else "gemini-$trimmed"
        }
    }

    private val model = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
        )
    )

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

    suspend fun analyzeFoodImages(bitmaps: List<Bitmap>, additionalInfo: String? = null): Result<FoodNutrients> = withContext(Dispatchers.IO) {
        try {
            val scaledBitmaps = bitmaps.map { bitmap ->
                val maxDim = 512
                if ((bitmap.width > maxDim) || (bitmap.height > maxDim)) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val (w, h) = if (ratio > 1) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                    bitmap.scale(w, h, filter = true)
                } else bitmap
            }

            val response = model.generateContent(
                content {
                    scaledBitmaps.forEach { image(it) }
                    var prompt = "Estimate nutrients for food in image as JSON: food_item, serving_size (weight in grams, e.g. '150g'), calories (kcal), protein, carbs, fat, fiber, sugar, saturated_fat (all in grams), sodium (in milligrams), explanation. Use plain numbers for nutrient values. explanation is an array of plain-text points (no markdown), one per value you had to guess rather than read directly from the image (e.g. from a nutrition label). Each point is one brief sentence naming the specific evidence (restaurant or brand, ingredient, visual size cue, typical recipe) and the resulting number, following patterns like '[Source]'s [ingredient] is usually [preparation], putting [nutrient] around [amount].' or '[Object] looks about [size], so roughly [amount] total.' Only mention a restaurant or brand if it is visible in the image or given in the context. Skip values that were shown. Empty array if nothing was guessed."
                    if (!additionalInfo.isNullOrBlank()) {
                        prompt += " Context: $additionalInfo"
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
            
            val jsonStart = text.indexOf("{")
            val jsonEnd = text.lastIndexOf("}")
            if (jsonStart == -1 || jsonEnd == -1) {
                return@withContext Result.failure(Exception("No response from Gemini"))
            }
            
            val json = JSONObject(text.substring(jsonStart, jsonEnd + 1))
            Result.success(FoodNutrients(
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("GeminiManager", "Analysis failed", e)
            val msg = e.localizedMessage ?: e.message
            val displayError = if (!msg.isNullOrBlank() && !msg.contains("Unexpected Response", ignoreCase = true)) msg else "No response from Gemini"
            Result.failure(Exception(displayError))
        }
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

    suspend fun analyzeFoodImage(bitmap: Bitmap, additionalInfo: String? = null): Result<FoodNutrients> = analyzeFoodImages(listOf(bitmap), additionalInfo)
}
