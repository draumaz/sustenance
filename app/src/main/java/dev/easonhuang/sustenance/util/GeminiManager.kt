package dev.easonhuang.sustenance.util

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
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
    val sodium: Double
)

class GeminiManager(apiKey: String) {
    private val model = GenerativeModel(
        modelName = "gemini-3.1-flash-lite", // Do not change this
        apiKey = apiKey
    )

    suspend fun analyzeFoodImages(bitmaps: List<Bitmap>, additionalInfo: String? = null): Result<FoodNutrients> = withContext(Dispatchers.IO) {
        try {
            val scaledBitmaps = bitmaps.map { bitmap ->
                val maxDim = 1024
                if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val (w, h) = if (ratio > 1) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                    Bitmap.createScaledBitmap(bitmap, w, h, true)
                } else bitmap
            }

            Log.d("GeminiManager", "Sending ${scaledBitmaps.size} images to Gemini... Info: $additionalInfo")
            
            val response = model.generateContent(
                content {
                    scaledBitmaps.forEach { image(it) }
                    var prompt = "Analyze these food images. Return a JSON object with: food_item, serving_size, calories, protein, carbs, fat, fiber, sugar, saturated_fat, and sodium. Use numbers for nutrients. You MUST specify serving_size as a weight in grams (e.g., '150g'). Estimate the weight if not known. Return ONLY the JSON."
                    if (!additionalInfo.isNullOrBlank()) {
                        prompt += " Additional context from user: $additionalInfo"
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
                val reason = response.candidates.firstOrNull()?.finishReason
                return@withContext Result.failure(Exception("Gemini error ($reason): ${e.message}"))
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
                sodium = json.optDouble("sodium", 0.0)
            ))
        } catch (e: Exception) {
            Log.e("GeminiManager", "Analysis failed", e)
            Result.failure(Exception("${e.javaClass.simpleName}: ${e.localizedMessage}"))
        }
    }

    suspend fun analyzeFoodImage(bitmap: Bitmap, additionalInfo: String? = null): Result<FoodNutrients> = analyzeFoodImages(listOf(bitmap), additionalInfo)
}
