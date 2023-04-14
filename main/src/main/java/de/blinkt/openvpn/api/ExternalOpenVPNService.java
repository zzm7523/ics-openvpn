/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.api;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import de.blinkt.xp.openvpn.R;

import java.io.IOException;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

import de.blinkt.openvpn.LaunchOpenVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.VpnStatus.StatusListener;
import de.blinkt.openvpn.utils.OpenVPNUtils;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class ExternalOpenVPNService extends Service implements StatusListener {

    private static final int SEND_TOALL = 0;

    private final OpenVPNServiceHandler mHandler = new OpenVPNServiceHandler();

    private final RemoteCallbackList<IOpenVPNStatusCallback> mCallbacks = new RemoteCallbackList<>();

    private IOpenVPNServiceInternal mService;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mService = (IOpenVPNServiceInternal) (service);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_UNINSTALL_PACKAGE.equals(intent.getAction())) {
                // Check if the running config is temporary and installed by the app being uninstalled
                ProfileManager profileManager = ProfileManager.getInstance(context);
                VpnProfile profile = profileManager.getConnectedVpnProfile();
                if (profileManager.getConnectedVpnProfile() == profileManager.getTemporaryProfile()) {
                    if (TextUtils.equals(intent.getPackage(), profile.getProfileCreator())) {
                        if (mService != null) {
                            try {
                                mService.stopVPN(false);
                            } catch (RemoteException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        VpnStatus.addStatusListener(this);

        Intent intent = new Intent(ExternalOpenVPNService.this, OpenVPNService.class);
        intent.setAction(OpenVPNService.START_SERVICE);

        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mHandler.setService(this);

        IntentFilter uninstallBroadcast = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        registerReceiver(mBroadcastReceiver, uninstallBroadcast);
    }

    private final IOpenVPNAPIService.Stub mBinder = new IOpenVPNAPIService.Stub() {

        public void registerStatusCallback(IOpenVPNStatusCallback cb) throws RemoteException {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            if (cb != null) {
                if (mMostRecentStatus != null)
                    cb.newStatus(mMostRecentStatus.vpnUuid, mMostRecentStatus.status.getStatus(), mMostRecentStatus.status.getMessage());
                mCallbacks.register(cb);
            }
        }

        public void unregisterStatusCallback(IOpenVPNStatusCallback cb) {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            if (cb != null)
                mCallbacks.unregister(cb);
        }

        public List<APIVpnProfile> getProfiles() {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            List<APIVpnProfile> profiles = new LinkedList<>();
            for (VpnProfile profile : ProfileManager.getInstance(ExternalOpenVPNService.this).getProfiles()) {
                profiles.add(new APIVpnProfile(profile.getUUIDString(), profile.getName(), profile.isUserEditable()));
            }
            return profiles;
        }

        private void startProfile(@NonNull VpnProfile profile) {
            Intent startIntent = new Intent(Intent.ACTION_MAIN);
            startIntent.setClass(ExternalOpenVPNService.this, LaunchOpenVPN.class);
            startIntent.putExtra(LaunchOpenVPN.EXTRA_KEY, profile.getUUIDString());
            startIntent.putExtra(LaunchOpenVPN.EXTRA_HIDELOG, true);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startIntent);
        }

        public void startProfile(@NonNull String profileUUID) {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            VpnProfile profile = ProfileManager.getInstance(ExternalOpenVPNService.this).get(ExternalOpenVPNService.this, profileUUID);
            // NullPointerException, IllegalStateException 会自动传递到远端
            int checked = profile.checkProfile(getApplicationContext());
            if (checked != R.string.no_error_found) {
                // 不要抛RemoteException，client不能捕获
                throw new IllegalStateException(getString(checked));
            }
            startProfile(profile);
        }

        public void startVPN(@NonNull String inlineConfig) {
            String callingApp = ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            ConfigParser parser = new ConfigParser();

            try {
                parser.parseConfig(new StringReader(inlineConfig));
                VpnProfile profile = parser.convertProfile();
                profile.setName("Remote APP VPN");
                profile.setProfileCreator(callingApp);

                int checked = profile.checkProfile(getApplicationContext());
                if (checked != R.string.no_error_found)
                    throw new IllegalStateException(getString(checked));

                ProfileManager.getInstance(ExternalOpenVPNService.this).setTemporaryProfile(ExternalOpenVPNService.this, profile);
                startProfile(profile);

            } catch (ConfigParser.ParseError | IOException ex) {
                throw new IllegalStateException(ex.getMessage());
            }
        }

        public void disconnect() throws RemoteException {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            if (mService != null)
                mService.stopVPN(false);
        }

        public APIVpnProfile addProfile(@NonNull String name, boolean userEditable, @NonNull String config) {
            String callingPackage = ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            ConfigParser parser = new ConfigParser();

            try {
                parser.parseConfig(new StringReader(config));
                VpnProfile profile = parser.convertProfile();
                profile.setName(name);
                profile.setUserEditable(userEditable);
                profile.setProfileCreator(callingPackage);

                setUniqueProfileName(profile);
                OpenVPNUtils.importVpnProfile(ExternalOpenVPNService.this, profile, false);
                return new APIVpnProfile(profile.getUUIDString(), profile.getName(), profile.isUserEditable());

            } catch (ConfigParser.ParseError | IOException ex) {
                VpnStatus.logThrowable(ex);
                throw new IllegalStateException(ex.getMessage());
            }
        }

        private void setUniqueProfileName(@NonNull VpnProfile profile) {
            int idx = 0;
            String name = profile.getName();
            while (ProfileManager.getInstance(ExternalOpenVPNService.this).getProfileByName(name) != null) {
                name = name + " " + idx++;
            }
            profile.setName(name);
        }

        public void removeProfile(@NonNull String profileUUID) {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            ProfileManager profileManager = ProfileManager.getInstance(ExternalOpenVPNService.this);
            VpnProfile profile = profileManager.get(ExternalOpenVPNService.this, profileUUID);
            profileManager.removeProfile(ExternalOpenVPNService.this, profile);
        }

        public boolean protectSocket(@NonNull ParcelFileDescriptor pfd) throws RemoteException {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            try {
                boolean success = false;
                if (mService != null)
                    success = mService.protect(pfd.getFd());
                pfd.close();
                return success;

            } catch (IOException ex) {
                throw new IllegalStateException(ex.getMessage());
            }
        }

        public Intent prepare(@NonNull String packageName) {
            if (ExternalAppDatabase.isAllowedApp(ExternalOpenVPNService.this, packageName))
                return null;

            Intent intent = new Intent();
            intent.setClass(ExternalOpenVPNService.this, ConfirmDialog.class);
            return intent;
        }

        public Intent prepareVPNService() {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            if (VpnService.prepare(ExternalOpenVPNService.this) == null)
                return null;

            return new Intent(ExternalOpenVPNService.this, GrantPermissionsActivity.class);
        }

        public void pause() throws RemoteException {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            if (mService != null)
                mService.userPause(true);
        }

        public void resume() throws RemoteException {
            ExternalAppDatabase.checkOpenVPNPermission(ExternalOpenVPNService.this);
            if (mService != null)
                mService.userPause(false);
        }

    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCallbacks.kill();
        unbindService(mConnection);
        VpnStatus.removeStatusListener(this);
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void updateStatus(@NonNull ConnectionStatus status, Bundle extra) {
        String uuid = null;
        ProfileManager profileManager = ProfileManager.getInstance(ExternalOpenVPNService.this);
        if (profileManager.getConnectedVpnProfile() != null)
            uuid = profileManager.getConnectedVpnProfile().getUUIDString();
        mMostRecentStatus = new UpdateMessage(uuid, status, extra);
        Message msg = mHandler.obtainMessage(SEND_TOALL, mMostRecentStatus);
        msg.sendToTarget();
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

    private UpdateMessage mMostRecentStatus;

    private static class UpdateMessage {
        String vpnUuid;
        ConnectionStatus status;
        Bundle extra;

        UpdateMessage(String vpnUuid, ConnectionStatus status, Bundle extra) {
            this.vpnUuid = vpnUuid;
            this.status = status;
            this.extra = extra;
        }
    }

    private static class OpenVPNServiceHandler extends Handler {
        WeakReference<ExternalOpenVPNService> service = null;

        private void setService(ExternalOpenVPNService service) {
            this.service = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            RemoteCallbackList<IOpenVPNStatusCallback> callbacks;
            switch (msg.what) {
                case SEND_TOALL:
                    if (service == null || service.get() == null)
                        return;

                    callbacks = service.get().mCallbacks;

                    // Broadcast to all clients the new value.
                    int N = callbacks.beginBroadcast();
                    for (int i = 0; i < N; i++) {
                        try {
                            sendUpdate(callbacks.getBroadcastItem(i), (UpdateMessage) msg.obj);
                        } catch (RemoteException ex) {
                            // The RemoteCallbackList will take care of removing the dead object for us.
                        }
                    }
                    callbacks.finishBroadcast();
                    break;
            }
        }

        private void sendUpdate(@NonNull IOpenVPNStatusCallback broadcastItem, @NonNull UpdateMessage um) throws RemoteException {
            broadcastItem.newStatus(um.vpnUuid, um.status.getStatus(), um.status.getMessage());
        }

    }

}
