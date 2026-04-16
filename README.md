# Eversense E3/365 CGM Plugin for AndroidAPS dev

Patches to add Eversense E3 and E365 CGM support to any AndroidAPS dev build.

Developed by **n0rb33r7**, **bastiaanv**, and **CAPTCG**.

---

## Requirements

- Android Studio installed
- Git installed

---

## Step 1 — Clone AndroidAPS

1. Open Android Studio
2. Click **File** → **New** → **Project from Version Control**
3. Enter this URL: `https://github.com/nightscout/AndroidAPS.git`
4. Choose a folder on your computer to save the project
5. Click **Clone**
6. When asked to open the project, click **Yes**

---

## Step 2 — Switch to the dev branch

1. Look at the bottom right corner of Android Studio
2. Click the branch name (it may show **master** or **main**)
3. In the list, find **origin/dev**
4. Click **Checkout**
5. Wait for Android Studio to finish switching branches

> **Note:** If you don't see `origin/dev`, click **Fetch** first to update the list of remote branches.

---

## Step 3 — Download the Eversense patches

1. Go to `https://github.com/CAPTCG/AndroidAPS-Eversense-`
2. Click the green **Code** button
3. Click **Download ZIP**
4. Save the ZIP file to your Downloads folder
5. Extract the ZIP file:
   - **Windows:** Right-click the ZIP → **Extract All** → extract to your Downloads folder
   - **Mac/Linux:** Double-click the ZIP or run `unzip` in Terminal
6. After extraction you will see a folder called `AndroidAPS-Eversense--master`
7. Inside that folder there is another folder also called `AndroidAPS-Eversense--master` — the patch files are inside this inner folder

---

## Step 4 — Apply the patches

1. In Android Studio, click **Terminal** at the bottom of the screen
2. Run each of the following 6 commands one at a time, pressing Enter after each

**Windows** — replace `YourWindowsUsername` with your actual Windows username:

```
git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0001-Add-Eversense-E3-365-CGM-plugin.patch
git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0002-E3-fixes-SingleByte-packets-force-sync-TOO_SOON-dark.patch
git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0003-Fix-TOO_SOON-calibration-summary-display-in-dark-mod.patch
git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0004-Sync-Eversense-plugin-to-latest-from-AndroidAPSEvers.patch
git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0005-pushing-changes.patch
git am C:/Users/YourWindowsUsername/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0006-Fix-suppress-Recalculated-Data-Used-warning-for-Ever.patch
```

**Mac/Linux** — paths use `~/Downloads/` instead:

```
git am ~/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0001-Add-Eversense-E3-365-CGM-plugin.patch
git am ~/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0002-E3-fixes-SingleByte-packets-force-sync-TOO_SOON-dark.patch
git am ~/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0003-Fix-TOO_SOON-calibration-summary-display-in-dark-mod.patch
git am ~/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0004-Sync-Eversense-plugin-to-latest-from-AndroidAPSEvers.patch
git am ~/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0005-pushing-changes.patch
git am ~/Downloads/AndroidAPS-Eversense--master/AndroidAPS-Eversense--master/0006-Fix-suppress-Recalculated-Data-Used-warning-for-Ever.patch
```

> **If a patch fails to apply:** Run `git am --abort` to cancel, then check that you are on the correct branch and that your working tree is clean. Patch conflicts can occur if the `dev` branch has moved ahead since these patches were created — if this happens, please open an issue on this repo.

---

## Step 5 — Sync and Build

1. In Android Studio, click **File** → **Sync Project with Gradle Files**
2. Wait for the sync to complete
3. Click **Build** → **Make Project**
4. Wait for the build to complete
5. Click **Run** → **Run app** to install on your phone

> **Important — signing:** This build uses a debug signing key. If you have a previously installed release-signed version of AndroidAPS on your phone, you must uninstall it first before installing this build, otherwise the installation will fail due to a signature mismatch.

---

## What these patches add

| Patch | Description |
|-------|-------------|
| 0001 | Eversense E3/365 CGM plugin — core BLE driver and AAPS integration |
| 0002 | E3 fixes — SingleByte packets, force sync, calibration |
| 0003 | Fix TOO_SOON calibration display in dark mode |
| 0004 | Sync Eversense plugin with latest updates |
| 0005 | Additional Eversense updates |
| 0006 | Fix: suppress Recalculated Data Used yellow triangle warning for E3 and E365 |

---

## Transmitter support

| Transmitter | Notes |
|-------------|-------|
| Eversense E3 (180-day) | Official Eversense app must run alongside AndroidAPS |
| Eversense 365 (1-year) | Standalone — no official app needed |

---

## Related

- Original PR: [nightscout/AndroidAPS#4474](https://github.com/nightscout/AndroidAPS/pull/4474)
- Full 3.4.2.2 build with Eversense: [CAPTCG/AndroidAPS-3.4.2.2-Eversense](https://github.com/CAPTCG/AndroidAPS-3.4.2.2-Eversense)
