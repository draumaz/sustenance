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
)

class GeminiManager(
    apiKey: String,
    modelName: String = "gemini-3.5-flash-lite"
) {
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

            Log.d("GeminiManager", "Sending ${scaledBitmaps.size} images to Gemini... Info: $additionalInfo")
            
            val response = model.generateContent(
                content {
                    scaledBitmaps.forEach { image(it) }
                    var prompt = "Estimate nutrients for food in image as JSON: food_item, serving_size (weight in grams, e.g. '150g'), calories, protein, carbs, fat, fiber, sugar, saturated_fat, sodium. Use numbers for nutrient values."
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
                    if (reason != null) "Gemini error ($reason): $msg" else "Gemini error: $msg"
                ))
            } ?: return@withContext Result.failure(Exception("Empty response from Gemini"))
            
            Log.d("GeminiManager", "Response: $text")
            
            val jsonStart = text.indexOf("{")
            val jsonEnd = text.lastIndexOf("}")
            if (jsonStart == -1 || jsonEnd == -1) {
                return@withContext Result.failure(Exception("No JSON found in response"))
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
            ))
        } catch (e: Exception) {
            Log.e("GeminiManager", "Analysis failed", e)
            val msg = e.localizedMessage ?: e.message
            val displayError = if (!msg.isNullOrBlank()) msg else e.javaClass.simpleName
            Result.failure(Exception(displayError))
        }
    }

    suspend fun analyzeFoodImage(bitmap: Bitmap, additionalInfo: String? = null): Result<FoodNutrients> = analyzeFoodImages(listOf(bitmap), additionalInfo)
}
