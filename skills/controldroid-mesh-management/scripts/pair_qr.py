import os
import sys

def generate_qr_data(ip, port, code):
    """Genera el string de datos para el código QR de vinculación ADB."""
    return f"ADB_PAIR:{ip}:{port}:{code}"

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Uso: python3 pair_qr.py <IP> <PORT> <CODE>")
        sys.exit(1)
    
    ip = sys.argv[1]
    port = sys.argv[2]
    code = sys.argv[3]
    print(generate_qr_data(ip, port, code))
