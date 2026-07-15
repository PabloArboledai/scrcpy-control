package org.client.scrcpy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.widget.ListView;
import android.widget.ArrayAdapter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import org.client.scrcpy.network.DiscoveryService;
import org.client.scrcpy.network.GlobalRegistry;
import java.util.List;
import java.util.ArrayList;

import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import org.client.scrcpy.utils.AdbHelper;
import org.client.scrcpy.utils.QRCodeUtil;
import org.client.scrcpy.utils.HttpRequest;
import org.client.scrcpy.utils.PreUtils;
import org.client.scrcpy.utils.Progress;
import org.client.scrcpy.utils.ThreadUtils;
import org.client.scrcpy.utils.Util;
import org.client.scrcpy.Constant;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements Scrcpy.ServiceCallbacks, SensorEventListener {

    private DiscoveryService discoveryService;
    private ListView listViewDevices;
    private ArrayAdapter<String> devicesAdapter;
    private List<GlobalRegistry.MeshDevice> discoveredDevices = new ArrayList<>();
    
    // Removed ActivityResultLauncher, using IntentIntegrator

    
    private boolean headlessMode = false;
    private int screenWidth;
    private int screenHeight;
    private boolean landscape = false;
    private boolean first_time = true;
    private boolean result_of_Rotation = false;
    private boolean serviceBound = false;
    private boolean resumeScrcpy = false;
    SensorManager sensorManager;
    private SendCommands sendCommands;
    private int videoBitrate;
    private int delayControl;
    private Context context;
    private String serverAdr = null;
    private SurfaceView surfaceView;
    private Surface surface;
    private Scrcpy scrcpy;
    private long timestamp = 0;
    private LinearLayout linearLayout;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            scrcpy = ((Scrcpy.MyServiceBinder) iBinder).getService();
            scrcpy.setServiceCallbacks(MainActivity.this);
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        scrcpy_main();

        // Mesh Networking Init
        listViewDevices = findViewById(R.id.listView_devices);
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        if (listViewDevices != null) {
            listViewDevices.setAdapter(devicesAdapter);
            listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
                if (position >= 0 && position < discoveredDevices.size()) {
                    GlobalRegistry.MeshDevice d = discoveredDevices.get(position);
                    connectWithMesh(d.ip, d.port, d.code);
                }
            });
        }
        
        Button scanBtn = findViewById(R.id.button_scan_qr);
        if (scanBtn != null) {
            scanBtn.setOnClickListener(v -> {
                com.google.zxing.integration.android.IntentIntegrator integrator = new com.google.zxing.integration.android.IntentIntegrator(MainActivity.this);
                integrator.setPrompt("Escanea el QR de ControlDroid");
                integrator.setOrientationLocked(false);
                integrator.initiateScan();
            });
        }
        
        setupMeshDiscovery();
                EditText hostEdit = findViewById(R.id.editText_server_host);
                serverAdr = (hostEdit != null) ? hostEdit.getText().toString() : "100.91.47.35:42529";
                connectScrcpyServer(serverAdr);
            });
        }

        final Button qrButton = findViewById(R.id.button_qr_pair);
        if (qrButton != null) {
            qrButton.setOnClickListener(v -> showMultiNetworkQRDialog());
        }
    }

    private void connectScrcpyServer(String addr) {
        Progress.showDialog(this, "Conectando a " + addr);
        // Lógica de conexión simplificada
    }

    protected void connectSuccessExt() {
        Progress.closeDialog();
    }

    protected void connectExitExt() {
        finishAndRemoveTask();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {}

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // onScrcpyExit is a legacy method — not in ServiceCallbacks interface
    public void onScrcpyExit() {
        connectExitExt();
    }
    
    private void set_display_nd_touch() {
        if (surfaceView != null) {
            surfaceView.setOnTouchListener(this::onTouch);
        }
    }

    private boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    @Override
    public void errorDisconnect() {
        // Handle disconnection error — show toast and return to connect screen
        runOnUiThread(() -> {
            android.widget.Toast.makeText(this, "Disconnected from device", android.widget.Toast.LENGTH_SHORT).show();
            connectExitExt();
        });
    }

    @Override
    public void loadNewRotation() {
        // Handle rotation change from service
        runOnUiThread(this::set_display_nd_touch);
    }


    /**
     * Detects all available IPs (WiFi, Tailscale/VPN) and shows a dialog
     * to generate QR for pairing via any network type.
     */
    private void showMultiNetworkQRDialog() {
        java.util.List<String[]> networks = detectNetworkInterfaces();

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Generar QR de Emparejamiento ADB");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        android.widget.TextView ipLabel = new android.widget.TextView(this);
        ipLabel.setText("Red / IP detectada:");
        ipLabel.setTextSize(14);
        layout.addView(ipLabel);

        android.widget.Spinner ipSpinner = new android.widget.Spinner(this);
        java.util.List<String> ipOptions = new java.util.ArrayList<>();
        for (String[] net : networks) {
            ipOptions.add(net[0] + " — " + net[1]);
        }
        ipOptions.add("IP manual (datos móviles / VPN pública)");
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ipOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ipSpinner.setAdapter(adapter);
        layout.addView(ipSpinner);

        android.widget.EditText manualIpEdit = new android.widget.EditText(this);
        manualIpEdit.setHint("Ingresa IP (ej: 200.x.x.x o IP de VPN)");
        manualIpEdit.setVisibility(android.view.View.GONE);
        layout.addView(manualIpEdit);

        ipSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v2, int pos, long id) {
                manualIpEdit.setVisibility(pos == ipOptions.size() - 1 ? android.view.View.VISIBLE : android.view.View.GONE);
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        android.widget.TextView portLabel = new android.widget.TextView(this);
        portLabel.setText("Puerto ADB Pairing:");
        portLabel.setTextSize(14);
        portLabel.setPadding(0, 16, 0, 0);
        layout.addView(portLabel);

        android.widget.EditText portEdit = new android.widget.EditText(this);
        portEdit.setHint("Ver: Ajustes → Desarrollador → Emparejamiento inalámbrico");
        portEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(portEdit);

        android.widget.TextView codeLabel = new android.widget.TextView(this);
        codeLabel.setText("Código de emparejamiento (6 dígitos):");
        codeLabel.setTextSize(14);
        codeLabel.setPadding(0, 16, 0, 0);
        layout.addView(codeLabel);

        android.widget.EditText codeEdit = new android.widget.EditText(this);
        codeEdit.setHint("Código que aparece en el dispositivo destino");
        codeEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(codeEdit);

        android.widget.TextView infoText = new android.widget.TextView(this);
        infoText.setText("ℹ️ Ajustes > Opciones de desarrollador > Emparejamiento inalámbrico por código QR. Para Tailscale/VPN ambos deben estar en la misma red virtual.");
        infoText.setTextSize(10);
        infoText.setPadding(0, 16, 0, 0);
        layout.addView(infoText);

        builder.setView(layout);
        builder.setPositiveButton("Generar QR", (dialog, which) -> {
            String ip;
            int sel = ipSpinner.getSelectedItemPosition();
            if (sel == ipOptions.size() - 1) {
                ip = manualIpEdit.getText().toString().trim();
            } else {
                ip = networks.get(sel)[1];
            }
            String port = portEdit.getText().toString().trim();
            String code = codeEdit.getText().toString().trim();
            if (ip.isEmpty() || port.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }
            generateAndShowQR(ip, port, code);
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    /**
     * Detects all IPv4 interfaces: WiFi (192.168/10/172.16), Tailscale (100.x), VPN, etc.
     */
    private java.util.List<String[]> detectNetworkInterfaces() {
        java.util.List<String[]> results = new java.util.ArrayList<>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        String label;
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.16.")) {
                            label = "WiFi/LAN";
                        } else if (ip.startsWith("100.")) {
                            label = "Tailscale";
                        } else {
                            label = "VPN/Otro";
                        }
                        results.add(new String[]{label, ip});
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ControlDroid", "Error detecting network interfaces", e);
        }
        // Always add Tailscale default if none detected
        if (results.stream().noneMatch(r -> r[1].startsWith("100."))) {
            results.add(new String[]{"Tailscale (predeterminado)", "100.91.47.35"});
        }
        return results;
    }

    /**
     * Generates and shows QR for ADB Wireless pairing.
     * Format: ADB_PAIR:IP:PORT:CODE
     */
    private void generateAndShowQR(String ip, String port, String code) {
        try {
            String qrData = "ADB_PAIR:" + ip + ":" + port + ":" + code;
            if(discoveryService != null) discoveryService.registerService(android.os.Build.MODEL, Integer.parseInt(port.isEmpty()?"0":port), code, ip);
            GlobalRegistry.registerDevice(this, ip, port, code, ip.startsWith("100.") ? "Tailscale" : "WiFi");
            Log.d("ControlDroid", "Generating QR: " + qrData);
            android.graphics.Bitmap bitmap = QRCodeUtil.generateQRCode(qrData, 600, 600);
            ImageView qrImageView = findViewById(R.id.imageView_qr);
            if (qrImageView != null) {
                qrImageView.setImageBitmap(bitmap);
                qrImageView.setVisibility(android.view.View.VISIBLE);
                qrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Toast.makeText(this, "✅ QR listo — Escanea desde el dispositivo a controlar (" + ip + ")", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e("ControlDroid", "QR Error", e);
            Toast.makeText(this, "Error al generar QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void setupMeshDiscovery() {
        discoveryService = new DiscoveryService(this);
        GlobalRegistry.MeshCallback callback = devices -> runOnUiThread(() -> {
            discoveredDevices.clear();
            discoveredDevices.addAll(devices);
            List<String> names = new ArrayList<>();
            for (GlobalRegistry.MeshDevice d : devices) {
                names.add("📱 " + d.name + " (" + d.networkType + ") - " + d.ip);
            }
            if (names.isEmpty()) {
                names.add("Buscando dispositivos cercanos...");
            }
            devicesAdapter.clear();
            devicesAdapter.addAll(names);
            devicesAdapter.notifyDataSetChanged();
        });
        
        discoveryService.setCallback(callback);
        discoveryService.discoverServices();
        
        // Empezar polling global
        new Thread(() -> {
            while (!isDestroyed()) {
                GlobalRegistry.getAvailableDevices(callback);
                try { Thread.sleep(10000); } catch (Exception e) {}
            }
        }).start();
    }
    
    private void connectWithMesh(String ip, String port, String code) {
        EditText hostEdit = findViewById(R.id.editText_server_host);
        if (hostEdit != null) {
            hostEdit.setText(ip + ":" + port);
        }
        // Save code globally if needed, then connect
        Toast.makeText(this, "Conectando a " + ip + " usando código...", Toast.LENGTH_SHORT).show();
        connectScrcpyServer(ip + ":" + port);
        // Note: Real adb pairing requires running adb pair ip:port code under the hood.
        // That logic must be triggered in connectScrcpyServer or before it.
        // For now we set it to start the connection flow.
    }
    
    private void checkAndLaunchTailscale() {
        String tailscalePackage = "com.tailscale.ipn";
        boolean isInstalled = false;
        try {
            getPackageManager().getPackageInfo(tailscalePackage, 0);
            isInstalled = true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            isInstalled = false;
        }

        if (isInstalled) {
            // Check if we already have a Tailscale IP (100.x.x.x)
            java.util.List<String[]> networks = detectNetworkInterfaces();
            boolean hasTailscaleIp = false;
            for (String[] net : networks) {
                if (net[1].startsWith("100.")) {
                    hasTailscaleIp = true;
                    break;
                }
            }

            if (!hasTailscaleIp) {
                // Not connected to Tailscale yet, prompt user to connect
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                builder.setTitle("Conexión Remota (Tailscale)");
                builder.setMessage("Parece que Tailscale está instalado pero no está conectado. Para poder usar ControlDroid de forma remota, necesitas iniciar Tailscale.");
                builder.setPositiveButton("Abrir Tailscale", (dialog, which) -> {
                    // Copiar Auth Key al portapapeles por si la necesitan
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("AuthKey", "tskey-auth-XYZ123-YOUR_KEY_HERE");
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Llave copiada al portapapeles (por si la necesitas)", Toast.LENGTH_LONG).show();
                    
                    Intent intent = getPackageManager().getLaunchIntentForPackage(tailscalePackage);
                    if (intent != null) {
                        startActivity(intent);
                    }
                });
                builder.setNegativeButton("Omitir (Uso Local)", null);
                builder.show();
            }
        } else {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Tailscale No Instalado");
            builder.setMessage("Para conectarte a otros dispositivos fuera de tu red WiFi, necesitas instalar Tailscale.");
            builder.setPositiveButton("Descargar", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("market://details?id=" + tailscalePackage));
                startActivity(intent);
            });
            builder.setNegativeButton("Omitir", null);
            builder.show();
        }
    }

    @Override
    @Override
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
    protected void onDestroy() {
        super.onDestroy();
        if (discoveryService != null) discoveryService.stopDiscovery();
    }

}
