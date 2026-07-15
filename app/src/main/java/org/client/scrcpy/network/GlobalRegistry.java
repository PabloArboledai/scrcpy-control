package org.client.scrcpy.network;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GlobalRegistry {
    private static final String TAG = "GlobalRegistry";
    private static final String API_URL = "https://omniverso-brain.loca.lt/api/mesh";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    public interface MeshCallback {
        void onDevicesFound(List<MeshDevice> devices);
    }
    
    public static class MeshDevice {
        public String id;
        public String name;
        public String ip;
        public String port;
        public String code;
        public String networkType;
    }

    public static void registerDevice(Context context, String ip, String port, String code, String networkType) {
        executor.execute(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("bypass-tunnel-reminder", "true");
                conn.setDoOutput(true);

                String deviceId = android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                String deviceName = Build.MANUFACTURER + " " + Build.MODEL;

                JSONObject body = new JSONObject();
                body.put("deviceId", deviceId);
                body.put("name", deviceName);
                body.put("ip", ip);
                body.put("port", port);
                body.put("code", code);
                body.put("network_type", networkType);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();
            } catch (Exception e) {
                Log.e(TAG, "Error registering on Mesh", e);
            }
        });
    }

    public static void getAvailableDevices(MeshCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("bypass-tunnel-reminder", "true");

                Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8");
                String response = scanner.useDelimiter("\\A").next();
                scanner.close();

                JSONObject resObj = new JSONObject(response);
                JSONArray array = resObj.getJSONArray("devices");
                List<MeshDevice> list = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    MeshDevice d = new MeshDevice();
                    d.id = obj.optString("deviceId");
                    d.name = obj.optString("name");
                    d.ip = obj.optString("ip");
                    d.port = obj.optString("port");
                    d.code = obj.optString("code");
                    d.networkType = obj.optString("network_type");
                    list.add(d);
                }
                callback.onDevicesFound(list);
            } catch (Exception e) {
                Log.e(TAG, "Error getting Mesh devices", e);
                callback.onDevicesFound(new ArrayList<>());
            }
        });
    }
}
