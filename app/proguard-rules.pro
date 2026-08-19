# ── Room ORM ──────────────────────────────────────────────────────────────────
# Keep entity data classes (accessed via reflection by Room)
-keep class com.chat.app.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Kotlin serialization / data classes used by Compose ─────────────────────
-keep class com.chat.app.ui.theme.** { *; }
-keepclassmembers class com.chat.app.** { *; }

# ── Coil ─────────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── ZXing QR ─────────────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }

# ── CameraX ──────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── Kotlin coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── AndroidX / Compose ───────────────────────────────────────────────────────
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# ── General Android safety ───────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
