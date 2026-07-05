# 🛠 Guía de Ingeniería ControlDroid

Esta guía documenta las estrategias y automatizaciones implementadas para evitar errores recurrentes y asegurar la evolución del proyecto.

## 1. Versionado y Naming (Anti-Sobrescritura)
Para permitir múltiples versiones instaladas simultáneamente:
- **ApplicationId Dinámico:** Se modifica en `app/build.gradle` usando un timestamp.
- **Nombre de Archivo:** El script `builder.py` renombra el APK final con la fecha y hora.
- **Nombre de App:** El recurso `app_name` en `strings.xml` debe ser siempre `ControlDroid`.

## 2. Protocolo de Compilación Limpia
**ERROR EVITADO:** Los cambios en el layout (como el botón QR) a veces no se reflejan por el caché de Gradle.
- **Solución:** Siempre ejecutar `./gradlew clean` antes de `assembleDebug`. Esto se ha automatizado en `backend/modal/builder.py`.

## 3. Vinculación Inalámbrica por QR
- **Lógica:** Ubicada en `MainActivity.java` y `QRCodeUtil.java`.
- **Formato de Datos:** `ADB_PAIR:IP:PUERTO:CODIGO`.
- **UI:** El botón `button_qr_pair` debe tener visibilidad `visible` y un tamaño suficiente para ser escaneado fácilmente.

## 4. Automatización Total de Entrega (CI/CD)
El sistema es ahora completamente autónomo. El script `builder.py` integra el envío a Telegram directamente desde la infraestructura de Modal.com:
1. **Compilación Limpia:** Ejecuta siempre `./gradlew clean` para asegurar la integridad de los recursos visuales.
2. **Nombre de Archivo:** Genera el archivo con el nombre definitivo `ControlDroid.apk`.
3. **Envío Automático:** Utiliza `modal.Secret` para inyectar de forma segura el token del bot y el chat ID.
4. **Verificación:** El sistema verifica el código de respuesta de la API de Telegram y reporta el éxito o fallo del envío.
5. **Bots Configurados:**
   - **@ControlDroidBot:** Destino principal automático.
   - **@Archivosbotbot:** Respaldo configurado en el código.

## 5. Conectividad Universal con Tailscale
Para garantizar la conexión sin importar la red:
- **IP Prioritaria:** Se pre-configura la IP de Tailscale `100.91.47.35`.
- **Puerto Fijo:** Se utiliza el puerto `42529` para evitar conflictos de escaneo.
- Ambos dispositivos deben tener la sesión de Tailscale activa.

## 5. Gestión de Iconos
Para cambiar el icono, reemplazar `controldroid_icon.png` en la raíz y ejecutar el script de redimensionado en `/tools/update_icons.py`.

---
**NOTA PARA AGENTES FUTUROS:** No reinventes la rueda. Lee los scripts en `backend/modal` y `tools` antes de realizar cambios estructurales.
