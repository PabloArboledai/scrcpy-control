#!/bin/bash

# Script para actualizar la IP y Puerto en el código fuente de ControlDroid
# Uso: ./update_ip.sh <NUEVA_IP> <NUEVO_PUERTO>

if [ "$#" -ne 2 ]; then
    echo "Uso: $0 <IP> <PUERTO>"
    exit 1
fi

IP=$1
PUERTO=$2
FILE="../app/src/main/java/org/client/scrcpy/MainActivity.java"

if [ ! -f "$FILE" ]; then
    echo "Error: No se encuentra el archivo $FILE"
    exit 1
fi

# Actualizar IP y Puerto en MainActivity.java
sed -i "s/serverAdr = (hostEdit != null) ? hostEdit.getText().toString() : \".*\";/serverAdr = (hostEdit != null) ? hostEdit.getText().toString() : \"$IP:$PUERTO\";/g" "$FILE"
sed -i "s/String host = (hostEdit != null) ? hostEdit.getText().toString() : \".*\";/String host = (hostEdit != null) ? hostEdit.getText().toString() : \"$IP\";/g" "$FILE"
sed -i "s/String qrData = \"ADB_PAIR:\" + host + \":.*:665439\";/String qrData = \"ADB_PAIR:\" + host + \":$PUERTO:665439\";/g" "$FILE"

echo "✅ Código actualizado con IP: $IP y Puerto: $PUERTO"
