# youtubedl-android reflects into its own classes and reads bundled assets.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Jackson (used by the library to parse yt-dlp JSON output)
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
