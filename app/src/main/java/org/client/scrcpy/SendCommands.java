package org.client.scrcpy;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.client.scrcpy.utils.AdbHelper;
import org.client.scrcpy.utils.ThreadUtils;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public class SendCommands {

    public final static int WAIT_TIME = 5000;

    public enum CmdStatus {
        SUCCESS,
        RUNNING,
        ERROR
    }

    public SendCommands() {
    }

    public boolean pairDevice(Context context, String ip, int port, String pairingCode) {
        Log.i("Scrcpy", "Intentando vincular dispositivo en " + ip + ":" + port + " con código " + pairingCode);
        String result = AdbHelper.adbCmd(context, "pair", ip + ":" + port, pairingCode);
        Log.i("Scrcpy", "Resultado de vinculación: " + result);
        return result != null && result.contains("Successfully paired");
    }

    public CmdStatus SendAdbCommands(Context context, final String ip, int port, int forwardport, String localip, int bitrate, int size) {
        return this.SendAdbCommands(context, null, ip, port, forwardport, localip, bitrate, size);
    }

    public CmdStatus SendAdbCommands(Context context, final byte[] fileBase64, final String ip, int port, int forwardport, String localip, int bitrate, int size) {
        AtomicReference<CmdStatus> status = new AtomicReference<>(CmdStatus.RUNNING);
        
        ThreadUtils.execute(() -> {
            try {
                boolean serverIsRunning = AdbHelper.checkAdbServer();
                Log.i("Scrcpy", "serverIsRunning: " + serverIsRunning);
                if (!serverIsRunning || !AdbHelper.isRunning()){
                    AdbHelper.restartAdb();
                    AdbHelper.waitForRunning(5);
                }
                CmdStatus curStatus = startPortForward(context, ip, port, forwardport);
                if (curStatus == CmdStatus.SUCCESS) {
                    status.set(CmdStatus.SUCCESS);
                } else {
                    status.set(CmdStatus.ERROR);
                }
            } catch (Exception e) {
                Log.e("Scrcpy", "Error in SendAdbCommands", e);
                status.set(CmdStatus.ERROR);
            }
        });
        
        return status.get();
    }

    private CmdStatus startPortForward(Context context, String ip, int port, int forwardport) {
        String result = AdbHelper.adbCmd(context, "-s", ip + ":" + port, "forward", "tcp:" + forwardport, "localabstract:scrcpy");
        if (result != null && !result.toLowerCase().contains("error")) {
            return CmdStatus.SUCCESS;
        }
        return CmdStatus.ERROR;
    }
}
