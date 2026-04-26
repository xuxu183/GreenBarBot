# OpenCV
-keep class org.opencv.** { *; }

# GreenBarBot services (keep for reflection / accessibility)
-keep class com.greenbarbot.** { *; }

# AndroidX
-keepattributes *Annotation*
-dontwarn org.opencv.**
