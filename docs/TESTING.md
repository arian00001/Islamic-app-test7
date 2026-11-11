# Test plan

Run these checks on a physical device. Emulators cannot validate sensors,
accessibility blocking or VPN behaviour.

## 1. Shell

1. Launch the app: the production URL loads with a progress bar.
2. Rotate the device: the page keeps its state.
3. Press back inside the site: navigation goes back, then exits.
4. Turn airplane mode on and reload: the offline panel appears with Retry.

## 2. Bridge

1. In the page console run: NoorBridge.isAvailable() - expect true.
2. Run: await NoorBridge.device.getInfo() - expect model, sdkInt, appVersion.
3. Run: await NoorBridge.quran.status() - expect isPlaying false.

## 3. Quran audio and listening counter

1. Start playback; lock the device. Audio keeps playing with a media notification.
2. Pause from the lock screen: listeningTick stops advancing.
3. Note the remaining seconds, seek forward: remaining time must not jump.
4. Play until the goal completes: a completion notification appears and blocked
   apps unlock.

## 4. App blocking

1. Grant Usage Access and enable the accessibility service.
2. Select two apps and start a 5 minute goal.
3. Open a blocked app: the block screen appears with the countdown.
4. Press emergency unlock: blocking stops and the goal stays open.

## 5. Prayer and alarms

1. Request prayer times for your city and compare with a known timetable.
2. Schedule an adhan alarm one minute ahead; confirm the notification sounds.
3. Revoke exact alarm permission and schedule again: the response says
   "exact: false" instead of pretending it is exact.

## 6. Qibla

1. Call NoorBridge.prayer.qibla(23.81, 90.41): bearing should be close to 278 degrees.
2. Start the compass and rotate the device: compass events fire.
3. On a device without a compass the response says available false.

## 7. Focus timer

1. Start a 1 minute focus session, background the app: notification fires at the end.
2. Kill the app mid-session: the session is restored on next launch.
3. Reboot the device: the alarm is rescheduled.

## 8. Protection

1. Enable protection and accept the VPN prompt.
2. Enable again: status reports running true.
3. Open a blocklisted domain: it fails to resolve.
4. Disable protection: the VPN key icon disappears.

## 9. Customization studio

1. Change a theme: the whole app switches palette and the choice survives a reload.
2. Icon Studio: pick an icon, tap "Add to home" and accept the launcher prompt.
3. Widget Studio: save the ayah widget, place it, then press next/previous on the widget itself.
4. Tasbih widget: tap it and confirm the count increments without opening the app.
5. Wallpaper Studio: set a wallpaper for home and lock, then check both screens.
6. Disable the bridge (open the site in Chrome) and confirm the studio still saves locally and
   clearly says which buttons need the Android app.

## 10. Build pipeline

1. Push a commit: the Actions run builds a debug APK artifact.
2. Without signing secrets, the summary states the release build was skipped.
3. With secrets, release APK and AAB artifacts appear.
