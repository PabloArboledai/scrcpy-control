# Infraestructura ControlDroid Mesh

## Nodos de Control (VPS)
- **EC2 Maestro:** 18.215.153.6 (Admin / Atenea!3579!)
- **EC2 Secundario:** 13.59.222.215 (Admin / Atenea!3579!)
- **EC2 Terciario:** 54.82.79.157 (Admin / Atenea!3579!)
- **Vultr VPS:** 45.76.251.25 (Admin / Atenea!3579!)

## Red Privada (Tailscale)
- **IP Honor Target:** 100.91.47.35
- **IP AWS Mesh:** 100.114.3.88
- **IP HP One:** 100.104.166.73
- **IP Lenovo:** 100.96.218.12

## Protocolo de Vinculación
1. El agente debe verificar el puerto y código de vinculación inalámbrica actual en el dispositivo Honor.
2. Generar el código QR con el formato `ADB_PAIR:IP:PUERTO:CODIGO`.
3. Inyectar estos datos en `MainActivity.java` para automatizar el `pairDevice`.
4. Compilar el APK usando el script `builder.py` en Modal.com.
