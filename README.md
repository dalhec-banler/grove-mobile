# Grove Mobile

Native Android client for [Grove](https://github.com/dalhec-banler/grove), a file storage app for Urbit.

## Features

- **Browse & Search**: View all your Grove files with search and tag filtering
- **Offline-First**: Files are cached locally for offline access
- **Share Intent**: Share files from any app directly to Grove (silently uploads with source app tag)
- **Preview**: View images, text files, and documents inline
- **Sync**: Background service keeps files synced with your ship

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

## Building

```bash
./gradlew assembleDebug
```

## Requirements

- Android 10+ (API 29+)
- Grove agent installed on your Urbit ship
- Ship accessible via HTTP (localhost or network)

## Connection

The app connects to your ship at `http://127.0.0.1:80` by default. Enter your ship's `+code` to authenticate.

For remote ships, you'll need to configure the base URL (coming soon in settings).

## Share Intent

When you share a file to Grove from another app:
1. File is uploaded immediately (or queued if offline)
2. Tagged with the source app name (e.g., `camera`, `chrome`)
3. Toast confirms upload

## Offline Behavior

- Files are cached locally after first view
- Uploads queue when offline, sync when reconnected
- File list shows last synced data
- Clear offline indicator when disconnected

## License

MIT
