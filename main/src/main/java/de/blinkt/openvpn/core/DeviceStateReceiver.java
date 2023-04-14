/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Handler;

import java.util.LinkedList;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.core.VpnStatus.ByteCountListener;

public class DeviceStateReceiver extends BroadcastReceiver implements ByteCountListener, OpenVPNManagement.PausedStateCallback {

    private final Handler mDisconnectHandler;
    private int lastNetwork = -1;
    private OpenVPNManagement mManagement;

    // Window time in s
    private final int TRAFFIC_WINDOW = 60;
    // Data traffic limit in bytes
    private final long TRAFFIC_LIMIT = 64 * 1024;

    // Time to wait after network disconnect to pause the VPN
    private final int DISCONNECT_WAIT = 20;

    ConnectState network = ConnectState.DISCONNECTED;
    ConnectState screen = ConnectState.SHOULDBECONNECTED;
    ConnectState userpause = ConnectState.SHOULDBECONNECTED;

    private String lastStateMsg = null;
    private Runnable mDelayDisconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!(network == ConnectState.PENDINGDISCONNECT))
                return;

            network = ConnectState.DISCONNECTED;

            // Set screen state to be disconnected if disconnect pending
            if (screen == ConnectState.PENDINGDISCONNECT)
                screen = ConnectState.DISCONNECTED;

            mManagement.pauseVPN(getPauseReason());
        }
    };

    private NetworkInfo lastConnectedNetwork;

    @Override
    public boolean shouldBeRunning() {
        return shouldBeConnected();
    }

    private enum ConnectState {
        SHOULDBECONNECTED,
        PENDINGDISCONNECT,
        DISCONNECTED
    }

    private static class Datapoint {
        private Datapoint(long t, long d) {
            timestamp = t;
            data = d;
        }

        long timestamp;
        long data;
    }

    private LinkedList<Datapoint> trafficdata = new LinkedList<>();

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (screen != ConnectState.PENDINGDISCONNECT)
            return;

        long total = diffIn + diffOut;
        trafficdata.add(new Datapoint(System.currentTimeMillis(), total));

        while (trafficdata.getFirst().timestamp <= (System.currentTimeMillis() - TRAFFIC_WINDOW * 1000)) {
            trafficdata.removeFirst();
        }

        long windowtraffic = 0;
        for (Datapoint dp : trafficdata)
            windowtraffic += dp.data;

        if (windowtraffic < TRAFFIC_LIMIT) {
            VpnStatus.logInfo(R.string.screenoff_pause, "64 kB", TRAFFIC_WINDOW);
            screen = ConnectState.DISCONNECTED;
            mManagement.pauseVPN(getPauseReason());
        }
    }

    public void userPause(boolean pause) {
        if (pause) {
            userpause = ConnectState.DISCONNECTED;
            // Check if we should disconnect
            mManagement.pauseVPN(getPauseReason());
        } else {
            boolean wereConnected = shouldBeConnected();
            userpause = ConnectState.SHOULDBECONNECTED;
            if (shouldBeConnected() && !wereConnected)
                mManagement.resumeVPN();
            else
                // Update the reason why we currently paused
                mManagement.pauseVPN(getPauseReason());
        }
    }

    public DeviceStateReceiver(OpenVPNManagement magnagement) {
        super();
        mManagement = magnagement;
        mManagement.setPauseCallback(this);
        mDisconnectHandler = new Handler();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            networkStateChange(context);
        } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);
            boolean screenOffPause = prefs.getBoolean("screenoff", false);

            if (screenOffPause) {
                ProfileManager profileManager = ProfileManager.getInstance(context);
                VpnProfile connectedProfile = profileManager.getConnectedVpnProfile();

                if (connectedProfile != null && !connectedProfile.isPersistTun())
                    VpnStatus.logError(R.string.screen_nopersistenttun);

                screen = ConnectState.PENDINGDISCONNECT;
                fillTrafficData();
                if (network == ConnectState.DISCONNECTED || userpause == ConnectState.DISCONNECTED)
                    screen = ConnectState.DISCONNECTED;
            }
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            // Network was disabled because screen off
            boolean connected = shouldBeConnected();
            screen = ConnectState.SHOULDBECONNECTED;

            /* We should connect now, cancel any outstanding disconnect timer */
            mDisconnectHandler.removeCallbacks(mDelayDisconnectRunnable);
            /* should be connected has changed because the screen is on now, connect the VPN */
            if (shouldBeConnected() != connected) {
                mManagement.resumeVPN();
            } else if (!shouldBeConnected()) {
                /*Update the reason why we are still paused */
                mManagement.pauseVPN(getPauseReason());
            }
        }
    }

    private void fillTrafficData() {
        trafficdata.add(new Datapoint(System.currentTimeMillis(), TRAFFIC_LIMIT));
    }

    public static boolean equalsObj(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    public void networkStateChange(Context context) {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);
        boolean sendusr1 = prefs.getBoolean("netchange_reconnect", true);
        NetworkInfo networkInfo = getCurrentNetworkInfo(context);
        String netstatestring;

        if (networkInfo == null) {
            netstatestring = "not connected";
        } else {
            String subtype = networkInfo.getSubtypeName();
            String extrainfo = networkInfo.getExtraInfo();

            netstatestring = String.format("%2$s %4$s to %1$s %3$s", networkInfo.getTypeName(),
                networkInfo.getDetailedState(), extrainfo == null ? "" : extrainfo, subtype == null ? "" : subtype);
        }

        if (networkInfo != null && networkInfo.getState() == State.CONNECTED) {
            int newnet = networkInfo.getType();
            boolean pendingDisconnect = (network == ConnectState.PENDINGDISCONNECT);
            network = ConnectState.SHOULDBECONNECTED;

            boolean sameNetwork;
            if (lastConnectedNetwork == null || lastConnectedNetwork.getType() != networkInfo.getType()
                || !equalsObj(lastConnectedNetwork.getExtraInfo(), networkInfo.getExtraInfo()))
                sameNetwork = false;
            else
                sameNetwork = true;

            /* Same network, connection still 'established' */
            if (pendingDisconnect && sameNetwork) {
                mDisconnectHandler.removeCallbacks(mDelayDisconnectRunnable);
                // Reprotect the sockets just be sure
                mManagement.networkChange(true);
            } else {
                /* Different network or connection not established anymore */
                if (screen == ConnectState.PENDINGDISCONNECT)
                    screen = ConnectState.DISCONNECTED;

                if (shouldBeConnected()) {
                    mDisconnectHandler.removeCallbacks(mDelayDisconnectRunnable);
                    if (pendingDisconnect || !sameNetwork)
                        mManagement.networkChange(sameNetwork);
                    else
                        mManagement.resumeVPN();
                }

                lastNetwork = newnet;
                lastConnectedNetwork = networkInfo;
            }

        } else if (networkInfo == null) {
            // Not connected, stop openvpn, set last connected network to no network
            lastNetwork = -1;
            if (sendusr1) {
                network = ConnectState.PENDINGDISCONNECT;
                mDisconnectHandler.postDelayed(mDelayDisconnectRunnable, DISCONNECT_WAIT * 1000);
            }
        }

        if (!netstatestring.equals(lastStateMsg))
            VpnStatus.logInfo(R.string.netstatus, netstatestring);
        VpnStatus.logDebug(String.format("Debug state info: %s, pause: %s, shouldbeconnected: %s, network: %s ",
            netstatestring, getPauseReason(), shouldBeConnected(), network));
        lastStateMsg = netstatestring;
    }

    public boolean isUserPaused() {
        return userpause == ConnectState.DISCONNECTED;
    }

    private boolean shouldBeConnected() {
        return (screen == ConnectState.SHOULDBECONNECTED && userpause == ConnectState.SHOULDBECONNECTED
            && network == ConnectState.SHOULDBECONNECTED);
    }

    private OpenVPNManagement.PauseReason getPauseReason() {
        if (userpause == ConnectState.DISCONNECTED)
            return OpenVPNManagement.PauseReason.userPause;

        if (screen == ConnectState.DISCONNECTED)
            return OpenVPNManagement.PauseReason.screenOff;

        if (network == ConnectState.DISCONNECTED)
            return OpenVPNManagement.PauseReason.noNetwork;

        return OpenVPNManagement.PauseReason.userPause;
    }

    private NetworkInfo getCurrentNetworkInfo(Context context) {
        ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return conn == null ? null : conn.getActiveNetworkInfo();
    }

}
