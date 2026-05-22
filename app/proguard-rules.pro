# Financier ProGuard Rules
# Add project specific ProGuard rules here.

# Keep Room entities
-keep class com.financier.app.data.local.entity.** { *; }

# Keep Gemini API models
-keep class com.financier.app.data.remote.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.* { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ZXing / Embedded Scanner
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**