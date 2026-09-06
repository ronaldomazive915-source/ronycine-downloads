#!/bin/bash
sed -i 's/CONFIGURACOES("Configurações", "Settings")/CONFIGURACOES("Configurações", "Settings"),\n    DISPOSITIVOS("Dispositivos", "Smartphone")/g' app/src/main/java/com/example/ui/viewmodel/AdminViewModel.kt
