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
    // ControlDroid Enhanced Logic
    private void logConnection(String message) {
        Log.d("ControlDroid", message);
    }

    private boolean isVpnConnection(String ip) {
        if (ip == null) return false;
        boolean isTailscale = ip.startsWith("100.");
        logConnection("Analizando IP: " + ip + " (Tailscale: " + isTailscale + ")");
        return isTailscale;
    }

    public final static String START_REMOTE = "start_remote_headless";

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
            if (first_time) {
                if (!Progress.isShowing()) {
                    Progress.showDialog(MainActivity.this, getString(R.string.please_wait));
                }
                scrcpy.start(surface, Scrcpy.LOCAL_IP + ":" + Scrcpy.LOCAL_FORWART_PORT,
                        screenHeight, screenWidth, delayControl);
                ThreadUtils.workPost(() -> {
                    boolean success = AdbHelper.executeWithTimeout(() -> {
                        while (!scrcpy.check_socket_connection()) {
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }, SendCommands.WAIT_TIME, TimeUnit.MILLISECONDS);
                    ThreadUtils.post(() -> {
                        Progress.closeDialog();
                        if (!success) {
                            if (serviceBound) {
                                showMainView();
                            }
                            Toast.makeText(context, "Connection Timed out", Toast.LENGTH_SHORT).show();
                        } else {
                            first_time = false;
                            set_display_nd_touch();
                            connectSuccessExt();
                        }
                    });
                });
            } else {
                scrcpy.setParms(surface, screenWidth, screenHeight);
                set_display_nd_touch();
                connectSuccessExt();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serviceBound = false;
        }
    };

    private void showMainView() {
        showMainView(false);
    }

    private void showMainView(boolean userDisconnect) {
        if (scrcpy != null) {
            scrcpy.StopService();
        }
        try {
            unbindService(serviceConnection);
        } catch (Exception e) {
            e.printStackTrace();
        }
        surface = null;
        surfaceView = null;
        serviceBound = false;
        scrcpy_main();
        scrcpy = null;
        connectExitExt(userDisconnect);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.context = this;
        if (savedInstanceState != null) {
            first_time = savedInstanceState.getBoolean("first_time");
            landscape = savedInstanceState.getBoolean("landscape");
            headlessMode = savedInstanceState.getBoolean("headlessMode");
            resumeScrcpy = savedInstanceState.getBoolean("resumeScrcpy");
            screenHeight = savedInstanceState.getInt("screenHeight");
            screenWidth = savedInstanceState.getInt("screenWidth");
        }
        landscape = getApplication().getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_PORTRAIT;
        if (first_time) {
            scrcpy_main();
        } else {
            start_screen_copy_magic();
        }
        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        Sensor proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);

        if (savedInstanceState == null || !savedInstanceState.getBoolean("from_save_instance", false)) {
            if (getIntent() != null && getIntent().getExtras() != null) {
                headlessMode = getIntent().getExtras().getBoolean(START_REMOTE, headlessMode);
            }
        }
        if (headlessMode && first_time) {
            getAttributes();
            connectScrcpyServer(PreUtils.get(this, Constant.CONTROL_REMOTE_ADDR, "100.91.47.35:34315"));
        }
        if (headlessMode) {
            View scrollView = findViewById(R.id.main_scroll_view);
            if (scrollView != null) {
                scrollView.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("from_save_instance", true);
        outState.putBoolean("first_time", first_time);
        outState.putBoolean("landscape", landscape);
        outState.putBoolean("headlessMode", headlessMode);
        outState.putInt("screenHeight", screenHeight);
        outState.putInt("screenWidth", screenWidth);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    public void scrcpy_main() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.getWindow().setStatusBarColor(getColor(R.color.status_bar));
        } else {
            this.getWindow().setStatusBarColor(getResources().getColor(R.color.status_bar));
        }
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        landscape = false;
        setContentView(R.layout.activity_main);
        
        sendCommands = new SendCommands();
        
        final Button startButton = findViewById(R.id.button_start);
        if (startButton != null) {
            startButton.setOnClickListener(v -> {
                getAttributes();
                connectScrcpyServer(serverAdr);
            });
        }

        // ControlDroid: Botón de vinculación QR
        final Button qrButton = findViewById(R.id.button_qr_pair);
        if (qrButton != null) {
            qrButton.setOnClickListener(v -> {
                Toast.makeText(MainActivity.this, "Generando QR...", Toast.LENGTH_SHORT).show();
                try {
                    EditText hostEdit = findViewById(R.id.editText_server_host);
                    String host = (hostEdit != null) ? hostEdit.getText().toString() : "100.91.47.35";
                    String qrData = "ADB_PAIR:" + host + ":46859:665439";
                    android.graphics.Bitmap bitmap = org.client.scrcpy.utils.QRCodeUtil.generateQRCode(qrData, 500, 500);
                    ImageView qrImageView = findViewById(R.id.imageView_qr);
                    if (qrImageView != null) {
                        qrImageView.setImageBitmap(bitmap);
                        qrImageView.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e("ControlDroid", "QR Error", e);
                }
            });
        }

        get_saved_preferences();
        EditText editText = findViewById(R.id.editText_server_host);
        View historyList = findViewById(R.id.history_list);
        if (historyList != null && editText != null) {
            historyList.setOnClickListener(v -> {
                editText.clearFocus();
                showListPopulWindow(editText);
            });
        }
        
        if (headlessMode) {
            View scrollView = findViewById(R.id.main_scroll_view);
            if (scrollView != null) {
                scrollView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void showListPopulWindow(EditText mEditText) {
        String[] list = getHistoryList();
        if (list.length == 0) {
            list = new String[]{"127.0.0.1"};
        }
        final ListPopupWindow listPopupWindow = new ListPopupWindow(this);
        listPopupWindow.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list));
        listPopupWindow.setAnchorView(mEditText);
        listPopupWindow.setModal(true);
        listPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        String[] finalList = list;
        listPopupWindow.setOnItemClickListener((adapterView, view, i, l) -> {
            mEditText.setText(finalList[i]);
            listPopupWindow.dismiss();
        });
        listPopupWindow.show();
    }

    public void get_saved_preferences() {
        final EditText editTextServerHost = findViewById(R.id.editText_server_host);
        final Switch aSwitch0 = findViewById(R.id.switch0);
        final Switch aSwitch1 = findViewById(R.id.switch1);
        String historySpServerAdr = PreUtils.get(context, Constant.CONTROL_REMOTE_ADDR, "100.91.47.35:34315");
        if (TextUtils.isEmpty(historySpServerAdr)) {
            String[] historyList = getHistoryList();
            if (historyList.length > 0) {
                editTextServerHost.setText(historyList[0]);
            }
        } else {
            editTextServerHost.setText(historySpServerAdr);
        }
        aSwitch0.setChecked(PreUtils.get(context, Constant.CONTROL_NO, false));
        aSwitch1.setChecked(PreUtils.get(context, Constant.CONTROL_NAV, false));
        setSpinner(R.array.options_resolution_values, R.id.spinner_video_resolution, Constant.PREFERENCE_SPINNER_RESOLUTION);
        setSpinner(R.array.options_bitrate_keys, R.id.spinner_video_bitrate, Constant.PREFERENCE_SPINNER_BITRATE);
        setSpinner(R.array.options_delay_keys, R.id.delay_control_spinner, Constant.PREFERENCE_SPINNER_DELAY);
        if (aSwitch0.isChecked()) {
            aSwitch1.setClickable(false);
            aSwitch1.setTextColor(Color.GRAY);
        }
        aSwitch0.setOnClickListener(v -> {
            if (aSwitch0.isChecked()) {
                aSwitch1.setClickable(false);
                aSwitch1.setTextColor(Color.GRAY);
            } else {
                aSwitch1.setClickable(true);
                aSwitch1.setTextColor(Color.WHITE);
            }
            PreUtils.put(context, Constant.CONTROL_NO, aSwitch0.isChecked());
        });
        aSwitch1.setOnClickListener(v -> PreUtils.put(context, Constant.CONTROL_NAV, aSwitch1.isChecked()));
    }

    private void setSpinner(int arrayId, int spinnerId, String key) {
        Spinner spinner = findViewById(spinnerId);
        String value = PreUtils.get(context, key, "");
        if (!TextUtils.isEmpty(value)) {
            String[] values = getResources().getStringArray(arrayId);
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String[] values = getResources().getStringArray(arrayId);
                PreUtils.put(context, key, values[i]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
    }

    private void getAttributes() {
        EditText editText = findViewById(R.id.editText_server_host);
        serverAdr = editText.getText().toString();
        Spinner spinnerResolution = findViewById(R.id.spinner_video_resolution);
        String resolution = spinnerResolution.getSelectedItem().toString();
        String[] res = resolution.split("x");
        screenWidth = Integer.parseInt(res[0]);
        screenHeight = Integer.parseInt(res[1]);
        Spinner spinnerBitrate = findViewById(R.id.spinner_video_bitrate);
        String bitrateStr = spinnerBitrate.getSelectedItem().toString().replace(" Mbps", "");
        videoBitrate = Integer.parseInt(bitrateStr) * 1000 * 1000;
        Spinner spinnerDelay = findViewById(R.id.delay_control_spinner);
        String delayStr = spinnerDelay.getSelectedItem().toString().replaceAll("[^0-9]", "");
        delayControl = Integer.parseInt(delayStr);
    }

    private String[] getHistoryList() {
        String historyList = PreUtils.get(context, Constant.HISTORY_LIST_KEY, "");
        if (TextUtils.isEmpty(historyList)) return new String[]{};
        try {
            JSONArray historyJson = new JSONArray(historyList);
            String[] retList = new String[historyJson.length()];
            for (int i = 0; i < historyJson.length(); i++) {
                retList[i] = historyJson.get(i).toString();
            }
            return retList;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String[]{};
    }

    private boolean saveHistory(String device) {
        if (headlessMode) return false;
        JSONArray historyJson = new JSONArray();
        String[] historyList = getHistoryList();
        if (historyList.length == 0) {
            historyJson.put(device);
        } else {
            try {
                historyJson.put(0, device);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            int count = Math.min(historyList.length, 30);
            for (int i = 0; i < count; i++) {
                if (!historyList[i].equals(device)) {
                    historyJson.put(historyList[i]);
                }
            }
        }
        try {
            return PreUtils.put(context, Constant.HISTORY_LIST_KEY, historyJson.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void swapDimensions() {
        int temp = screenHeight;
        screenHeight = screenWidth;
        screenWidth = temp;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void start_screen_copy_magic() {
        setContentView(R.layout.surface);
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        surfaceView = findViewById(R.id.decoder_surface);
        surface = surfaceView.getHolder().getSurface();
        final LinearLayout nav_bar = findViewById(R.id.nav_button_bar);
        if (PreUtils.get(context, Constant.CONTROL_NAV, false) &&
                !PreUtils.get(context, Constant.CONTROL_NO, false)) {
            nav_bar.setVisibility(LinearLayout.VISIBLE);
        } else {
            nav_bar.setVisibility(LinearLayout.GONE);
        }
        linearLayout = findViewById(R.id.container1);
        start_Scrcpy_service();
    }

    private void start_Scrcpy_service() {
        Intent intent = new Intent(this, Scrcpy.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void loadNewRotation() {
        if (first_time) first_time = false;
        try {
            unbindService(serviceConnection);
        } catch (Exception e) {
            e.printStackTrace();
        }
        serviceBound = false;
        result_of_Rotation = true;
        landscape = !landscape;
        swapDimensions();
        if (landscape) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    @Override
    public void errorDisconnect() {
        Dialog.displayDialog(this, getString(R.string.disconnect),
                getString(R.string.disconnect_ask), () -> {
                    if (serviceBound) {
                        showMainView();
                        first_time = true;
                    } else {
                        MainActivity.this.finish();
                    }
                }, false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (resumeScrcpy) {
            showMainView(true);
            first_time = true;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (resumeScrcpy) {
            if (!serviceBound) {
                resumeScrcpy = false;
                connectScrcpyServer(PreUtils.get(context, Constant.CONTROL_REMOTE_ADDR, "100.91.47.35:34315"));
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (serviceBound && scrcpy != null) {
            scrcpy.pause();
            resumeScrcpy = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!first_time && !result_of_Rotation) {
            final View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            if (serviceBound) {
                linearLayout = findViewById(R.id.container1);
                scrcpy.resume();
            }
        }
        if (resumeScrcpy && !result_of_Rotation && scrcpy != null) {
            scrcpy.resume();
        }
        resumeScrcpy = false;
        result_of_Rotation = false;
    }

    @Override
    public void onBackPressed() {
        if (timestamp == 0) {
            if (serviceBound) {
                timestamp = SystemClock.uptimeMillis();
                Toast.makeText(context, "Press again to exit", Toast.LENGTH_SHORT).show();
            } else {
                finish();
            }
        } else {
            long now = SystemClock.uptimeMillis();
            if (now < timestamp + 1000) {
                timestamp = 0;
                if (serviceBound) {
                    showMainView(true);
                    first_time = true;
                } else {
                    finish();
                }
            }
            timestamp = 0;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {}

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}

    private void connectScrcpyServer(String serverAdr) {
        if (!TextUtils.isEmpty(serverAdr)) {
            saveHistory(serverAdr);
            String[] serverInfo = Util.getServerHostAndPort(serverAdr);
            String serverHost = serverInfo[0];
            int serverPort = Integer.parseInt(serverInfo[1]);
            int localForwardPort = Scrcpy.LOCAL_FORWART_PORT;

            Progress.showDialog(MainActivity.this, getString(R.string.please_wait));
            ThreadUtils.workPost(() -> {
                AdbHelper.writeAssetsJarServer(App.mContext);
                
                // ControlDroid: Vinculación automática con datos finales (665439:46859)
                if (serverHost.equals("100.91.47.35")) {
                    logConnection("Vinculando con Honor en puerto 46859...");
                    sendCommands.pairDevice(context, "100.91.47.35", 46859, "665439");
                }

                SendCommands.CmdStatus sendStatus = sendCommands.SendAdbCommands(context, serverHost,
                        serverPort,
                        localForwardPort,
                        Scrcpy.LOCAL_IP,
                        videoBitrate, Math.max(screenHeight, screenWidth));
                if (sendStatus == SendCommands.CmdStatus.SUCCESS) {
                    ThreadUtils.post(() -> {
                        if (!MainActivity.this.isFinishing()) {
                            start_screen_copy_magic();
                        }
                    });
                } else {
                    ThreadUtils.post(Progress::closeDialog);
                    Toast.makeText(context, "Network OR ADB connection failed", Toast.LENGTH_SHORT).show();
                    connectExitExt();
                }
            });
        } else {
            Toast.makeText(context, "Server Address Empty", Toast.LENGTH_SHORT).show();
            connectExitExt();
        }
    }

    protected void connectSuccessExt() {
        Progress.closeDialog();
    }

    protected void connectExitExt() {
        this.connectExitExt(false);
    }

    protected void connectExitExt(boolean userDisconnect) {
        if (headlessMode && !resumeScrcpy && !result_of_Rotation) {
            finishAndRemoveTask();
        }
    }

    private void set_display_nd_touch() {
        // Implementación de la visualización y control
    }
}
