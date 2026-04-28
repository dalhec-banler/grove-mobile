# Grove Mobile

Native Android client for [Grove](https://github.com/dalhec-banler/grove), an Urbit file storage and management app.

Grove Mobile connects to your local or remote Urbit ship and provides a native Android interface for browsing, uploading, and managing files stored in your Grove agent.

## Features

- **Browse and Search**: View all your Grove files with search and tag filtering
- **Offline-First**: Files are cached locally using Room database for offline access
- **Share Intent**: Share files from any Android app directly to Grove
  - Uploads silently in the background
  - Auto-tags with source app name (e.g., `camera`, `chrome`, `signal`)
- **File Preview**: View images, text files, and documents inline
- **Background Sync**: WorkManager keeps files synced with your ship
- **Real-time Updates**: SSE subscription for instant file change notifications

## Requirements

- Android 10+ (API 29+)
- Grove agent installed on your Urbit ship
- Ship accessible via HTTP (localhost on same device, or network URL)

## Building

### Prerequisites

- **JDK 17+**: Required for Android builds
  ```bash
  # macOS
  brew install openjdk@17
  
  # Ubuntu/Debian
  sudo apt install openjdk-17-jdk
  
  # Verify
  java -version   # Should show 17.x.x
  ```

- **Android SDK**: API level 34 (Android 14)
  - Install via [Android Studio](https://developer.android.com/studio) or command-line tools
  - Required SDK packages:
    - `platforms;android-34`
    - `build-tools;34.0.0`

### Build Debug APK

```bash
cd grove-mobile
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK

```bash
./gradlew assembleRelease
```

Note: Release builds require signing configuration. Add to `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("keystore.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = "release"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
```

### Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Connecting to Your Ship

### Local Ship (same device)

If vere is running on the same Android device (via urbit-mobile Magisk module or AOSP build):

1. Open Grove Mobile
2. **Base URL**: `http://127.0.0.1:80`
3. **+code**: Enter your ship's authentication code
   - Get it from dojo: `+code`
   - Or via HTTP: `curl http://127.0.0.1:12321 -d '{"source":{"dojo":"+code"},"sink":{"stdout":null}}'`

### Remote Ship (network)

For ships running on another machine:

1. Open Grove Mobile
2. **Base URL**: Your ship's HTTP URL (e.g., `http://192.168.1.100:80` or `https://your-ship.domain.com`)
3. **+code**: Enter your ship's authentication code

### Troubleshooting Connection

| Problem | Solution |
|---------|----------|
| Connection timeout | Verify ship is running and HTTP port is accessible |
| 401 Unauthorized | Check +code is correct and not expired |
| Connection refused | Ensure ship's Eyre is bound to accessible interface (not just localhost) |
| HTTPS certificate error | Add your CA to Android trusted certs, or use HTTP for local |

## Architecture

```
grove-mobile/
├── app/src/main/java/io/nativeplanet/grove/
│   ├── data/
│   │   ├── local/          # Room database for offline cache
│   │   ├── remote/         # Urbit HTTP API client
│   │   └── repository/     # Data layer combining local + remote
│   ├── domain/model/       # Domain models (GroveFile, etc.)
│   ├── service/            # Background sync service
│   └── ui/                 # Jetpack Compose UI
│       ├── browse/         # File browser screen
│       ├── preview/        # File preview screen
│       ├── settings/       # Settings screen
│       └── upload/         # Share intent receiver
```

### Key Components

- **UrbitClient**: HTTP client for Grove API using OkHttp
- **FileRepository**: Combines local Room cache with remote Urbit data
- **SyncWorker**: Background sync using WorkManager
- **SSESubscription**: Real-time updates via Server-Sent Events

## Share Intent

Share files to Grove from any Android app:

1. Select a file or image in any app
2. Tap Share
3. Select "Grove"
4. File uploads immediately (or queues if offline)
5. Toast confirms upload

Files are automatically tagged with the source app name for easy filtering.

## Offline Behavior

- File metadata is cached in Room database
- Previously viewed files are available offline
- Uploads queue when offline and sync when reconnected
- Clear "Offline" indicator when disconnected from ship

## Development

### Run Tests

```bash
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Instrumented tests
```

### Lint

```bash
./gradlew lint
```

### Clean Build

```bash
./gradlew clean assembleDebug
```

## CI Builds

Every push triggers GitHub Actions:

1. Builds debug and release APKs
2. Runs lint checks
3. Uploads APKs as artifacts

Download from the [Actions tab](https://github.com/dalhec-banler/grove-mobile/actions).

## Related Projects

- [Grove](https://github.com/dalhec-banler/grove) - The Urbit Grove agent
- [urbit-mobile](../urbit-mobile/) - Vere runtime for Android
- [Mobile Vere](../) - Project overview

## License

MIT
