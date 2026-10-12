# kotlinx.serialization (도감 JSON, Pl@ntNet 응답, 내비게이션 경로)
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.plantdex.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Crashlytics 오류 보고에서 줄 번호가 보이도록
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
