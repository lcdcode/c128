# Project-specific ProGuard / R8 rules.
#
# Intentionally empty: the BASIC interpreter and Compose UI used here do not
# rely on reflection, JNI, Parcelable, Gson/Moshi adapters, or any other
# mechanism that R8 would mis-strip. The defaults from
# `proguard-android-optimize.txt` plus the rules bundled with the AndroidX /
# Compose libraries are sufficient.
#
# If you add a feature that uses reflection (annotation processors, dynamic
# Class.forName, kotlinx.serialization, etc.), add the appropriate -keep
# rules here and verify with `./gradlew assembleRelease` plus an on-device
# smoke test.
