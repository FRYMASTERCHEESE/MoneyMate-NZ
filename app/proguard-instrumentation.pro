# Test variant only. AndroidJUnitRunner 1.6.2 references the shared Trace API
# from the app classloader. Keep its API stable across the two APKs; production
# release does not consume this rule.
-keep,allowoptimization class androidx.tracing.Trace { *; }
# The separately compiled AndroidX test runtime also shares Kotlin stdlib with
# the app. Its bytecode references the original public runtime API names.
-keep,allowoptimization class kotlin.** { *; }
# These existing white-box tests call app APIs from a separate APK. R8 cannot
# infer those external uses when compiling the app; retain their signatures in
# this variant while allowing method-body optimisation. Do not copy to release.
-keep,allowoptimization class nz.moneymate.app.Store { *; }
-keep,allowoptimization class nz.moneymate.app.SessionVault { *; }
-keep,allowoptimization class nz.moneymate.app.AuthClient { *; }
-keep,allowoptimization class nz.moneymate.app.AuthClient$* { *; }
-keep,allowoptimization class nz.moneymate.app.CloudSync { *; }
-keep,allowoptimization class nz.moneymate.app.CloudSync$* { *; }
-keep,allowoptimization class nz.moneymate.app.BankCsv { *; }
-keep,allowoptimization class nz.moneymate.app.BankCsv$* { *; }
-keep,allowoptimization class nz.moneymate.app.PdfStatement { *; }
-keep,allowoptimization class nz.moneymate.app.PdfStatementReader { *; }
# Back-navigation regression calls these APIs across the test APK boundary.
-keep,allowoptimization class androidx.activity.ComponentActivity { *; }
-keep,allowoptimization class androidx.activity.OnBackPressedDispatcher { *; }
-keep,allowoptimization class androidx.activity.OnBackPressedCallback { *; }
-keep,allowoptimization class nz.moneymate.app.MainActivity { void setAccountBusy(boolean); }
-keep,allowoptimization class nz.moneymate.app.GoogleOAuth { *; }
