# Health Connect / Glance keep rules are provided by their AARs.
-keepattributes *Annotation*

# Glance maps each receiver to its widget by the widget's class name (updateAll finds ids that
# way, and a running session keeps the widget that started it). R8's horizontal class merging
# folded SustenanceWidget and FastingWidget into one class, so updateAll on either reached both
# widgets' ids and the fasting widget sometimes rendered the dashboard. Pinning the widget
# classes keeps them distinct.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

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
