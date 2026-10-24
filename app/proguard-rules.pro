# CampusPocket release rules
# Keep data models / clients used with Gson reflection.
-keep class com.ecut.campuspocket.data.remote.** { *; }
-keepclassmembers class com.ecut.campuspocket.data.remote.** {
    <fields>;
    <init>();
}

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.**

# 签名器与接口常量交给 R8 正常混淆，不额外保留。
