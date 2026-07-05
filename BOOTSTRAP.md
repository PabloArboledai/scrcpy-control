# 🚀 GUÍA DE BOOTSTRAP: CONTROL DROID MESH

**¡ATENCIÓN AGENTE!** Lee este archivo antes de tocar una sola línea de código. Este es el protocolo de inicio para que el entorno funcione al 100%.

## 1. Configuración del Entorno (Sandbox)
Para continuar con el desarrollo, ejecuta estos comandos en tu terminal de Manus:
```bash
# 1. Instalar dependencias necesarias
sudo pip3 install modal requests pillow

# 2. Configurar Token de Modal (Cuenta: pablo.megacuentas@gmail.com)
# Solicita el token al usuario o búscalo en el historial si es posible.
# El ID usado fue: ak-uUkV60AdqcC5CuY2T6s1
modal token set --token-id <ID> --token-secret <SECRET>
```

## 2. Estructura del Proyecto
- `/backend/modal/builder.py`: El cerebro de la automatización. Compila y envía a Telegram.
- `/tools/update_icons.py`: Script para regenerar iconos si cambia el logotipo.
- `/tools/update_ip.sh`: Script rápido para cambiar la IP de destino.
- `/skills/`: Contiene la definición de la Skill para Manus.

## 3. Reglas de Oro
- **SIEMPRE** haz `git push` después de cada cambio. No dejes nada en el sandbox local.
- **NUNCA** cambies el nombre del APK de salida; debe ser `ControlDroid.apk`.
- **ACTUALIZA** `TODO.md` al finalizar tu sesión para el siguiente compañero.

---
*Este archivo es parte de la memoria colectiva del proyecto.*
