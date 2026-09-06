#!/bin/bash
awk '
/val catalogVersion = firebaseService.catalogVersion/ {
    print $0
    print "    val isDeviceBlocked = firebaseService.isDeviceBlocked"
    print ""
    print "    fun initDeviceManager(prefs: android.content.SharedPreferences) {"
    print "        firebaseService.initDeviceManager(prefs)"
    print "    }"
    next
}
{print}
' app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt > temp_main_vm.kt && mv temp_main_vm.kt app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt
