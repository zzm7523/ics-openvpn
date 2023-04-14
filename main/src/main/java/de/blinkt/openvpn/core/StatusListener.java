/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;

import de.blinkt.xp.openvpn.BuildConfig;
import de.blinkt.xp.openvpn.R;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.activities.BaseActivity;

/**
 * Created by arne on 09.11.16.
 * 在前台Activity主进程
 *    接收OpenVPNStatusService发送的状态、日志、流量; 通过VpnStatus转发给注册的监听器
 * 在后台Service进程
 *    Nothing
 */

public class StatusListener implements VpnStatus.LogListener {

    private static final String TAG = "StatusListener";
    private Context mContext;

    @Override
    public void newLog(@NonNull LogItem logItem) {
        switch (logItem.getLogLevel()) {
            case ERROR:
                Log.e(TAG, logItem.getString(mContext));
                break;
            case WARNING:
                Log.w(TAG, logItem.getString(mContext));
                break;
            case INFO:
                Log.i(TAG, logItem.getString(mContext));
                break;
            case DEBUG:
                Log.d(TAG, logItem.getString(mContext));
                break;
            case VERBOSE:
                Log.v(TAG, logItem.getString(mContext));
                break;
            default:
                assert false : "Not a valid log level";
                Log.d(TAG, logItem.getString(mContext));
                break;
        }
    }

    public void init(@NonNull Application application) {
        if (BuildConfig.DEBUG || BuildConfig.FLAVOR.equals("skeleton")) {
            /* Set up logging to Logcat with a context) */
            VpnStatus.addLogListener(this);
        }

        this.mContext = application.getApplicationContext();
        Intent intent = new Intent(application, OpenVPNStatusService.class);
        application.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private IStatusCallbacks mCallback = new IStatusCallbacks.Stub() {
        @Override
        public void updateStatus(@NonNull ConnectionStatus status, Bundle extra) {
            VpnStatus.updateStatus(status, extra);

            BaseActivity activity = ICSOpenVPNApplication.getCurrentBaseActivity();
            if (activity == null)
                return;

            if (ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT == status.getLevel()) {
                if (extra != null) {
                    Intent intent = extra.getParcelable("LAST_INTENT");
                    activity.runOnUiThread(() -> activity.showPasswordDialog(intent, false));
                }
            } else if (ConnectionStatus.LEVEL_AUTH_FAILED == status.getLevel()) {
                String message = status.getMessage();
                String finalMessage = TextUtils.isEmpty(message) ? status.getString(activity) : message;
                activity.runOnUiThread(() -> {
                    VpnProfile profile = ProfileManager.getInstance(activity).getConnectedVpnProfile();
                    if (profile != null)
                        profile.setPassword(null);
                    activity.showAlertDialog(activity.getString(R.string.state_auth_failed), finalMessage, false);
                });
            } else if (ConnectionStatus.LEVEL_GENERAL_ERROR == status.getLevel()) {
                String message = status.getMessage();
                String finalMessage = TextUtils.isEmpty(message) ? status.getString(activity) : message;
                activity.runOnUiThread(() -> activity.showAlertDialog(activity.getString(R.string.state_general_error), finalMessage, false));
            }
        }

        @Override
        public void newLogItem(LogItem item) {
            VpnStatus.newLogItem(item);
        }

        @Override
        public void updateByteCount(long inBytes, long outBytes) {
            VpnStatus.updateByteCount(inBytes, outBytes);
        }

        @Override
        public void connectedVPN(String uuid) {
            VpnStatus.setConnectedVpnProfile(uuid);
        }

    };

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            try {
                /* Check if this a local service ... */
                if (service.queryLocalInterface(IServiceStatus.class.getName()) == null) {
                    VpnStatus.initLogCache(mContext);

                    // 前台 remote service
                    IServiceStatus serviceStatus = IServiceStatus.Stub.asInterface(service);
                    serviceStatus.registerStatusCallback(mCallback);

                    VpnStatus.setConnectedVpnProfile(serviceStatus.getLastConnectedVPN());
                    synchronized (VpnStatus.TRAFFIC_LOCK) {
                        VpnStatus.TRAFFIC_HISTORY.copyFrom(serviceStatus.getTrafficHistory());
                    }

                } else {
                    // 后台 local service
                }

            } catch (Exception ex) {
                VpnStatus.logThrowable(ex);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            VpnStatus.removeLogListener(StatusListener.this);
        }

    };

}
