# Eversense E3/365 CGM Plugin for AndroidAPS dev

Patches to add Eversense E3 and E365 CGM support to any AndroidAPS dev build.

Developed by [n0rb33r7](https://github.com/n0rb33r7), [bastiaanv](https://github.com/bastiaanv), and [CAPTCG](https://github.com/CAPTCG).

---

## Requirements

- An existing AndroidAPS dev clone from `https://github.com/nightscout/AndroidAPS`
- Android Studio
- Git

---

## How to Add Eversense to Your AndroidAPS Dev Build

### Step 1 - Download the patches

Click the green **Code** button on this page and select **Download ZIP**.

Extract the ZIP somewhere on your computer, e.g. `C:\EversensePatches`

### Step 2 - Switch to the dev branch

Before applying patches, make sure you are on the `dev` branch. In Android Studio:

1. Look at the bottom right corner and click the branch name
2. Find **origin/dev** in the list and click **Checkout**
3. Wait for Android Studio to finish switching branches

> **Note:** If you do not see `origin/dev`, click **Fetch** first.

### Step 3 - Open your AndroidAPS project in Android Studio

### Step 4 - Apply the patches

Open the **Terminal** in Android Studio (bottom toolbar) and run each line in order:

    git am -3 C:/EversensePatches/0001-Add-Eversense-E3-365-CGM-plugin.patch
    git am -3 C:/EversensePatches/0002-Fix-EversensePlugin-for-latest-dev-branch-migrate-to.patch
    git am -3 C:/EversensePatches/0003-Add-EversenseStatusActivity-fix-plugin-registration-.patch
    git am -3 C:/EversensePatches/0004-Register-Eversense-in-core-enums-DB-models-and-plugi.patch
    git am -3 C:/EversensePatches/0005-UI-Eversense-customizations-TIR-rename-transmitter-b.patch
    git am -3 C:/EversensePatches/0006-Maintenance-config-refactors-and-Eversense-related-C.patch

### Step 5 - Build

1. **File -> Sync Project with Gradle Files**
2. **Build -> Make Project**
3. **Run -> Run app** to install on your phone

---

## What These Patches Add

| Patch | Description |
| --- | --- |
| `0001` | Eversense E3/365 BLE driver plugin - complete `plugins/eversense` module with BluetoothManager, GATT callback, all read/write/calibration/diagnostic-mode/signal-strength packets, SecureV2 crypto for 365, state management and watcher interface |
| `0002` | Source-plugin integration - `EversensePlugin` (BgSource), calibration / placement-guide / status / permission activities, signal-bar drawable, layouts and strings |
| `0003` | Register Eversense in core enums, DB models, and plugin list - `EVERSENSE_E3` / `EVERSENSE_365` in `SourceSensor`, notification IDs, `GlucoseValue`, DB converter, `PluginsListModule`, `MainApp`, `settings.gradle` |
| `0004` | UI customizations - rename "Dexcom TIR" header to "Eversense TIR" across 10 locales, add `IcPluginEversense` icon, add transmitter battery % to Overview status lights |
| `0005` | Maintenance config refactors and Config cleanup - Export Settings local fix, Eversense DMS credential sync into `SECURE_STATE`, `putCurrentValues` and `putDeviceEvents` for E365 DMS, removal of `enableOmnipodDriftCompensation` from Config |

---

## Transmitter Support

| Transmitter | Notes |
| --- | --- |
| **Eversense E3** (180-day) | Official Eversense app must run alongside AndroidAPS |
| **Eversense 365** (1-year) | Standalone - no official app needed |

---

## Related

- Original PR: [nightscout/AndroidAPS #4474](https://github.com/nightscout/AndroidAPS/pull/4474)


