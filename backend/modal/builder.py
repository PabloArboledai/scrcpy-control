import modal
import os
import subprocess

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
    repo_url = "https://github.com/PabloArboledai/scrcpy-control.git"
    subprocess.run(["git", "clone", repo_url, "/app"])
    os.chdir("/app")
    
    # Dar permisos
    subprocess.run(["chmod", "+x", "gradlew"])
    
    # Compilar usando el flavor específico
    result = subprocess.run(["./gradlew", "assembleScrcpyDebug"], capture_output=True, text=True)
    
    print(result.stdout)
    print(result.stderr)
    
    # Listar archivos para depurar
    debug_list = []
    for root, dirs, files in os.walk("."):
        for file in files:
            if file.endswith(".apk"):
                debug_list.append(os.path.join(root, file))
    
    if not debug_list:
        return {"status": "error", "log": "APK not found. Files: " + str(debug_list) + "\nLog: " + result.stdout}
        
    apk_path = debug_list[0]
    with open(apk_path, "rb") as f:
        apk_data = f.read()
        
    return {"status": "success", "apk_name": os.path.basename(apk_path), "apk_data": apk_data}

if __name__ == "__main__":
    with app.run():
        print("Starting build...")
        result = build_apk.remote()
        if result["status"] == "success":
            with open(result["apk_name"], "wb") as f:
                f.write(result["apk_data"])
            print(f"Build successful: {result['apk_name']}")
        else:
            print(f"Build failed: {result['log']}")
