# kotlinx.serialization: keep generated serializers for the saved profile.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.starshower.run.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.starshower.run.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.starshower.run.core.**$$serializer { *; }
