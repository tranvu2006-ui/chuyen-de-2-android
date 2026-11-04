# Proguard Rules — App Internal
-keep class com.chuyen_de_2.foodorder.core.data.model.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
