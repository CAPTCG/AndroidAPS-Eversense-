# Eversense E3/365 CGM Plugin for AndroidAPS dev

Patches to add Eversense E3 and E365 CGM support to any AndroidAPS dev build.

Developed by [n0rb33r7](https://github.com/n0rb33r7), [bastiaanv](https://github.com/bastiaanv), and [CAPTCG](https://github.com/CAPTCG).

---

## Requirements

- Android Studio installed
- Git installed

---

## Step 1 - Clone AndroidAPS

1. Open Android Studio
2. Click File then New then Project from Version Control
3. Enter this URL: https://github.com/nightscout/AndroidAPS.git
4. Choose a folder on your computer to save the project
5. Click Clone
6. When asked to open the project click Yes

---

## Step 2 - Switch to the dev branch

1. Look at the bottom right corner of Android Studio
2. Click the branch name which shows master
3. In the list find origin/dev
4. Click Checkout
5. Wait for Android Studio to finish switching branches

---

## Step 3 - Download the Eversense patches

1. Go to https://github.com/CAPTCG/AndroidAPS-Eversense-
2. Click the green Code button
3. Click Download ZIP
4. Save the ZIP file to your Downloads folder
5. Right-click the ZIP file and select Extract All
6. Extract to your Downloads folder
7. Click Extract
8. After extraction you will see a folder called AndroidAPS-Eversense--master inside your Downloads folder
9. Inside that folder there is another folder also called AndroidAPS-Eversense--master - the patch files are inside this inner folder

---

## Step 4 - Apply the patches

1. In Android Studio click Terminal at the bottom of the screen
2. Run each of these 6 commands one at a time pressing Enter after each
3. Replace YourWindowsUsername with your actual Windows username in each command

git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0001-Add-Eversense-E3-365-CGM-plugin.patch

git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0002-E3-fixes-SingleByte-packets-force-sync-TOO_SOON-dark.patch

git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0003-Fix-TOO_SOON-calibration-summary-display-in-dark-mod.patch

git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0004-Sync-Eversense-plugin-to-latest-from-AndroidAPSEvers.patch

git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0005-pushing-changes.patch

git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0006-Fix-suppress-Recalculated-Data-Used-warning-for-Ever.patch

---

## Step 5 - Sync and Build

1. In Android Studio click File then Sync Project with Gradle Files
2. Wait for the sync to complete
3. Click Build then Make Project
4. Wait for the build to complete
5. Click Run then Run app to install on your phone

---

## What These Patches Add

| Patch | Description |
| --- | --- |
| 0001 | Eversense E3/365 CGM plugin - core BLE driver and AAPS integration |
| 0002 | E3 fixes - SingleByte packets, force sync, calibration |
| 0003 | Fix TOO_SOON calibration display in dark mode |
| 0004 | Sync Eversense plugin with latest updates |
| 0005 | Additional Eversense updates |
| 0006 | Fix: suppress Recalculated Data Used yellow triangle warning for E3 and E365 |

---

## Transmitter Support

| Transmitter | Notes |
| --- | --- |
| Eversense E3 (180-day) | Official Eversense app must run alongside AndroidAPS |
| Eversense 365 (1-year) | Standalone - no official app needed |

---

## Related

- Original PR: https://github.com/nightscout/AndroidAPS/pull/4474
- Full 3.4.2.2 build with Eversense: https://github.com/CAPTCG/AndroidAPS-3.4.2.2-Eversense