import re

with open('app/src/main/java/org/client/scrcpy/MainActivity.java', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Remove the invalid barcodeLauncher declaration
code = re.sub(
    r'private final androidx\.activity\.result\.ActivityResultLauncher<ScanOptions> barcodeLauncher = .*?\}\);',
    '// ActivityResultLauncher eliminated, using IntentIntegrator directly.',
    code, flags=re.DOTALL
)

# 2. Add onActivityResult right before set_display_nd_touch()
code = re.sub(
    r'private void set_display_nd_touch',
    '''@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        com.google.zxing.integration.android.IntentResult result = com.google.zxing.integration.android.IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_LONG).show();
            } else {
                String qr = result.getContents();
                try {
                    if (qr.startsWith("ADB_PAIR:")) {
                        String[] parts = qr.split(":");
                        String ip = parts[1];
                        String port = parts[2];
                        String code_pairing = parts[3];
                        connectWithMesh(ip, port, code_pairing);
                    } else if (qr.startsWith("WIFI:T:ADB;")) {
                        Toast.makeText(this, "Escaneaste un QR nativo de Android. Intenta emparejar manualmente.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "QR no reconocido", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error leyendo QR", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void set_display_nd_touch''',
    code, count=1
)

# 3. Change scanBtn to use IntentIntegrator instead of barcodeLauncher
code = re.sub(
    r'ScanOptions options = new ScanOptions\(\);.*?barcodeLauncher\.launch\(options\);',
    '''com.google.zxing.integration.android.IntentIntegrator integrator = new com.google.zxing.integration.android.IntentIntegrator(MainActivity.this);
                integrator.setPrompt("Escanea el QR de ControlDroid");
                integrator.setOrientationLocked(false);
                integrator.initiateScan();''',
    code, flags=re.DOTALL
)

with open('app/src/main/java/org/client/scrcpy/MainActivity.java', 'w', encoding='utf-8') as f:
    f.write(code)

print("MainActivity.java patched successfully using IntentIntegrator!")
