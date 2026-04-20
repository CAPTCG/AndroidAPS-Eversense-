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

Open the **Terminal** in Android Studio (bottom toolbar) and run each line:

    git am C:/EversensePatches/0001-Add-Eversense-E3-365-CGM-plugin.patch
    git am C:/EversensePatches/0002-E3-fixes-SingleByte-packets-force-sync-TOO_SOON-dark.patch
    git am C:/EversensePatches/0003-Fix-TOO_SOON-calibration-summary-display-in-dark-mod.patch
    git am C:/EversensePatches/0004-Sync-Eversense-plugin-to-latest-from-AndroidAPSEvers.patch
    git am C:/EversensePatches/0005-pushing-changes.patch
    git am C:/EversensePatches/0006-Fix-suppress-Recalculated-Data-Used-warning-for-Ever.patch
    git am C:/EversensePatches/0007-Fix-EversensePlugin-for-latest-dev-branch-migrate-to.patch
    git am C:/EversensePatches/0008-Add-EversenseStatusActivity-fix-plugin-registration-.patch
    git am C:/EversensePatches/0009-Fix-remove-deprecated-pluginIcon-after-upstream-API-.patch
    git am C:/EversensePatches/0010-Update-Eversense-add-icon-sign-out-calibration-info-.patch
    git am C:/EversensePatches/0011-Fix-Eversense-DMS-upload-sync-credentials-from-AAPS-.patch
    git am C:/EversensePatches/0012-Fix-AAPT-resource-linking-replace-attr-refs-with-dir.patch
    git am C:/EversensePatches/0013-Remove-orphaned-source_item.xml-unused-after-upstrea.patch
    git am C:/EversensePatches/0014-Register-Eversense-activities-in-ActivitiesModule-fo.patch
    git am C:/EversensePatches/0015-Migrate-Eversense-activities-from-DaggerAppCompatAct.patch
    git am C:/EversensePatches/0016-Remove-Eversense-activities-from-ActivitiesModule-Hi.patch
    git am C:/EversensePatches/0017-Migrate-HistoryBrowseActivity-to-Hilt-AndroidEntryPo.patch
    git am C:/EversensePatches/0018-Remove-ActivitiesModule-all-activities-use-Hilt-Andr.patch
    git am C:/EversensePatches/0019-Add-Eversense-transmitter-battery-to-Overview-status.patch
    git am C:/EversensePatches/0020-Fix-Eversense-DMS-portal-sync-add-putCurrentValues-a.patch

### Step 5 - Build

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
| `0007` | Fix: migrate EversensePlugin preferences to Compose API for latest dev branch |
| `0008` | Add Eversense status activity, fix plugin registration, add credentials and calibration preferences |
| `0009` | Fix: remove deprecated pluginIcon after upstream API change |
| `0010` | Add Eversense icon, sign out, calibration info, placement signal label |
| `0011` | Fix Eversense DMS upload — sync credentials from AAPS prefs into SECURE_STATE |
| `0012` | Fix AAPT resource linking — replace attr refs with direct colors in source_item.xml |
| `0013` | Remove orphaned source_item.xml — unused after upstream Compose migration |
| `0014` | Register Eversense activities in ActivitiesModule for Dagger injection |
| `0015` | Migrate Eversense activities from DaggerAppCompatActivity to Hilt AndroidEntryPoint |
| `0016` | Remove Eversense activities from ActivitiesModule — Hilt auto-discovers |
| `0017` | Migrate HistoryBrowseActivity to Hilt AndroidEntryPoint, remove from ActivitiesModule |
| `0018` | Remove ActivitiesModule — all activities use Hilt AndroidEntryPoint |
| `0019` | Add Eversense transmitter battery % to Overview status lights (Compose) |
| `0020` | Fix Eversense DMS portal sync — add putCurrentValues and putDeviceEvents calls |

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



