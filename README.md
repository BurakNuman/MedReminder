# Med Reminder (Android)

Daily medicine reminders. Each medicine can have several times a day. At each time the phone
rings (looping alarm sound + vibration, over the lock screen) and **keeps ringing until you press
"I TOOK IT"** (or "TAKEN" in the notification).

## Build the APK
1. Install Android Studio (any recent version) and open this folder (`MedReminder`).
2. Let Gradle sync (it downloads Android Gradle Plugin 8.5.2 / Gradle 8.7; accept any upgrade prompt).
3. Plug in a phone (USB debugging on) and press Run, or use Build > Build APK(s).
   The APK is at `app/build/outputs/apk/debug/app-debug.apk`; copy it to the phone and install it.

## First run on the phone
- Allow notifications when asked.
- Android 14+: if the orange banner appears, tap it and allow full-screen alerts.
- Press **Test alarm** (top right) to hear what a reminder sounds like.
- On Xiaomi / Huawei / OPPO / Samsung phones, also set the app's battery mode to "Unrestricted"
  and enable "Autostart", otherwise the manufacturer may block alarms.

## How it works
- `Scheduler` books one exact alarm per medicine+time with `AlarmManager.setAlarmClock` (fires on time
  even in Doze, no special permission). Tomorrow's alarm is booked the moment today's fires.
- `AlarmReceiver` -> `RingService` (foreground service) plays the alarm sound on the alarm volume
  stream (raised to at least 60% if lower), vibrates, and shows a full-screen `AlarmActivity`.
- Pressing the button removes that medicine from the ringing list; when none are left the sound stops.
- `BootReceiver` re-books everything after reboot, app update, or time/time-zone change.
- Data is stored locally in SharedPreferences. No internet, no third-party libraries.

## Build without Android Studio (GitHub)
Create a new GitHub repository, upload the contents of this folder, open the **Actions** tab, and run
"Build APK". When it finishes (about 3 minutes), download `MedReminder-apk` from the run page,
unzip it, and install `app-debug.apk` on the phone (allow "install unknown apps").
