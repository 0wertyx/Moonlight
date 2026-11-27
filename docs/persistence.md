# Persistence contract (0.4.2)

Normal Android in-place updates retain the application sandbox. Keep applicationId com.moonlight.school, use the same private signing key for every distributed update, and increase versionCode. CI now refuses to build distribution artifacts without both persistent signing secrets rather than generating incompatible temporary APKs.

The versioned local configuration adopts the existing moonlight_local and moonlight_snapshot SharedPreferences files without resetting or copying them. Theme, personal grade calculator, personal lists, cached grades and timetable, refresh settings, timestamps and selected document URI remain in their existing stores. Existing WebView cookies are managed by Android in the same app sandbox; the server can still expire a session. No cookies are exported, and Android cloud backup remains disabled.

Future storage changes must implement an explicit, idempotent migration from every supported older schema, preserve unknown keys where possible, validate migrated data before committing a schema marker, and test upgrade from an installed older APK. No startup factory reset. Disconnect removes only known source and cache keys; it does not clear unrelated preferences. Explicit source changes may intentionally remove old school records to prevent mixing accounts.

The tests in this commit check schema-transition rules and stable store names, not real Android in-place upgrade or actual WebView session restoration. A future incompatible or broken migration, uninstall, app data clear, device loss or signing key change can still cause loss of data. There is no independent restore backup in this release.
