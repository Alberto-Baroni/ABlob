# Guida Completa per il Rilascio di ABlob sul Google Play Store

Questa guida descrive passo-passo la procedura per preparare, compilare e pubblicare l'applicazione **ABlob** sul Google Play Store.

---

## 📋 Indice dei Contenuti
1. [Prerequisiti Fondamentali](#1-prerequisiti-fondamentali)
2. [Generazione del Keystore di Firma](#2-generazione-del-keystore-di-firma)
3. [Compilazione del pacchetto Android App Bundle (AAB)](#3-compilazione-del-pacchetto-android-app-bundle-aab)
4. [Configurazione di Google Play Console](#4-configurazione-di-google-play-console)
5. [Gestione degli Aggiornamenti Futuri](#5-gestione-degli-aggiornamenti-futuri)

---

## 1. Prerequisiti Fondamentali

### ⚠️ Cambiare l'Application ID (`applicationId`)
Google Play Store **rifiuta** tassativamente le applicazioni il cui pacchetto inizia con `com.example`. 
Prima di procedere:
1. Apri il file [app/build.gradle.kts](file:///d:/Alberto/SVILUPPO/ABlob/app/build.gradle.kts).
2. Individua la riga `applicationId = "com.example.ablob"` (riga 11).
3. Modificala inserendo un dominio univoco di tua proprietà o una combinazione che ti identifichi, ad esempio:
   ```kotlin
   applicationId = "com.alkaloon.ablob"
   ```
4. Salva il file e sincronizza il progetto con Gradle in Android Studio.

### 🖼️ Icone dell'Applicazione (Launcher Icons)
Assicurati di aver configurato le icone della tua app in formato adattivo. Le trovi all'interno della cartella `app/src/main/res/mipmap-*`. 
Se desideri modificarle, puoi utilizzare lo strumento **Asset Studio** integrato in Android Studio (tasto destro sulla cartella `res` -> *New* -> *Image Asset*).

### 📄 Informativa sulla Privacy (Privacy Policy)
Google Play Store richiede un link pubblico a una Privacy Policy valida (specialmente poiché l'app richiede la connessione a servizi esterni come Azure Blob Storage e richiede permessi biometrici). Puoi ospitare la tua Privacy Policy su un sito personale o su servizi come GitHub Pages.

---

## 2. Generazione del Keystore di Firma

Le applicazioni rilasciate sul Play Store devono essere firmate digitalmente con una chiave crittografica. Abbiamo preparato uno script automatico per semplificare questo passaggio.

### Procedura:
1. Apri un terminale PowerShell nella cartella principale del progetto.
2. Esegui lo script:
   ```powershell
   ./generate_release_key.ps1
   ```
3. Inserisci una password sicura quando richiesto (e confermala).
4. Lo script eseguirà `keytool` (estratto da Android Studio o dal JDK di sistema) per generare il file `release.jks` e configurerà automaticamente il file `keystore.properties` con i parametri corretti.

> [!WARNING]
> **BACKUP DELLA CHIAVE DI FIRMA**
> Il file `release.jks` e il file `keystore.properties` sono inseriti nel file `.gitignore` e **non verranno caricati su GitHub**.
> **Salva il file `release.jks` in un luogo sicuro ed eterno (es. sul tuo password manager o cloud privato).** Se perdi questo file o dimentichi la password, non potrai mai più aggiornare l'applicazione sul Play Store!

---

## 3. Compilazione del pacchetto Android App Bundle (AAB)

Google Play richiede il formato `.aab` (Android App Bundle), che permette a Google di ottimizzare le dimensioni dell'app scaricata per ogni specifico dispositivo dell'utente.

### Procedura di compilazione:
1. Dal terminale nella cartella principale del progetto, compila il bundle di release eseguendo:
   ```powershell
   ./gradlew bundleRelease
   ```
2. Una volta completato il processo con successo, troverai il file pronto per il caricamento in:
   `app/build/outputs/bundle/release/app-release.aab`
3. R8 (il compilatore ottimizzatore) applicherà automaticamente le regole di minificazione e offuscamento configurate in [proguard-rules.pro](file:///d:/Alberto/SVILUPPO/ABlob/app/proguard-rules.pro) per ridurre il peso dell'app e proteggere il codice sorgente.

---

## 4. Configurazione di Google Play Console

### A. Creazione dell'Account Sviluppatore
1. Accedi a [Google Play Console](https://play.google.com/console).
2. Registrati come Sviluppatore Individuale (il costo una tantum è di $25 USD).
3. Completa la verifica dell'identità richiesta da Google.

### B. Creazione dell'Applicazione
1. Fai clic su **Crea app** in alto a destra.
2. Compila i dettagli di base:
   - **Nome dell'app**: `ABlob` (o il nome pubblico desiderato).
   - **Lingua predefinita**: `Italiano` (o un'altra lingua principale).
   - **Tipo di applicazione**: `App`.
   - **Gratuita o a pagamento**: `Gratuita`.
3. Conferma le dichiarazioni e clicca su **Crea app**.

### C. Configurazione Iniziale (Dashboard)
Nella Dashboard troverai una sezione denominata **Configura la tua app**. Completa i seguenti passaggi obbligatori:
- **Accesso all'app**: Scegli "Tutte le funzionalità sono disponibili senza accesso speciale" o, se l'app richiede credenziali preimpostate di test per la recensione da parte di Google, inserisci dei parametri di test per l'account Azure.
- **Annunci**: Seleziona "No, la mia app non contiene annunci".
- **Classificazione dei contenuti**: Compila il questionario per ottenere la classificazione dell'età (es. PEGI 3).
- **Target e contenuti**: Definisci il pubblico target (es. 18 anni o superiore).
- **App di notizie**: Seleziona "No".
- **Sicurezza dei dati (Data Safety)**: Dichiara quali dati raccoglie la tua app. Poiché ABlob si connette ad Azure e usa biometria in locale, dichiara che i dati sensibili di connessione rimangono sul dispositivo e che non vengono condivisi con terze parti.
- **Informativa sulla privacy**: Inserisci il link URL alla tua Privacy Policy.

### D. Scheda dello Store (Presenza nello Store)
Configura gli elementi visivi dell'applicazione:
- **Descrizione breve**: Un client moderno, leggero e sicuro per Azure Blob Storage.
- **Descrizione completa**: Descrivi le caratteristiche principali dell'app (es. autenticazione avanzata, visualizzazione immagini integrata, supporto biometria per accesso rapido e sicuro).
- **Grafica**:
  - **Icona dell'app**: PNG o JPEG quadrata (512x512px).
  - **Grafica promozionale**: PNG o JPEG (1024x500px).
  - **Screenshot per telefono**: Da 2 a 8 screenshot dell'app funzionante (dimensioni consigliate 1080x1920px o simili). Puoi scattare gli screenshot direttamente dall'emulatore o da un dispositivo reale.

### E. Requisito dei 20 Tester (Solo per nuovi Account Personali)
> [!IMPORTANT]
> Se hai creato un nuovo account sviluppatore Google Play di tipo personale dopo il 13 novembre 2023, Google richiede obbligatoriamente di condurre un test chiuso prima di poter pubblicare l'app in produzione.
> - Devi reclutare **almeno 20 tester**.
> - I tester devono rimanere iscritti al test chiuso per **almeno 14 giorni consecutivi**.
> - Solo dopo aver superato questa fase potrai richiedere l'accesso alla pubblicazione in produzione.

1. Vai su **Rilasci** -> **Test chiuso** -> **Traccia alfa**.
2. Crea un gruppo di tester inserendo i loro indirizzi email Google.
3. Carica il file `app-release.aab` nella traccia.
4. Condividi il link di partecipazione con i tuoi tester.

---

## 5. Gestione degli Aggiornamenti Futuri

Quando pubblicherai modifiche o aggiornamenti della tua applicazione, dovrai incrementare la versione in [app/build.gradle.kts](file:///d:/Alberto/SVILUPPO/ABlob/app/build.gradle.kts):

1. **`versionCode`**: È un numero intero progressivo che identifica univocamente la build per il Play Store. Deve essere incrementato di `1` ad ogni rilascio (es. da `1` a `2`).
2. **`versionName`**: È la stringa della versione visibile agli utenti (es. da `"1.0"` a `"1.1"` o `"1.0.1"`).

```kotlin
defaultConfig {
    applicationId = "com.alkaloon.ablob"
    minSdk = 24
    targetSdk = 36
    versionCode = 2  // <-- Incrementa ad ogni aggiornamento
    versionName = "1.0.1" // <-- Modifica la stringa visibile
}
```

Una volta modificati questi valori, riesegui `./gradlew bundleRelease` e carica il nuovo file `.aab` nella Console di Google Play per avviare il processo di revisione del nuovo aggiornamento.
