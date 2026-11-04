# ============================================================================
# ProGuard / R8 Rules for SafetyLog / InspectField Production Release
# ============================================================================

# --- Room Database Rules ---
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.TypeConverter
-keep class com.safetylog.app.data.Converters { *; }
-keep class com.safetylog.app.data.model.** { *; }
-keep class com.safetylog.app.data.dao.** { *; }

# --- Security & Keystore Crypto (MasterKey & EncryptedSharedPreferences) ---
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Google Credential Manager & Identity ---
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# --- Google Play Billing Client ---
-keep class com.android.billingclient.** { *; }

# --- Google Mobile Ads (AdMob) & Unity Ads Mediation ---
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-keep public class com.google.android.gms.ads.internal.client.** { *; }
-keep class com.google.ads.mediation.unity.** { *; }
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-dontwarn com.unity3d.ads.**
-dontwarn com.unity3d.services.**

# --- Meta Audience Network Mediation ---
-keep class com.google.ads.mediation.facebook.** { *; }
-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.ads.**
-dontwarn com.facebook.infer.annotation.**

# --- Domain & Auth Models ---
-keep class com.safetylog.app.data.auth.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Coil Image Loading ---
-keep class coil.** { *; }
-dontwarn coil.**

# --- BuildConfig ---
-keep class com.safetylog.app.BuildConfig { *; }

