import modal
import os
import subprocess
from datetime import datetime

app = modal.App("controldroid-builder")

image = (
    modal.Image.debian_slim()
    .apt_install("openjdk-17-jdk", "wget", "unzip", "git")
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

@app.function(image=image, timeout=1200)
def build_apk():
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
        return {"status": "error", "log": f"Build failed. See logs for details. Stdout: {result.stdout}\nStderr: {result.stderr}"}
    

    
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
    # Generar nombre único
    timestamp = datetime.now().strftime("%Y%m%d_%H%M")
    final_name = f"ControlDroid_{timestamp}.apk"
    
    with open(apk_path, "rb") as f:
        apk_data = f.read()
        
    return {"status": "success", "apk_name": final_name, "apk_data": apk_data}

if __name__ == "__main__":
    with app.run():
        print("Starting build...")
        result = build_apk.remote()
        
    if result["status"] == "success":
        apk_name = result["apk_name"]
        with open(apk_name, "wb") as f:
            f.write(result["apk_data"])
        print(f"Build successful: {apk_name}")
        print(f"APK saved locally as {apk_name}")
        
        # Enviar a Telegram
        import requests
        bot_token = "8987478008:AAHd6jhsRyBVsbExWYecI6NgqavGiAp3Lew"
        chat_id = "8776480439"
        url = f"https://api.telegram.org/bot{bot_token}/sendDocument"
        print(f"Enviando {apk_name} a Telegram...")
        with open(apk_name, 'rb') as f:
            r = requests.post(url, data={'chat_id': chat_id}, files={'document': f})
            print(f"Respuesta Telegram: {r.status_code} - {r.text}")
    else:
        print(f"Build failed: {result['log']}")
