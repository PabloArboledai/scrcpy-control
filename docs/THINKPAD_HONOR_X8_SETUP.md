# ThinkPad + Honor X8: guía operativa de ControlDroid

## Objetivo

Usar la ThinkPad como estación de control para detectar el Honor X8 conectado por USB, validar Android Debug Bridge (ADB), instalar una APK por ADB y abrir scrcpy para reflejar y controlar el teléfono desde Windows.

## Artefactos

| Archivo | Uso |
|---|---|
| `ControlDroid_v1.4.3.apk` | Aplicación Android compilada desde `scrcpy-control`. |
| `Preparar_ControlDroid_ThinkPad.ps1` | Descarga Platform Tools y scrcpy oficiales; detecta ADB y abre scrcpy. |
| Workflow `android_build.yml` | Compila APK debug, conserva artefacto de GitHub Actions y envía a Telegram solo si existen secretos configurados. |

## Preparación de la ThinkPad

1. Conecta el Honor X8 mediante un cable USB de datos.
2. En el teléfono, habilita **Opciones de desarrollador** y **Depuración USB**.
3. Ejecuta PowerShell como el usuario habitual de Windows y corre:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\Preparar_ControlDroid_ThinkPad.ps1
```

4. Cuando Android muestre la huella RSA, toca **Permitir** y, si es apropiado, marca **Permitir siempre desde este equipo**.
5. El script ejecuta `adb devices`; la salida correcta muestra un identificador con estado `device`. Si muestra `unauthorized`, vuelve a revisar el diálogo RSA en el Honor X8.

## Control del teléfono

Cuando `adb devices` muestre estado `device`, el script abre `scrcpy --turn-screen-off --stay-awake`. El teléfono puede controlarse con teclado y mouse de la ThinkPad. No debe exponerse ADB por Internet. Para depuración inalámbrica posterior, usar el emparejamiento Wireless Debugging de Android dentro de la red privada y conservar la comprobación `adb devices`.

## Instalación de la APK

La instalación desde la ThinkPad se realiza solo cuando ADB muestre el estado `device`:

```powershell
adb install -r .\ControlDroid_v1.4.3.apk
```

La opción `-r` conserva los datos de la aplicación cuando existe una instalación compatible. Si Android bloquea la instalación, revisar la autorización de depuración y los permisos de instalación.

## Compilación y entrega

La rama `fix/apk-artifact-delivery` contiene un workflow corregido para generar la APK debug como artefacto de GitHub Actions durante 14 días. Telegram es un canal adicional; la compilación no debe depender de que la entrega por Telegram esté configurada.

## Recuperación

Para cerrar la sesión de scrcpy, cierra la ventana. Para retirar la autorización ADB, usa Opciones de desarrollador en Android y selecciona revocar autorizaciones de depuración USB. No se deben borrar herramientas ni credenciales durante una situación urgente; cualquier rotación o limpieza se agenda después de comprobar que el control de la ThinkPad y el Honor X8 funciona.
