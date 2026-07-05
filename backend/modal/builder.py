import modal
import os
import subprocess

stub = modal.App("controldroid-builder")

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

@stub.function(image=image, timeout=1200)
def build_apk():
    # Clonar el repo dentro de Modal
    repo_url = "https://github.com/PabloArboledai/scrcpy-control.git"
    subprocess.run(["git", "clone", repo_url, "/app"])
    os.chdir("/app")
    
    # Dar permisos
    subprocess.run(["chmod", "+x", "gradlew"])
    
    # Compilar
    result = subprocess.run(["./gradlew", "assembleDebug"], capture_output=True, text=True)
    
    if result.returncode != 0:
        return {"status": "error", "log": result.stdout + result.stderr}
    
    # Buscar el APK
    apk_path = ""
    for root, dirs, files in os.walk("app/build/outputs/apk/debug"):
        for file in files:
            if file.endswith(".apk"):
                apk_path = os.path.join(root, file)
                break
    
    if not apk_path:
        return {"status": "error", "log": "APK not found"}
        
    with open(apk_path, "rb") as f:
        apk_data = f.read()
        
    return {"status": "success", "apk_name": os.path.basename(apk_path), "apk_data": apk_data}

if __name__ == "__main__":
    with stub.run():
        print("Starting build...")
        result = build_apk.remote()
        if result["status"] == "success":
            with open(result["apk_name"], "wb") as f:
                f.write(result["apk_data"])
            print(f"Build successful: {result['apk_name']}")
        else:
            print(f"Build failed: {result['log']}")
