# R8 full mode is the default with AGP 8.13.2. Keep optimisation, shrinking,
# and obfuscation enabled; do not add blanket app/Google/ML Kit keep rules.
# Android framework entry points are retained by AGP's generated rules.
# Ads, UMP, and bundled ML Kit supply their own consumer rules (including JNI).
# App persistence uses explicit SQLite columns and JSONObject keys, not reflected
# Java field names or Java serialization, so backups do not require model keeps.
# Retain source locations for useful retraced crash reports; archive mapping.txt
# for each release. proguard-android-optimize.txt retains required annotations.
-keepattributes SourceFile,LineNumberTable
