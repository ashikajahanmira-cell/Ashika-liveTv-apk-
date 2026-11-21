# 📺 Ashika Live TV

<p align="center">
  <strong>Your Entertainment. One Destination.</strong>
</p>

<p align="center">
  A modern Android Live TV streaming application built with Kotlin, Firebase and ExoPlayer.
</p>

---

## ✨ Features

- 📡 Live TV channel streaming
- 🎬 HLS video playback
- 📂 Multiple M3U playlist support
- 🔍 Channel search
- 🎤 Voice search
- ❤️ Favorite channels
- 🕒 Watch history
- 📁 Category filtering
- 🎨 Modern purple-themed interface
- 📱 Fullscreen video player
- 🪟 Picture-in-Picture mode
- 🔄 Automatic stream reconnection
- 🌐 Internet connection monitoring
- 🔐 Firebase Authentication
- ⚙️ Admin channel management
- ☁️ Firebase Firestore integration

## 🛠️ Technologies

| Technology | Version |
|---|---|
| Language | Kotlin |
| Minimum SDK | Android 6.0 (API 23) |
| Compile SDK | 35 |
| Target SDK | 35 |
| Java | 17 |
| ExoPlayer / Media3 | 1.5.1 |
| Firebase BOM | 33.7.0 |
| Build System | Gradle |

## 📱 Application Information

- **App Name:** Ashika Live TV
- **Package Name:** `com.ashika.livetv`
- **Version:** 2.0
- **Version Code:** 2
- **Platform:** Android

## 🚀 Getting Started

### Requirements

- Android Studio
- JDK 17
- Android SDK 35
- Firebase project

### Installation

1. Clone this repository:

   ```bash
   git clone https://github.com/ashikajahanmira-cell/Ashika-liveTv-apk-.git
   ```

2. Open the project in Android Studio.

3. Add your Firebase configuration file:

   `app/google-services.json`

4. Sync Gradle.

5. Build and run the application.

## 🔥 Firebase Configuration

Ashika Live TV uses Firebase Authentication and Cloud Firestore.

### Firestore Collections

**1. channels**

Each channel document supports:

| Field | Type |
|---|---|
| name | String |
| url | String |
| category | String |
| enabled | Boolean |

**2. settings / playlist**

Supports multiple playlists:

- name
- url
- enabled

The app also supports the legacy single-playlist format.

## 🎥 Video Player

The application uses AndroidX Media3 ExoPlayer.

Player features:

- HLS streaming
- Play / Pause
- Fullscreen mode
- Picture-in-Picture
- Automatic reconnection
- Buffering indicator
- Stream error handling

## ⚙️ GitHub Actions APK Build

This repository includes an automated GitHub Actions workflow.

### Build APK

1. Open the GitHub repository.
2. Go to the Actions tab.
3. Select **Build Ashika Live TV**.
4. Click **Run workflow**.
5. Wait for the build to finish.
6. Download the `Ashika-Live-TV-APKs` artifact.

The workflow builds:

- Debug APK
- Release APK

Required signing secrets:

- `DEBUG_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_PASSWORD`

Keep all signing credentials private.

## 📂 Project Structure

```text
Ashika-liveTv-apk-
│
├── app/
│   ├── src/main/
│   │   ├── java/com/ashika/livetv/
│   │   │   ├── MainActivity.kt
│   │   │   └── PlayerActivity.kt
│   │   │
│   │   └── AndroidManifest.xml
│   │
│   └── build.gradle.kts
│
├── .github/
│   └── workflows/
│       └── build.yml
│
├── settings.gradle.kts
├── gradlew
└── README.md
```

## 🔒 Security

- Protect Firebase credentials.
- Use secure Firestore rules.
- Never publish private signing keys.
- Only authorized administrators should manage channels and playlists.
- Only stream content you have permission to distribute.

## 📡 Streaming Notice

Channel availability depends on the stream provider and internet connection. Some streams may become unavailable or require authorized access.

## 👨‍💻 Developer

**Ashika Live TV**

GitHub:  
https://github.com/ashikajahanmira-cell

Repository:  
https://github.com/ashikajahanmira-cell/Ashika-liveTv-apk-

---

<p align="center">
  Made with ❤️ for Ashika Live TV
</p>
