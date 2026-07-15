package org.client.scrcpy.network;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscoveryService {
    private static final String TAG = "DiscoveryService";
    private static final String SERVICE_TYPE = "_controldroid._tcp.";
    
    private Context context;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private String serviceName;
    
    private Map<String, GlobalRegistry.MeshDevice> localDevices = new HashMap<>();
    private GlobalRegistry.MeshCallback updateCallback;

    public DiscoveryService(Context context) {
        this.context = context;
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    public void setCallback(GlobalRegistry.MeshCallback callback) {
        this.updateCallback = callback;
    }

    public void registerService(String name, int port, String code, String ip) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("ControlDroid_" + name);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);
        
        initializeRegistrationListener();
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        } catch (Exception e) {
            Log.e(TAG, "Error registering local service", e);
        }
    }

    public void discoverServices() {
        initializeDiscoveryListener();
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            Log.e(TAG, "Error discovering services", e);
        }
    }

    public void stopDiscovery() {
        if (nsdManager != null) {
            try {
                if (discoveryListener != null) nsdManager.stopServiceDiscovery(discoveryListener);
                if (registrationListener != null) nsdManager.unregisterService(registrationListener);
            } catch (Exception e) { }
        }
    }

    private void initializeRegistrationListener() {
        registrationListener = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) { serviceName = NsdServiceInfo.getServiceName(); }
            @Override public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { }
            @Override public void onServiceUnregistered(NsdServiceInfo arg0) { }
            @Override public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { }
        };
    }

    private void initializeDiscoveryListener() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String regType) { }
            @Override public void onServiceFound(NsdServiceInfo service) {
                if (!service.getServiceType().equals(SERVICE_TYPE)) return;
                nsdManager.resolveService(service, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) { }
                    @Override public void onServiceResolved(NsdServiceInfo serviceInfo) {
                        if (serviceInfo.getServiceName().equals(serviceName)) return;
                        GlobalRegistry.MeshDevice d = new GlobalRegistry.MeshDevice();
                        d.id = serviceInfo.getServiceName();
                        d.name = serviceInfo.getServiceName().replace("ControlDroid_", "");
                        d.ip = serviceInfo.getHost().getHostAddress();
                        d.port = String.valueOf(serviceInfo.getPort());
                        d.networkType = "WiFi Local";
                        localDevices.put(d.id, d);
                        if (updateCallback != null) updateCallback.onDevicesFound(new ArrayList<>(localDevices.values()));
                    }
                });
            }
            @Override public void onServiceLost(NsdServiceInfo service) {
                localDevices.remove(service.getServiceName());
                if (updateCallback != null) updateCallback.onDevicesFound(new ArrayList<>(localDevices.values()));
            }
            @Override public void onDiscoveryStopped(String serviceType) { }
            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) { nsdManager.stopServiceDiscovery(this); }
            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) { nsdManager.stopServiceDiscovery(this); }
        };
    }
}
