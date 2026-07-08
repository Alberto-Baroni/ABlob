# Regole di ProGuard/R8 per l'applicazione ABlob

# 1. Regole per Kotlinx Serialization
# Mantiene gli attributi necessari per le annotazioni e le firme dei tipi generici
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Mantiene le classi serializzabili e i loro membri
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Mantiene i Companion object delle classi serializzabili (necessari per trovare i serializzatori)
-keepclassmembers class * {
    *** Companion;
}

# Mantiene i metodi sintetici di scrittura generati dal compilatore Kotlin per la serializzazione
-keepclassmembers class * {
    *** write$Self(...);
}

# Evita l'offuscamento dei nomi delle proprietà nelle classi serializzabili se necessario
# (in generale Kotlinx Serialization gestisce questo tramite R8, ma keepattributes Signature aiuta)

# 2. Regole per Android Security Crypto e Tink
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.errorprone.annotations.**

# 3. Regole per Android Biometrics
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# 4. Regole per OkHttp e Okio
-keepattributes Signature,Markup,SourceFile,LineNumberTable
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    *** publicSuffixListBytes;
    *** publicSuffixExceptionListBytes;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# 5. Regole per Coil (Caricamento Immagini)
-keep class coil.** { *; }
-dontwarn coil.**
