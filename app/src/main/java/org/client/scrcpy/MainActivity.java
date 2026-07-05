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
                    String qrData = "ADB_PAIR:" + host + ":42529:665439";
                    android.graphics.Bitmap bitmap = QRCodeUtil.generateQRCode(qrData, 500, 500);
                    ImageView qrImageView = findViewById(R.id.imageView_qr);
                    if (qrImageView != null) {
                        qrImageView.setImageBitmap(bitmap);
                        qrImageView.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, "QR Generado: Escanéalo con el otro móvil", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("ControlDroid", "QR Error", e);
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

    @Override
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
}
