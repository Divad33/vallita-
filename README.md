# Vallita (Wear OS) — Referee Timer

Standalone Wear OS app for Galaxy Watch4 (and other Wear OS 3+ watches):
- Countdown timer for 1st half -> break -> 2nd half (auto transitions)
- 1:00 warning (short whistle)
- 0:00 whistle (long)
- Final "FIFA-style" double whistle (long + short)
- Optional Overtime countdown
- Haptics + vibration
- Big buttons and quick presets

## Build on GitHub (no PC needed)
1) Create a new GitHub repo and upload all files from this folder.
2) Go to **Actions** tab → run the workflow or push to `main`.
3) Download the APK from **Artifacts**: `Vallita-Wear-debug-apk`.

## Install on your Watch (ADB Wireless)
Use Wireless debugging on the watch, then:
- `adb pair IP:PORT CODE`
- `adb connect IP:PORT`
- `adb install -r app-debug.apk`

Audio files are in `app/src/main/res/raw/`.
