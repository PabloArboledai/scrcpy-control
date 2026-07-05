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
El sistema es completamente autónomo y no requiere intervención humana para la distribución:
1. **Compilación Limpia:** Ejecuta siempre `./gradlew clean` para asegurar la integridad de los recursos visuales y el branding.
2. **Nombre de Archivo:** El archivo de salida es siempre `ControlDroid.apk`.
3. **Distribución Dual Automática:**
   - **Telegram:** Envío directo al bot `@ControlDroidBot` con formato Markdown.
   - **Enlace Público:** Generación de un enlace de descarga externo (vía file.io) incluido en el mensaje de Telegram.
4. **Seguridad:** Las credenciales (Tokens y IDs) están protegidas en `modal.Secret`.
5. **Mantenimiento:** Los agentes solo deben verificar que `builder.py` reporte `✅ Envío a Telegram exitoso` al finalizar. No es necesario subir el APK manualmente a ningún sitio.

## 5. Conectividad Universal con Tailscale
Para garantizar la conexión sin importar la red:
- **IP Prioritaria:** Se pre-configura la IP de Tailscale `100.91.47.35`.
- **Puerto Fijo:** Se utiliza el puerto `42529` para evitar conflictos de escaneo.
- Ambos dispositivos deben tener la sesión de Tailscale activa.

## 5. Gestión de Iconos
Para cambiar el icono, reemplazar `controldroid_icon.png` en la raíz y ejecutar el script de redimensionado en `/tools/update_icons.py`.

---
**NOTA PARA AGENTES FUTUROS:** No reinventes la rueda. Lee los scripts en `backend/modal` y `tools` antes de realizar cambios estructurales.
