# kotlinx.serialization: keep generated serializers of our models
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class dev.frontek.reads.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.frontek.reads.**$$serializer { *; }

# Jsoup optional re2j dependency
-dontwarn com.google.re2j.**
