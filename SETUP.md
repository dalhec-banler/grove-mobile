# Grove Mobile Setup

## Create GitHub Repository

1. Go to https://github.com/new
2. Name: `grove-mobile`
3. Visibility: Public (or Private)
4. **Don't** initialize with README (we already have one)
5. Click "Create repository"

Then push the existing code:

```bash
cd /home/anoffice/dev/mobile-vere/grove-mobile
git push -u origin main
```

## Building Locally

Requires:
- JDK 17+
- Android SDK 34

```bash
./gradlew assembleDebug
```

APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

## Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## CI Builds

Once pushed to GitHub, CI will automatically:
1. Build debug and release APKs
2. Run lint checks
3. Upload APKs as artifacts

Download APKs from the Actions tab after each push.
