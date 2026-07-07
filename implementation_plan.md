# ABlob - Android Azure Blob Storage Client Implementation Plan

This plan details the implementation of **ABlob**, a modern, secure, and lightweight Android application (compiled for SDK 36/37) that allows browsing, searching, viewing, and downloading files from an Azure Blob Storage container. It supports three authentication mechanisms (Shared Key, SAS Token, and Microsoft Entra ID via Client Credentials), stores sensitive configuration using Android's security best practices, and leverages biometrics for passwordless app entry.

## User Review Required

> [!IMPORTANT]
> **Authentication Options:**
> For Microsoft Entra ID (Azure AD), we propose using the **OAuth2 Client Credentials Flow** (Tenant ID, Client ID, Client Secret). This is a robust, programmatic connection that works natively in a mobile app without requiring MSAL browser redirects, custom package signatures, or interactive developer console setup.
> 
> **Secure Storage:**
> Sensitive connection parameters (keys, secrets, SAS tokens) will be saved using **EncryptedSharedPreferences** (`androidx.security:security-crypto`). This automatically handles master key creation in the Android Keystore and encrypts keys/values with AES-256 GCM.
> 
> **Biometric Entry:**
> When biometric entry is enabled, the app will show a system biometric prompt on startup. Once authenticated, it will retrieve the encrypted connection parameters and automatically sign in.

## Proposed Changes

We will organize the project into a modular architecture under `com.example.ablob`:
- `data`: Models and repository for local secure settings and remote Blob API queries.
- `network`: Custom OkHttp interceptors for signing SharedKey requests and managing Entra ID OAuth tokens.
- `ui`: Jetpack Compose views for login, settings, directory browser, image gallery, and detail lightbox.

---

### Build Configurations

#### [MODIFY] [libs.versions.toml](file:///d:/Alberto/SVILUPPO/ABlob/gradle/libs.versions.toml)
Add dependencies for Android Security Crypto, Android Biometrics, OkHttp, and Coil:
- `androidx.security:security-crypto:1.1.0`
- `androidx.biometric:biometric:1.1.0`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `io.coil-kt:coil-compose:2.6.0`

#### [MODIFY] [app/build.gradle.kts](file:///d:/Alberto/SVILUPPO/ABlob/app/build.gradle.kts)
Apply added dependencies to the application module.

---

### Permissions & Android Manifest

#### [MODIFY] [AndroidManifest.xml](file:///d:/Alberto/SVILUPPO/ABlob/app/src/main/AndroidManifest.xml)
Add the following permissions:
- `android.permission.INTERNET` (to communicate with Azure Storage)
- `android.permission.USE_BIOMETRIC` (for fingerprint login)

---

### Core Data & Network Architecture

#### [NEW] [AzureBlobModels.kt](file:///d:/Alberto/SVILUPPO/ABlob/app/src/main/java/com/example/ablob/data/AzureBlobModels.kt)
Define models for the UI:
- `StorageConfig`: Data class representing connection parameters (account name, container name, auth type, keys/secrets).
- `AuthType`: Enum (`SHARED_KEY`, `SAS_TOKEN`, `ENTRA_ID`).
- `BlobItem`: Sealed class:
  - `Folder(val name: String, val path: String)`
  - `File(val name: String, val path: String, val size: Long, val contentType: String)`

#### [NEW] [SecurityManager.kt](file:///d:/Alberto/SVILUPPO/ABlob/app/src/main/java/com/example/ablob/data/SecurityManager.kt)
Implement secure storage and biometrics:
- Wraps `EncryptedSharedPreferences` to load/save `StorageConfig`.
- Exposes helper to check if biometric hardware is available and if biometric login is enabled.

#### [NEW] [AzureBlobClient.kt](file:///d:/Alberto/SVILUPPO/ABlob/app/src/main/java/com/example/ablob/data/AzureBlobClient.kt)
Provide remote communication:
- Builds custom `OkHttpClient` with an Interceptor.
- Signature Generator: Implements SharedKey signature (`StringToSign` with 12 newlines + canonical headers + canonical resource).
- Entra ID Token Refresh: Refreshes access tokens via `https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token`.
- `listBlobs(prefix, delimiter, maxResults, marker)`: Executes GET container query and parses XML with `XmlPullParser`.
- `downloadBlob(blobPath)`: Streams blob file.

---

### User Interface & Navigation

#### [MODIFY] [NavigationKeys.kt](file:///d:/Alberto/SVILUPPO/ABlob/app/src/main/java/com/example/ablob/NavigationKeys.kt)
Define routes:
- `Splash`: Show biometric login prompt.
- `Login`: Input configuration screen (URL, keys, secrets).
- `Browser`: File/Folder listing, directory navigation, search.
- `Settings`: Edit/update credentials, toggle biometric configuration, disconnect.

#### [MODIFY] [Navigation.kt](file:///d:/Alberto/SVILUPPO/ABlob/app/src/main/java/com/example/ablob/Navigation.kt)
Wire up screens and view models with Navigation3.

#### [NEW] [LoginScreen.kt](file:///d:/Alberto/SVILUPPO/ABlob/app/src/main/java/com/example/ablob/ui/login/LoginScreen.kt)
A sleek Material 3 screen that allows choosing connection type and inputting details. Includes a toggle to enable biometrics.

#### [NEW] [BrowserScreen.kt](file:///d:/Alberto/SVILUPPO/ABlob/app/src/main/java/com/example/ablob/ui/browser/BrowserScreen.kt)
Main browser interface:
- **Breadcrumbs / Navigation Bar:** Displays current directory, tapping a parent folder navigates up.
- **Search bar:** Filter files/folders by search query.
- **Tab Layout:** "Documents" and "Images" tabs.
- **Documents List:** Table or list with name, size, modified date, and a Download button. Paginated at 10 items with a "Load More" button.
- **Images Gallery:** A grid gallery (paginated at 10 items) showing image thumbnails (rendered using Coil with authorization headers).
- **Lightbox / Zoom Dialog:** Click an image to view in fullscreen. Implements pinch-to-zoom, pan, and dismiss gesture.

#### [NEW] [SettingsScreen.kt](file:///d:/Alberto/SVILUPPO/ABlob/app/src/main/java/com/example/ablob/ui/settings/SettingsScreen.kt)
Clean menu to:
- Review connection parameters (masked).
- Modify credentials.
- Toggle biometric access.
- Log out (deletes saved config).

---

## Verification Plan

### Automated Tests
- Run Gradle check/build to ensure compiles: `.\gradlew.bat assembleDebug`
- Unit tests for SharedKey signature string calculation and XML response parser.

### Manual Verification
- Test secure credential storage and biometric popup.
- Verify browsing virtual folders (hierarchical navigation works).
- Verify pagination (exactly 10 items at a time + Load More).
- Verify loading images in gallery and zooming in fullscreen.
- Verify downloading documents/images (check Downloads folder).
