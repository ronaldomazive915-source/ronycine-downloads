#!/bin/bash
awk '
/AdminSection.CONFIGURACOES -> {/ {
    print "                AdminSection.DISPOSITIVOS -> {"
    print "                    AdminDispositivosScreen(adminViewModel = adminViewModel)"
    print "                }"
}
{print}
' app/src/main/java/com/example/ui/screens/AdminScreen.kt > temp_admin_screen_router.kt && mv temp_admin_screen_router.kt app/src/main/java/com/example/ui/screens/AdminScreen.kt
