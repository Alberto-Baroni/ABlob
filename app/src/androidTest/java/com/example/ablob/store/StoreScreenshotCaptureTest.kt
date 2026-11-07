package com.example.ablob.store

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ablob.data.AuthType
import com.example.ablob.data.AzureBlobClient
import com.example.ablob.data.SecurityManager
import com.example.ablob.data.StorageConfig
import com.example.ablob.theme.ABlobTheme
import com.example.ablob.ui.browser.BrowserScreen
import com.example.ablob.ui.viewer.ViewerScreen
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Opt-in capture of real application screens backed by a private demo container. */
class StoreScreenshotCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureItalianPhoneScreenshots() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureStoreScreenshots") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val localeConfig = Configuration(base.resources.configuration).apply { setLocale(Locale.ITALIAN) }
        val localized = base.createConfigurationContext(localeConfig)
        val isolated = object : ContextWrapper(localized) {
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("store-capture-$name", mode)
        }
        val security = SecurityManager(isolated)
        security.clearConfig()
        val token = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/ablob-store-sas")
        ).bufferedReader().use { it.readText().trim() }
        require(token.isNotBlank()) { "A short-lived read-only demo SAS must be supplied on the emulator." }
        val storage = AzureBlobClient()
        storage.updateConfig(StorageConfig(
            accountName = "ablobtestab20261107", containerName = "ablob-demo",
            authType = AuthType.SAS_TOKEN, sasToken = token,
        ))
        security.toggleFavorite("Documenti/")
        security.toggleFavorite("Configurazione.json")
        security.toggleFavorite("Guida_rapida.txt")
        val route = mutableStateOf("root")
        val dark = mutableStateOf(false)
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localeConfig,
                LocalActivityResultRegistryOwner provides compose.activity,
            ) {
                ABlobTheme(appTheme = if (dark.value) SecurityManager.AppTheme.DARK else SecurityManager.AppTheme.LIGHT) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        key(route.value) {
                            when (route.value) {
                                "text", "json" -> {
                                    val json = route.value == "json"
                                    val fileName = if (json) "Configurazione.json" else "Guida_rapida.txt"
                                    ViewerScreen(storage, fileName, fileName,
                                        if (json) "application/json" else "text/plain",
                                        onBackClick = { route.value = "root" })
                                }
                                else -> BrowserScreen(
                                    azureBlobClient = storage, securityManager = security,
                                    prefix = if (route.value == "folder") "Documenti/" else "",
                                    onNavigateToFolder = { route.value = "folder" },
                                    onNavigateUp = { route.value = "root" },
                                    onSettingsClick = {},
                                    onNavigateToViewer = { _, name, _ -> route.value = if (name.endsWith(".json")) "json" else "text" },
                                )
                            }
                        }
                    }
                }
            }
        }
        val output = File(base.getExternalFilesDir(null), "play-store-it-IT").apply { mkdirs() }

        fun waitFor(text: String) {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        fun capture(name: String) {
            compose.waitForIdle()
            compose.activityRule.scenario.onActivity {
                WindowInsetsControllerCompat(it.window, it.window.decorView).apply {
                    isAppearanceLightStatusBars = !dark.value
                    isAppearanceLightNavigationBars = !dark.value
                }
            }
            SystemClock.sleep(500)
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            check(bitmap.width == 1080 && bitmap.height == 1920) { "Configure emulator as 1080x1920 first." }
            File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
        }

        try {
            waitFor("Guida_rapida.txt")
            capture("01-file-e-cartelle.jpg")
            compose.runOnIdle { route.value = "folder" }
            waitFor("Guida_al_progetto.txt")
            capture("02-navigazione-cartelle.jpg")
            compose.runOnIdle { route.value = "favorites" }
            waitFor("Guida_rapida.txt")
            compose.onNodeWithText("Preferiti", substring = true).performClick()
            capture("03-preferiti.jpg")
            compose.runOnIdle { route.value = "text" }
            waitFor("BENVENUTO IN ABLOB")
            capture("04-lettore-testo.jpg")
            compose.runOnIdle { route.value = "json" }
            waitFor("Workspace demo")
            capture("05-visualizzatore-json.jpg")
            compose.runOnIdle { dark.value = true; route.value = "dark" }
            waitFor("Guida_rapida.txt")
            capture("06-tema-scuro.jpg")
        } finally {
            security.clearConfig()
            base.deleteSharedPreferences("store-capture-ablob_secure_prefs")
            base.deleteSharedPreferences("store-capture-ablob_settings")
        }
    }
}
