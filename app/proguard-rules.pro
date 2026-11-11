# Keep the JavaScript bridge entry points reachable from WebView.
-keepclassmembers class com.islamicapp.noor.WebAppBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.islamicapp.noor.WebAppBridge { *; }

# Media3 / Room
-keep class androidx.media3.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
