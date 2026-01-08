# SimpleTranslator

SimpleTranslator is a minimal Android app (Java) that listens to the microphone, detects the spoken language (English or Greek), and translates between English and Greek using on-device ML Kit translation models. It also provides a Manage Models screen to download or delete the on-device models.

## Setup

1. Install Android Studio and the Android SDK (API 34 recommended).
2. Clone this repository.

   ```bash
   git clone https://github.com/AllTeach/SimpleTranslator.git
   cd SimpleTranslator
   ```

3. Open the project in Android Studio.
4. Let Gradle sync and download dependencies.
5. Run the app on a device or emulator. The app requires RECORD_AUDIO permission at runtime.

Notes:
- The app uses ML Kit on-device translation models. The Manage Models screen allows you to pre-download English and Greek models. By default downloads require Wi‑Fi; enable cellular download in Manage Models if desired.
- Replace the placeholder app icon in `app/src/main/res/mipmap-*` with your preferred image if desired.
