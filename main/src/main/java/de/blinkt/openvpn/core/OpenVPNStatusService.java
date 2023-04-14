/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by arne on 08.11.16.
 */

public class OpenVPNStatusService extends Service implements VpnStatus.StatusListener, VpnStatus.LogListener, VpnStatus.ByteCountListener {

    @Override
    public void updateStatus(@NonNull ConnectionStatus status, Bundle extra) {
        mLastUpdateMessage = new UpdateMessage(status, extra);
        Message msg = mHandler.obtainMessage(SEND_NEW_STATE, mLastUpdateMessage);
        msg.sendToTarget();
    }

    @Override
    public void setConnectedVPN(String uuid) {
        Message msg = mHandler.obtainMessage(SEND_NEW_CONNECTED_VPN, uuid);
        msg.sendToTarget();
    }

    @Override
    public void newLog(@NonNull LogItem logItem) {
        Message msg = mHandler.obtainMessage(SEND_NEW_LOGITEM, logItem);
        msg.sendToTarget();
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        Message msg = mHandler.obtainMessage(SEND_NEW_BYTECOUNT, Pair.create(in, out));
        msg.sendToTarget();
    }

    private void sendUpdate(IStatusCallbacks broadcastItem, UpdateMessage um) throws RemoteException {
        broadcastItem.updateStatus(um.status, um.extra);
    }

    private static final int SEND_NEW_LOGITEM = 100;
    private static final int SEND_NEW_STATE = 101;
    private static final int SEND_NEW_BYTECOUNT = 102;
    private static final int SEND_NEW_CONNECTED_VPN = 103;

    private UpdateMessage mLastUpdateMessage;
    private final RemoteCallbackList<IStatusCallbacks> mCallbacks = new RemoteCallbackList<>();

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            RemoteCallbackList<IStatusCallbacks> callbacks = OpenVPNStatusService.this.mCallbacks;

            // Broadcast to all clients the new value.
            int N = callbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    IStatusCallbacks broadcastItem = callbacks.getBroadcastItem(i);
                    switch (msg.what) {
                        case SEND_NEW_LOGITEM:
                            broadcastItem.newLogItem((LogItem) msg.obj);
                            break;
                        case SEND_NEW_BYTECOUNT:
                            Pair<Long, Long> inout = (Pair<Long, Long>) msg.obj;
                            broadcastItem.updateByteCount(inout.first, inout.second);
                            break;
                        case SEND_NEW_STATE:
                            sendUpdate(broadcastItem, (UpdateMessage) msg.obj);
                            break;
                        case SEND_NEW_CONNECTED_VPN:
                            broadcastItem.connectedVPN((String) msg.obj);
                            break;
                    }

                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing the dead object for us.
                }
            }
            callbacks.finishBroadcast();
        }
    };

    private static class UpdateMessage {
        ConnectionStatus status;
        Bundle extra;

        UpdateMessage(ConnectionStatus status, Bundle extra) {
            this.status = status;
            this.extra = extra;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        VpnStatus.addLogListener(this);
        VpnStatus.addByteCountListener(this);
        VpnStatus.addStatusListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        VpnStatus.removeLogListener(this);
        VpnStatus.removeByteCountListener(this);
        VpnStatus.removeStatusListener(this);
        mCallbacks.kill();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IServiceStatus.Stub mBinder = new IServiceStatus.Stub() {
        @Override
        public void registerStatusCallback(IStatusCallbacks cb) throws RemoteException {
            mCallbacks.register(cb);

            new Thread("pushLogs") {
                @Override
                public void run() {
                    try {
                        if (mLastUpdateMessage != null) {
                            sendUpdate(cb, mLastUpdateMessage);
                        }

                        LinkedList<LogItem> logBuffer;
                        synchronized (VpnStatus.LOG_LOCK) {
                            logBuffer = new LinkedList<>(VpnStatus.getLogBuffer(LogSource.OPENVPN_FRONT));
                        }

                        for (LogItem logItem : logBuffer) {
                            cb.newLogItem(logItem);
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }.start();
        }

        @Override
        public void unregisterStatusCallback(IStatusCallbacks cb) throws RemoteException {
            mCallbacks.unregister(cb);
        }

        @Override
        public String getLastConnectedVPN() throws RemoteException {
            return VpnStatus.getConnectedVpnProfile();
        }

        @Override
        public void setCachedPassword(String uuid, int type, String password) {
            PasswordCache.setCachedPassword(uuid, type, password);
        }

        @Override
        public TrafficHistory getTrafficHistory() throws RemoteException {
            TrafficHistory trafficHistory = new TrafficHistory();
            synchronized (VpnStatus.TRAFFIC_LOCK) {
                trafficHistory.copyFrom(VpnStatus.TRAFFIC_HISTORY);
            }
            return trafficHistory;
        }

    };

}
