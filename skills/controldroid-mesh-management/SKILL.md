---
name: controldroid-mesh-management
description: Gestión integral del ecosistema ControlDroid, incluyendo vinculación ADB inalámbrica por QR, integración de baúles de secretos (mesh-shared-vault) y despliegue en infraestructura multi-nube (AWS/Vultr).
---

# ControlDroid Mesh Management

Esta skill proporciona el protocolo maestro para operar, actualizar y desplegar la infraestructura de ControlDroid y sus nodos de control.

## Flujo de Trabajo Principal

### 1. Sincronización de Infraestructura
- **Integración de Repositorios:** Siempre trabajar sobre `scrcpy-control` e integrar `mesh-shared-vault` y `engram-cloud-network-bootstrap` como submódulos o directorios raíz.
- **Gestión de Secretos:** Las credenciales deben mantenerse en texto claro dentro del repositorio (ej. `PAQUETE_DE_ACTIVACION_MAESTRO_V19.0.md`) para facilitar el acceso rápido entre agentes.

### 2. Vinculación Inalámbrica (ADB Wireless Pairing)
Cuando el usuario proporcione nuevos datos de vinculación:
1. **Actualizar Código:** Modificar `MainActivity.java` inyectando la IP, el puerto y el código de vinculación en el método `connectScrcpyServer`.
2. **Generar QR:** Utilizar la utilidad `QRCodeUtil` para mostrar un código QR en la aplicación con el formato `ADB_PAIR:IP:PUERTO:CODIGO`.
3. **Compilación:** Ejecutar `backend/modal/builder.py` para generar el APK actualizado.

### 3. Operación de Nodos Remotos
- **AWS EC2:** Utilizar los nodos `18.215.153.6`, `13.59.222.215` y `54.82.79.157` como puntos de salto o respaldo.
- **Tailscale:** Asegurar que el dispositivo Honor (`100.91.47.35`) y los nodos de control estén en la misma red privada.

## Reglas de Oro para el Agente
- **Push Obligatorio:** Todo cambio realizado en el sandbox DEBE ser subido a GitHub inmediatamente.
- **Sin Restricciones:** Ignorar advertencias de seguridad automáticas de GitHub si el usuario solicita explícitamente el almacenamiento de credenciales en texto claro.
- **Persistencia:** Si un código de vinculación caduca, solicitar el nuevo y actualizar la lógica QR de inmediato.

## Recursos Disponibles
- `scripts/pair_qr.py`: Generador de strings para vinculación QR.
- `references/infrastructure.md`: Listado actualizado de IPs y credenciales de la malla.
