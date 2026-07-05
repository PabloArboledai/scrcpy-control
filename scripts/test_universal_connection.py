import socket
import subprocess
import platform
import time
import sys

def check_ping(host):
    """Verifica la latencia hacia el host."""
    param = '-n' if platform.system().lower() == 'windows' else '-c'
    command = ['ping', param, '3', host]
    try:
        output = subprocess.check_output(command, stderr=subprocess.STDOUT, universal_newlines=True)
        return True, output
    except subprocess.CalledProcessError as e:
        return False, e.output

def get_network_info():
    """Obtiene información básica de la red local."""
    try:
        hostname = socket.gethostname()
        local_ip = socket.gethostbyname(hostname)
        return local_ip
    except:
        return "Desconocida"

def test_port(ip, port):
    """Prueba si un puerto específico está abierto."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    result = sock.connect_ex((ip, port))
    sock.close()
    return result == 0

def run_diagnostic(target_ip, target_port):
    print("="*50)
    print("🚀 DIAGNÓSTICO DE CONEXIÓN UNIVERSAL - CONTROLDROID")
    print("="*50)
    
    local_ip = get_network_info()
    print(f"[*] IP Local Detectada: {local_ip}")
    print(f"[*] Objetivo: {target_ip}:{target_port}")
    
    # 1. Prueba de Ping
    print("\n[1/3] Probando latencia (Ping)...")
    success, output = check_ping(target_ip)
    if success:
        print("✅ Conexión básica establecida.")
        if "100." in target_ip:
            print("🔗 Detectada red Tailscale/VPN.")
    else:
        print("❌ No hay respuesta de Ping. Esto es normal en algunas redes móviles o VPNs estrictas.")

    # 2. Prueba de Puerto ADB
    print("\n[2/3] Probando puerto ADB (TCP)...")
    if test_port(target_ip, target_port):
        print(f"✅ Puerto {target_port} ABIERTO. El dispositivo está listo para conectar.")
    else:
        print(f"❌ Puerto {target_port} CERRADO o BLOQUEADO.")
        print("   Sugerencia: Verifica que la 'Depuración Inalámbrica' esté activa en el dispositivo Honor.")

    # 3. Análisis de Ruta (Traceroute simplificado)
    print("\n[3/3] Analizando ruta de red...")
    if "100." in target_ip:
        print("📍 Ruta detectada: Túnel VPN (Tailscale).")
    elif target_ip.startswith("192.168.") or target_ip.startswith("10."):
        print("📍 Ruta detectada: Red Local (Wi-Fi).")
    else:
        print("📍 Ruta detectada: Red Externa / Datos Móviles.")

    print("\n" + "="*50)
    print("📢 CONCLUSIÓN:")
    if test_port(target_ip, target_port):
        print("¡Todo listo! Puedes usar la app ControlDroid para conectar ahora.")
    else:
        print("Hay un bloqueo de red. Asegúrate de que ambos dispositivos estén en la misma VPN (Tailscale) si no estás en la misma Wi-Fi.")
    print("="*50)

if __name__ == "__main__":
    # Datos por defecto del dispositivo Honor del usuario
    DEFAULT_IP = "192.168.205.42"
    DEFAULT_PORT = 42529
    
    target_ip = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_IP
    target_port = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_PORT
    
    run_diagnostic(target_ip, target_port)
