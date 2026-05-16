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