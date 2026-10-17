# Backup payload classes are (de)serialized by kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.bottlepost.app.backup.**$$serializer { *; }
-keepclassmembers class com.bottlepost.app.backup.** { *** Companion; }
