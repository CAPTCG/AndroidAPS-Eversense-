# AAPS
* Check the wiki: https://wiki.aaps.app
*  Everyone who's been looping with AAPS needs to fill out the form after 3 days of looping  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

[![Support Server](https://img.shields.io/discord/629952586895851530.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/4fQUWHZ4Mw)

[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/master.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/master)
[![Crowdin](https://d322cqt584bo4o.cloudfront.net/androidaps/localized.svg)](https://translations.aaps.app/project/androidaps)
[![Documentation Status](https://readthedocs.org/projects/androidaps/badge/?version=latest)](https://wiki.aaps.app/en/latest/?badge=latest)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/master/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS)

DEV:
[![CircleCI](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev.svg?style=svg)](https://circleci.com/gh/nightscout/AndroidAPS/tree/dev)
[![codecov](https://codecov.io/gh/nightscout/AndroidAPS/branch/dev/graph/badge.svg?token=EmklfIV6bH)](https://codecov.io/gh/nightscout/AndroidAPS/tree/dev)

---

## This Branch: Eversense CGM (E3 / E365)

This branch adds **Eversense CGM integration** on top of upstream AAPS — direct BLE connection to Eversense E3 and E365 transmitters as a native AAPS BG source, with calibration, alarms, DMS portal sync, and (for E365) cloud upload.

This is an experimental, community-developed modification. It is not approved by any regulatory body. **Discuss any changes to your treatment with your endocrinologist before use, and always keep fingerstick meter access as a backup.**

---

## Eversense CGM: Getting Started

### Sensor Insertion (Done by Your Doctor)
1. Visit your trained healthcare provider for the procedure. Only physicians who have completed the Eversense CGM Insertion and Removal Training Program may perform the insertion.
2. The tiny sensor is placed just under the skin of your upper arm via a small incision, closed with Steri-Strips — usually no stitches required.

### Incision Care (First Few Days)
3. Avoid strenuous activities that may pull at the incision or cause heavy sweating while it heals. Remove the Steri-Strips within a few days.

### Warm-Up Phase
4. After insertion, the official Eversense app will show "Warm Up Phase." The sensor must complete this period before calibration is accepted. Do not attempt to calibrate during this phase.
5. After warm-up, the official Eversense app will move into an **Initialization Phase**. This must also be completed using the official Eversense app before proceeding.
6. While still in the official Eversense app, set your **Low and High BG Alert levels**. These are stored on the transmitter itself, so this only needs to be done once per transmitter (or whenever you want to change them) — but it must be done through the official app; AAPS does not write these settings to the transmitter.
7. Once warm-up, initialization, and alert levels are all complete, proceed to set up the AAPS plugin.

---

### ⚠️ IMPORTANT — E365 vs E3: Official App Coexistence Differs

The Eversense transmitter can only maintain **one active Bluetooth connection at a time**. Whether the official Eversense app can safely coexist with AAPS depends on which transmitter you have.

#### E365 — the official app and AAPS cannot reliably coexist

Through real-world testing, we found that leaving the official Eversense app installed alongside AAPS on an E365 setup leads to intermittent BLE connection contention — the official app's more aggressive background reconnect behavior can "win" the race for the transmitter's single connection slot after any interruption, leaving AAPS unable to reconnect on its own until you manually intervene.

**The reliable setup for E365 is:**

1. Complete warm-up, initialization, and on-transmitter Low/High BG alert-level setup using the official Eversense app (see "Warm-Up Phase" above) — these all happen once, before you touch AAPS.
2. Once that's done, **completely delete the official Eversense app from your phone** (not just force-stop — a full uninstall). This removes the only thing AAPS was competing against for the connection.

**⚠️ Warning:** if you do not fully delete the official Eversense app, AAPS can lose the transmitter connection and fail to reconnect on its own in any of the following situations:
- The Bluetooth connection is interrupted (e.g. Bluetooth toggled off/on, moving out of range, brief RF interference such as walking through a security/metal detector).
- The phone is restarted.
- AAPS is updated (installing a new build/APK).

In each of these cases, the official app — if still installed — can grab the transmitter's connection before AAPS gets a chance to, and you will need to manually reconnect. Fully deleting the official app removes this risk entirely.

3. Now connect the E365 transmitter to AAPS (see "Transmitter & AAPS Setup" below). With no competing app, AAPS's own reconnect logic reliably recovers the connection on its own after Bluetooth interruptions, phone restarts, etc. — no manual reconnect should be needed.
4. If you ever need to change your alert levels again, or check firmware updates, you'll need to temporarily reinstall the official app, make your changes, and delete it again afterward.

#### E3 — the official app and AAPS can run together

Unlike the E365, the E3 does not exhibit the same contention problem — it can remain connected to **both** the official Eversense app and AAPS simultaneously in coexistence mode. You do not need to delete the official app for E3 use.

---

### Transmitter & AAPS Setup

8. **E365 only:** confirm you've completed warm-up, initialization, and alert-level setup above, then completely delete the official Eversense app.
9. **E3 only:** you may leave the official app installed; no disconnect step is required.
10. In AAPS, go to Config Builder and select Eversense as your BG source.
11. Open the Eversense plugin settings and enter your Eversense DMS account credentials (username and password). Both the E3 and E365 require credentials to authenticate with the Eversense cloud and retrieve your transmitter's security certificate at every new connection.
12. Tap Connect to find your transmitter and pair it via Bluetooth.

---

## Eversense Plugin Settings — Functions Explained

### Credentials
Enter your Eversense DMS account username and password. Both the E3 and E365 require credentials to authenticate with the Eversense cloud and retrieve your transmitter's security certificate at every new connection.

**European Region:** Underneath Credentials is a **European Region** toggle. Enable it if your Eversense account and transmitter were registered in Europe — this routes login and cloud/DMS sync through Eversense's European servers instead of the US ones. Leave it off for a US-registered transmitter. Changing this toggle invalidates your cached login token, so AAPS will need to re-authenticate on the next sync.

### Calibration
Displays your current calibration phase, the date of your last calibration, and when your next calibration is due. When the transmitter is ready, the Calibrate button becomes active — tap it, enter your fingerstick reading, and the value is sent directly to the transmitter over Bluetooth. Calibration is not accepted during the warm-up or initialization phase. An alert to calibrate will be displayed each time a calibration is required.

### Transmitter Placement Signal
Shows real-time signal strength between the transmitter and the sensor. Signal levels are:
- Excellent (>=75)
- Good (48-74)
- Low (30-47)
- Poor (25-29)
- Very Poor (1-24)

If the signal is poor for 3 or more consecutive readings, you will receive an urgent notification. Tap it to open the placement guide.

### Notifications & Alerts
The plugin provides the following system notifications:
- Transmitter not placed — urgent alert with a link to the placement guide.
- Firmware version — shown once per firmware version, prompting you to check for updates in the official Eversense app.
- Transmitter alarms — high/low glucose and other transmitter alerts are relayed as AAPS notifications.
- Calibration required — an alert to calibrate is displayed each time a calibration is required.

### DMS Portal Sync
After every glucose reading, the plugin automatically uploads your data to the Eversense DMS web portal so your care team can view it in real time. Each sync uploads your latest glucose value, trend, signal strength, and battery level; your glucose history; and device diagnostic logs.

These same uploads are also sent to the **Eversense NOW** app, available on the Play Store. Eversense NOW lets a follower see your live BG readings on their own device — useful for caregivers of people with T1D who want remote visibility into glucose levels.

---

## Daily Use

13. Apply a fresh adhesive patch each morning and wear the transmitter over the sensor site on your upper arm.
14. Charge the transmitter daily — no glucose data is collected while it is charging.
15. Check AAPS for your glucose reading, trend arrows, and alerts.
16. Monitor placement signal in the plugin settings if readings seem inconsistent.
17. Calibrate when due — tap the Calibrate button and enter your fingerstick reading.
18. **E3 only:** if you need to switch back to the official Eversense app temporarily, go to AAPS Settings → CGM and delete the CGM source, then reconnect in the official app.
19. **E365 only:** if you need to make on-transmitter changes (alert levels, firmware check), you will need to temporarily reinstall the official Eversense app, make your changes, then delete it again before reconnecting to AAPS (see the E365 note above).

---

## Known Limitations

- The Eversense connection logic is actively evolving. Check the branch's commit history for the latest state before relying on it in a real-world dosing decision.
- The E365/official-app contention issue is a platform-level Android Bluetooth limitation (only one app can hold an active GATT connection to the transmitter at a time), not a bug specific to either app. There is no way to make two apps share the connection simultaneously for the E365.
