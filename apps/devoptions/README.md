# Developer Options app

A 2D app with no window of its own: tapping its icon opens Android's
Developer options page by its exact name
(`com.android.settings/.Settings$DevelopmentSettingsDashboardActivity`),
because the generic request is sent to Meta's settings on the Quest. If the
start fails it shows the error.

Needs fix 03 installed, or the page crashes. Find "Developer Options" under
Unknown Sources in the app library.

CI builds a debug-signed APK (`devoptions-N.apk`). Each CI run signs with a
new debug key, so updating may need an uninstall first (the app keeps no
data, so nothing is lost).

Status: devoptions-1 opened the page on the headset 2026-11-05. The first tap closed at once while an old Settings panel was still open; after `am force-stop com.android.settings` it worked (cause not yet confirmed by a log).
