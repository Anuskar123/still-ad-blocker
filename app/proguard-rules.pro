# AndroidX, Compose, coroutines and WorkManager ship their own consumer rules.
# Keep the worker constructor used by WorkManager's default reflective factory.
-keep class dev.still.dns.FilterUpdateWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# Preserve model names in diagnostics without disabling whole-library shrinking.
-keepnames class dev.still.dns.AppSettings
-keepnames class dev.still.dns.FilterPolicy
-keepnames class dev.still.dns.PacketParser
# No JavaScript bridge is exposed. Keep annotated bridges if one is later added.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
