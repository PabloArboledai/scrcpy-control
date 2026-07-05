import modal
import os
import subprocess
from datetime import datetime

app = modal.App("controldroid-builder")

image = (
    modal.Image.debian_slim()
    .apt_install("openjdk-17-jdk", "wget", "unzip", "git")
    .pip_install("requests")
    .run_commands(
        "wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip",
        "mkdir -p /sdk/cmdline-tools",
        "unzip cmdline-tools.zip -d /sdk/cmdline-tools",
        "mv /sdk/cmdline-tools/cmdline-tools /sdk/cmdline-tools/latest",
        "yes | /sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/sdk 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0'",
    )
    .env({
        "ANDROID_HOME": "/sdk",
        "JAVA_HOME": "/usr/lib/jvm/java-17-openjdk-amd64",
    })
)

@app.function(image=image, timeout=1200, secrets=[modal.Secret.from_dict({"TELEGRAM_BOT_TOKEN": "8987478008:AAHd6jhsRyBVsbExWYecI6NgqavGiAp3Lew", "TELEGRAM_CHAT_ID": "8776480439"})])
def build_apk():
    import requests
    # Clonar el repo dentro de Modal
    repo_url = os.environ.get("REPO_URL", "https://github.com/PabloArboledai/scrcpy-control.git")
    # Limpiar directorio si existe y clonar de nuevo
    print("Cleaning existing /app directory...")
    subprocess.run(["rm", "-rf", "/app"], check=True)
    print(f"Cloning repository {repo_url}...")
    subprocess.run(["git", "clone", "--depth", "1", "-b", "main", repo_url, "/app"], check=True)
    os.chdir("/app")
    
    # Dar permisos
    print("Setting execute permissions for gradlew...")
    subprocess.run(["chmod", "+x", "gradlew"], check=True)
    
    print("Cleaning project with gradlew clean...")
    # No usar check=True para clean, ya que puede fallar en entornos efímeros sin ser un error crítico
    subprocess.run(["./gradlew", "clean"])
    
    print("Compiling ControlDroid APK with gradlew assembleScrcpyDebug --stacktrace...")
    # Capturamos la salida para poder incluirla en el log de error si falla
    result = subprocess.run(["./gradlew", "assembleScrcpyDebug", "--stacktrace"], capture_output=True, text=True, check=False)
    
    if result.returncode != 0:
        print("Gradle build failed. Standard Output:")
        print(result.stdout)
        print("Standard Error:")
        print(result.stderr)
        return {"status": "error", "log": f"Build failed. Stdout: {result.stdout}\nStderr: {result.stderr}"}
    

    
    # Listar archivos para encontrar el APK
    apks = []
    for root, dirs, files in os.walk("app/build/outputs/apk"):
        for file in files:
            if file.endswith(".apk"):
                apks.append(os.path.join(root, file))
    
    if not apks:
        print("APK not found after successful build attempt.")
        return {"status": "error", "log": "APK not found. Check build output for errors."}
    
    print(f"Found APKs: {apks}")
        
    apk_path = apks[0]
    # Nombre final solicitado por el usuario
    final_name = "ControlDroid.apk"
    
    with open(apk_path, "rb") as f:
        apk_data = f.read()
        
    # Guardar localmente para el envío
    with open(final_name, "wb") as f:
        f.write(apk_data)

    # --- ENVÍO AUTOMÁTICO A TELEGRAM (DENTRO DE MODAL) ---
    bot_token = os.environ.get("TELEGRAM_BOT_TOKEN")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID")
    
    if bot_token and chat_id:
        url = f"https://api.telegram.org/bot{bot_token}/sendDocument"
        print(f"Enviando {final_name} a Telegram...")
        try:
            with open(final_name, 'rb') as f:
                r = requests.post(url, data={'chat_id': chat_id, 'caption': f'✅ ControlDroid APK generado automáticamente\n📅 {datetime.now().strftime("%Y-%m-%d %H:%M")}'}, files={'document': f})
                if r.status_code == 200:
                    print("✅ Envío a Telegram exitoso.")
                    telegram_status = "success"
                else:
                    print(f"❌ Error en Telegram: {r.text}")
                    telegram_status = f"error: {r.text}"
        except Exception as e:
            print(f"❌ Error enviando a Telegram: {e}")
            telegram_status = f"exception: {e}"
    else:
        telegram_status = "missing_credentials"

    return {"status": "success", "apk_name": final_name, "apk_data": apk_data, "telegram_status": telegram_status}

if __name__ == "__main__":
    with app.run():
        print("🚀 Iniciando compilación y envío automático...")
        result = build_apk.remote()
        
    if result["status"] == "success":
        print(f"✅ Proceso completado: {result['apk_name']}")
        print(f"📱 Estado de envío a Telegram: {result['telegram_status']}")
        # Guardar una copia local en el sandbox también
        with open(result["apk_name"], "wb") as f:
            f.write(result["apk_data"])
    else:
        print(f"❌ Fallo en el proceso: {result['log']}")
