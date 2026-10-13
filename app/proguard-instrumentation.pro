# Test variant only. AndroidJUnitRunner 1.6.2 references the shared Trace API
# from the app classloader. Keep its API stable across the two APKs; production
# release does not consume this rule.
-keep,allowoptimization class androidx.tracing.Trace { *; }
