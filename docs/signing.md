# Stable APK signing

The Android package ID remains com.moonlight.school. To install future versions in place, all builds must have the same certificate and nondecreasing versionCode. Every hosted runner's default debug keystore can differ. Existing temporary-debug installations may require one uninstall when migrating; export any local data first.

Generate a dedicated PKCS12 key on a trusted computer with JDK keytool, not in GitHub or CI. Keep a private backup. Example (PowerShell; choose a strong unique password when prompted):

```powershell
keytool -genkeypair -storetype PKCS12 -keystore moonlight-signing.p12 -alias moonlight -keyalg RSA -keysize 3072 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path moonlight-signing.p12).Path)) | Set-Clipboard
```

In repository Settings > Secrets and variables > Actions, create MOONLIGHT_KEYSTORE_BASE64 from the clipboard, and MOONLIGHT_KEYSTORE_PASSWORD from the keystore password. For PKCS12 the key and store password must match. Clear the clipboard afterward. Never commit either value or the private key to the public repository. Use a separate key from other projects. Workflow signing secrets are passed only to the build step; do not run untrusted changes with secrets.

Trigger Android debug APK again. A persistent artifact indicates both secrets were used; temporary-debug indicates no stable key was configured. Compare the SHA-256 certificate fingerprint in the build log across persistent builds. APK filenames and artifact names contain version, run and attempt so old downloads are distinguishable. This does not recover the private key of any previously installed APK. Existing build variants are debug APKs, not production releases.
