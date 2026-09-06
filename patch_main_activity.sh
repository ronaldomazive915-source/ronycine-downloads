#!/bin/bash
awk '
/val syncStatus by mainViewModel.syncStatus.collectAsState()/ {
    print "                val isDeviceBlocked by mainViewModel.isDeviceBlocked.collectAsState()"
    print $0
    next
}
/LaunchedEffect\(Unit\)/ {
    print $0
    print "                    mainViewModel.initDeviceManager(prefs)"
    next
}
/if \(showIntro && currentRoute == ScreenRoute.HOME.route\) {/ {
    print "                if (isDeviceBlocked) {"
    print "                    com.example.ui.screens.BlockedScreen()"
    print "                } else if (showIntro && currentRoute == ScreenRoute.HOME.route) {"
    next
}
{print}
' app/src/main/java/com/example/MainActivity.kt > temp_main_activity.kt && mv temp_main_activity.kt app/src/main/java/com/example/MainActivity.kt
