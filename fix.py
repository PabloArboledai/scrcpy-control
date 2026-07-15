import re

with open('app/src/main/java/org/client/scrcpy/MainActivity.java', 'r', encoding='utf-8') as f:
    code = f.read()

# Fix registerForActivityResult by replacing it with IntentIntegrator
code = re.sub(
    r'private final androidx\.activity\.result\.ActivityResultLauncher<ScanOptions> barcodeLauncher = .*?\}\);',
    '// Removed ActivityResultLauncher, using IntentIntegrator',
    code, flags=re.DOTALL
)

# Fix missing scrcpy_main
code = re.sub(
    r'setupMeshDiscovery\(\);\s*checkAndLaunchTailscale\(\);\s*\}\s*EditText hostEdit',
    '''setupMeshDiscovery();
        checkAndLaunchTailscale();
    }

    @android.annotation.SuppressLint("SourceLockedOrientationActivity")
    public void scrcpy_main() {
        setContentView(R.layout.activity_main);
        sendCommands = new SendCommands();
        
        final android.widget.Button startButton = findViewById(R.id.button_start);
        if (startButton != null) {
            startButton.setOnClickListener(v -> {
                EditText hostEdit''',
    code, flags=re.DOTALL
)

# Add onActivityResult for IntentIntegrator
code = re.sub(
    r'protected void onDestroy\(\)',
    '''@Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        com.google.zxing.integration.android.IntentResult result = com.google.zxing.integration.android.IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                android.widget.Toast.makeText(this, "Escaneo cancelado", android.widget.Toast.LENGTH_LONG).show();
            } else {
                String qr = result.getContents();
                try {
                    if (qr.startsWith("ADB_PAIR:")) {
                        String[] parts = qr.split(":");
                        String ip = parts[1];
                        String port = parts[2];
                        String code_pairing = parts[3];
                        connectWithMesh(ip, port, code_pairing);
                    } else {
                        android.widget.Toast.makeText(this, "QR no reconocido", android.widget.Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, "Error leyendo QR", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy()''', code
)

# Change scanBtn click listener to use IntentIntegrator
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

print('MainActivity.java fixed!')
