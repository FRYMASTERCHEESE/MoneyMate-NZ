# Test variant only. AndroidJUnitRunner 1.6.2 references the shared Trace API
# from the app classloader. Keep its API stable across the two APKs; production
# release does not consume this rule.
-keep,allowoptimization class androidx.tracing.Trace { *; }
# The separately compiled AndroidX test runtime also shares Kotlin stdlib with
# the app. Its bytecode references the original public runtime API names.
-keep,allowoptimization class kotlin.** { *; }
