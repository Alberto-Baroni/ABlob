# ABlob - Azure Blob Storage Client for Android

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg?style=flat-circle&logo=android)](https://developer.android.com)
[![Kotlin Version](https://img.shields.io/badge/kotlin-2.0.0-blue.svg?style=flat-circle&logo=kotlin)](https://kotlinlang.org)
[![Gradle Version](https://img.shields.io/badge/gradle-9.6.1-green.svg?style=flat-circle&logo=gradle)](https://gradle.org)
[![AGP Version](https://img.shields.io/badge/AGP-9.2.0-orange.svg?style=flat-circle)](https://developer.android.com/studio/releases/gradle-plugin)

**ABlob** is a lightweight, elegant, and highly secure Android application designed to browse, search, view, and download files stored in Azure Blob Storage. It is built entirely in **Kotlin** using **Jetpack Compose** and follows modern Android development best practices.

To maintain a minimal application size and ensure compatibility with the Android runtime, ABlob uses a custom-tailored lightweight OkHttp connection wrapper. This bypasses the heavy Project Reactor and Netty dependencies of the official server-side Azure Java SDK.

---

## Key Features

- 🔐 **Secure Credential Storage**: Sensitive configuration details (account keys, client secrets, SAS tokens) are encrypted with **AES-256 GCM** and saved in `EncryptedSharedPreferences` backed by the Android Keystore.
- ☝️ **Biometric Authentication**: Access the application passwordlessly using standard Android fingerprint and face recognition.
- 🌐 **Flexible Connections**: Connect to containers via:
  - **Shared Key**: Computes standard Azure canonical header signatures signed with HMAC-SHA256.
  - **SAS Token**: Connects directly via Shared Access Signature query parameters.
  - **Microsoft Entra ID (Azure AD)**: Connects programmatically using the OAuth2 Client Credentials flow (Tenant ID, Client ID, Client Secret) with automatic token refresh caching.
- 📁 **Virtual Folder Navigation**: Seamlessly browse nested virtual directories in the storage container, complemented by interactive breadcrumbs navigation.
- 📄 **Paginated Lists**: Lists documents and folders exactly **10 at a time** using continuation tokens (`NextMarker`) to keep network payloads small.
- 🖼️ **Image Gallery**: View photos as a grid gallery loaded asynchronously using Coil. Click on any thumbnail to open a fullscreen lightbox with smooth **pinch-to-zoom** and panning capabilities.
- 📥 **Scoped Storage Downloads**: Stream files directly to the public device `Downloads` directory without requiring runtime storage permissions.
- ⚙️ **Connection Management**: Easily update credentials, toggle biometrics, or disconnect account profiles from the settings panel.

---

## Technical Stack

- **UI & Layout**: Jetpack Compose, Compose Navigation3, Material 3 components.
- **Asynchronous Flow**: Kotlin Coroutines & Flow.
- **Image Loading**: Coil Compose.
- **Network Core**: OkHttp 4.12.0.
- **Security & Biometrics**: AndroidX Security Crypto 1.1.0, AndroidX Biometrics 1.1.0.
- **Parser**: XML Pull Parser (native to Android runtime).
- **Target Specifications**: Minimum SDK `24`, Compile/Target SDK `36` (Android 16), Gradle `9.6.1`.

---

## Architecture Overview

The codebase is organized cleanly into modular layers:

```text
com.example.ablob
│
├── data
│   ├── AzureBlobModels.kt    # Schemas for configs, items, and auth types
│   ├── SecurityManager.kt    # Keystore encryption & biometrics logic
│   └── AzureBlobClient.kt    # HTTP Interceptors, OAuth token fetcher, and XML parsing
│
├── ui
│   ├── login                 # Connection and configuration setup screen
│   ├── browser               # Directory tree, document list, gallery, and zoom lightbox
│   └── settings              # App preferences, credential manager, and biometric toggle
│
├── MainActivity.kt           # FragmentActivity binding lifecycle and custom Coil ImageLoader
└── Navigation.kt             # Navigation3 routes graph (Splash, Login, Browser, Settings)
```

---

## Getting Started

### Prerequisites

- Android Studio Koala / Ladybug or newer.
- Android SDK 36 (Android 16) tools installed.
- A physical device or emulator running API level 24 or higher.

### Gradle Configuration

The project utilizes the **Gradle Daemon Toolchain** to automatically locate or download the correct JDK requirements:
- Check the configuration at [gradle-daemon-jvm.properties](file:///d:/Alberto/SVILUPPO/ABlob/gradle/gradle-daemon-jvm.properties).

### Building from Source

To compile the application debug APK, execute the following command in the root directory:

```bash
./gradlew assembleDebug
```

To run unit tests:

```bash
./gradlew test
```
