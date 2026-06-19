# kotlinx.serialization — keep @Serializable metadata and generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.echon.voice.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.echon.voice.model.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.echon.voice.model.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# Tink (EncryptedSharedPreferences) references Error Prone annotations that are
# compile-only and absent at runtime — safe to ignore.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# LiveKit / WebRTC
-keep class org.webrtc.** { *; }
-keep class io.livekit.android.** { *; }
-dontwarn org.webrtc.**
