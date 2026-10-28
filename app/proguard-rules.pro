# Health Connect / Glance keep rules are provided by their AARs.
-keepattributes *Annotation*

# Keep Google Generative AI SDK classes and exceptions
-keep class com.google.ai.client.generativeai.** { *; }
-keepclassmembers class com.google.ai.client.generativeai.** { *; }

# Keep kotlinx.serialization classes used by Gemini SDK
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }


# Strip debug/verbose/info logging from release builds (warnings and errors are kept).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
