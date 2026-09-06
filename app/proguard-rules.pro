# Khutwa — R8 rules for the Play release build (CI only).
# Manifest-declared components (services, receivers, the widget provider,
# the tile) are kept by AGP automatically. Compose and material3 ship their
# own consumer rules. What is left is ours:

# Raw SQLite, no reflection; nothing to keep. Kept explicit for the day
# someone adds a JSON mapper and wonders why fields vanish:
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Crash reports stay readable without a mapping file server
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose UI 1.6 finds lifecycle-compose's LocalLifecycleOwner by reflection;
# R8 stripped it in the first Play build and every launch died with
# "CompositionLocal LocalLifecycleOwner not present" (2026-09-06). Keep it.
-if public class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt
-keep public class androidx.lifecycle.compose.LocalLifecycleOwnerKt {
    public static *** getLocalLifecycleOwner();
}
