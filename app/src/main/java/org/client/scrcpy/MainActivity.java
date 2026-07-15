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
    
    // ActivityResultLauncher eliminated, using IntentIntegrator directly.

    
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

    }

    @SuppressLint("SourceLockedOrientationActivity")
    public void scrcpy_main() {
        setContentView(R.layout.activity_main);
        sendCommands = new SendCommands();
        
        final Button startButton = findViewById(R.id.button_start);
        if (startButton != null) {
            startButton.setOnClickListener(v -> {
                EditText hostEdit = findViewById(R.id.editText_server_host);
                serverAdr = (hostEdit != null) ? hostEdit.getText().toString() : "100.91.47.35:42529";
                connectScrcpyServer(serverAdr);
            });
        }

        final Button qrButton = findViewById(R.id.button_qr_pair);
        if (qrButton != null) {
            qrButton.setOnClickListener(v -> {
                try {
                    EditText hostEdit = findViewById(R.id.editText_server_host);
                    String host = (hostEdit != null) ? hostEdit.getText().toString() : "100.91.47.35";
                    
                    // Obtener puerto y código dinámicamente si existen campos, sino usar por defecto
                    String port = "42529";
                    String code = "665439";
                    
                    // Formato oficial de Android para Wireless Debugging Pairing
                    // WIFI:S:<SSID>;P:<PASSWORD>;T:<AUTH_TYPE>;;
                    // Sin embargo, para ADB Pairing el formato que suele funcionar es:
                    // ADB_PAIR:IP:PORT:CODE
                    String qrData = "ADB_PAIR:" + host + ":" + port + ":" + code;
                    
                    Log.d("ControlDroid", "Generando QR para: " + qrData);
                    
                    android.graphics.Bitmap bitmap = QRCodeUtil.generateQRCode(qrData, 500, 500);
                    ImageView qrImageView = findViewById(R.id.imageView_qr);
                    if (qrImageView != null) {
                        qrImageView.setImageBitmap(bitmap);
                        qrImageView.setVisibility(View.VISIBLE);
                        
                        // Asegurarse de que el ImageView sea lo suficientemente grande y visible
                        qrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        
                        Toast.makeText(MainActivity.this, "QR de Vinculación Generado para " + host, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("ControlDroid", "QR Error", e);
                    Toast.makeText(MainActivity.this, "Error al generar QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
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
    
    @Override
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discoveryService != null) discoveryService.stopDiscovery();
    }

}
