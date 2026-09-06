#!/bin/bash
awk '
/AdminNavOptionCard\(/ && /AdminSection.SINCRONIZACAO/ {
    print "                                AdminNavOptionCard("
    print "                                    title = \"Dispositivos\","
    print "                                    description = \"Gerenciar acessos e conexões\","
    print "                                    icon = Icons.Default.Smartphone,"
    print "                                    isSelected = currentSection == AdminSection.DISPOSITIVOS,"
    print "                                    onClick = { onSelectSection(AdminSection.DISPOSITIVOS) },"
    print "                                    testTag = \"admin_menu_dispositivos\""
    print "                                )"
    print ""
}
{print}
' app/src/main/java/com/example/ui/screens/AdminScreen.kt > temp_admin_screen_menu.kt && mv temp_admin_screen_menu.kt app/src/main/java/com/example/ui/screens/AdminScreen.kt
