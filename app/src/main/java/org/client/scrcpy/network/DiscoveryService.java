package org.client.scrcpy.network;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.net.InetAddress;
import java.util.Map;
import java.util.HashMap;

public class DiscoveryService {
    private static final String TAG = "DiscoveryService";
    private static final String REGISTRY_URL = "http://3.136.155.210:8080/registry.jsp";
    
    private Context context;
    private GlobalRegistry.MeshCallback updateCallback;
    private boolean isDiscovering = false;
    private Thread discoveryThread;
    
    private String myId;
    private String myName;
    private int myPort;
    private String myCode;
    
    public DiscoveryService(Context context) {
        this.context = context;
    }

    public void setCallback(GlobalRegistry.MeshCallback callback) {
        this.updateCallback = callback;
    }

    private String getTailscaleOrLocalIp() {
        try {
            String fallbackIp = null;
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().indexOf(':') < 0) {
                        String ip = inetAddress.getHostAddress();
                        if (ip.startsWith("100.")) return ip; // Tailscale IP has priority
                        fallbackIp = ip;
                    }
                }
            }
            return fallbackIp != null ? fallbackIp : "127.0.0.1";
        } catch (Exception ex) { return "127.0.0.1"; }
    }

    public void registerService(String name, int port, String code, String ip) {
        this.myId = "device_" + System.currentTimeMillis();
        this.myName = name;
        this.myPort = port;
        this.myCode = code;
    }

    public void discoverServices() {
        if (isDiscovering) return;
        isDiscovering = true;
        
        discoveryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isDiscovering) {
                    try {
                        String myIp = getTailscaleOrLocalIp();
                        // 1. Register myself
                        if (myId != null) {
                            String regUrl = REGISTRY_URL + "?action=register&id=" + myId + "&name=" + myName + "&ip=" + myIp + "&port=" + myPort + "&code=" + myCode;
                            URL url = new URL(regUrl.replace(" ", "%20"));
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            conn.getResponseCode();
                            conn.disconnect();
                        }
                        
                        // 2. Fetch others
                        URL listUrl = new URL(REGISTRY_URL + "?action=list");
                        HttpURLConnection conn = (HttpURLConnection) listUrl.openConnection();
                        conn.setRequestMethod("GET");
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder result = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) result.append(line);
                        reader.close();
                        conn.disconnect();
                        
                        JSONArray arr = new JSONArray(result.toString());
                        List<GlobalRegistry.MeshDevice> devices = new ArrayList<>();
                        for (int i=0; i<arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            if (obj.getString("id").equals(myId)) continue;
                            
                            GlobalRegistry.MeshDevice d = new GlobalRegistry.MeshDevice();
                            d.id = obj.getString("id");
                            d.name = obj.getString("name");
                            d.ip = obj.getString("ip");
                            d.port = obj.getString("port");
                            d.code = obj.getString("code");
                            d.networkType = d.ip.startsWith("100.") ? "Tailscale Mesh" : "WiFi Local";
                            devices.add(d);
                        }
                        if (updateCallback != null) updateCallback.onDevicesFound(devices);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error in Mesh discovery", e);
                    }
                    try { Thread.sleep(5000); } catch (Exception e) {}
                }
            }
        });
        discoveryThread.start();
    }

    public void stopDiscovery() {
        isDiscovering = false;
        if (discoveryThread != null) {
            discoveryThread.interrupt();
        }
    }
}
