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

### Step 2 - Open your AndroidAPS project in Android Studio

### Step 3 - Apply the patches
Open the **Terminal** in Android Studio (bottom toolbar) and run each line:

    git apply C:/EversensePatches/0001-Add-Eversense-E3-365-CGM-plugin.patch
    git apply C:/EversensePatches/0002-E3-fixes-SingleByte-packets-force-sync-TOO_SOON-dark.patch
    git apply C:/EversensePatches/0003-Fix-TOO_SOON-calibration-summary-display-in-dark-mod.patch
    git apply C:/EversensePatches/0004-Sync-Eversense-plugin-to-latest-from-AndroidAPSEvers.patch
    git apply C:/EversensePatches/0005-pushing-changes.patch
    git apply C:/EversensePatches/0006-Fix-suppress-Recalculated-Data-Used-warning-for-Ever.patch

### Step 4 - Build
1. **File -> Sync Project with Gradle Files**
2. **Build -> Make Project**
3. **Run -> Run app** to install on your phone

---

## What These Patches Add

| Patch | Description |
| --- | --- |
| `0001` | Eversense E3/365 CGM plugin - core BLE driver and AAPS integration |
| `0002` | E3 fixes - SingleByte packets, force sync, calibration |
| `0003` | Fix TOO_SOON calibration display in dark mode |
| `0004` | Sync Eversense plugin with latest updates |
| `0005` | Additional Eversense updates |
| `0006` | Fix: suppress Recalculated Data Used yellow triangle warning for E3 and E365 |

---

## Transmitter Support

| Transmitter | Notes |
| --- | --- |
| **Eversense E3** (180-day) | Official Eversense app must run alongside AndroidAPS |
| **Eversense 365** (1-year) | Standalone - no official app needed |

---

## Related

- Original PR: [nightscout/AndroidAPS #4474](https://github.com/nightscout/AndroidAPS/pull/4474)
- Full build with Eversense: [CAPTCG/AndroidAPS-3.4.2.2-Eversense](https://github.com/CAPTCG/AndroidAPS-3.4.2.2-Eversense)