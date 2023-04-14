/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.Manifest.permission;
import android.app.PendingIntent;
import android.app.Service;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import de.blinkt.xp.openvpn.R;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.api.ExternalAppDatabase;
import de.blinkt.openvpn.core.VpnStatus.ByteCountListener;
import de.blinkt.openvpn.core.VpnStatus.StatusListener;
import de.blinkt.openvpn.utils.NetworkUtils;
import de.blinkt.openvpn.utils.OpenVPNUtils;

public class OpenVPNService extends VpnService implements Callback, StatusListener, ByteCountListener {

    public static final String START_SERVICE = "de.blinkt.openvpn.START_SERVICE";
    public static final String DISCONNECT_VPN = "de.blinkt.openvpn.DISCONNECT_VPN";
    public static final String VPNSERVICE_TUN = "vpnservice-tun";
    public static final String ALWAYS_SHOW_NOTIFICATION = "de.blinkt.openvpn.NOTIFICATION_ALWAYS_VISIBLE";

    private static final String PAUSE_VPN = "de.blinkt.openvpn.PAUSE_VPN";
    private static final String RESUME_VPN = "de.blinkt.openvpn.RESUME_VPN";

    private static final String TAG = "OpenVPNService";

    private static boolean mNotificationAlwaysVisible = false;

    private final List<String> mDnslist = new ArrayList<>();
    private final NetworkSpace mRoutes = new NetworkSpace();
    private final NetworkSpace mRoutesv6 = new NetworkSpace();

    private String mDomain = null;
    private CIDRIP mLocalIP = null;
    private int mMtu;
    private String mLocalIPv6 = null;
    private DeviceStateReceiver mDeviceStateReceiver;
    private boolean mDisplayBytecount = false;
    private boolean mStarting = false;
    private long mConnecttime;

    private String mLastTunCfg;
    private String mRemoteGW;

    private VpnProfile mProfile;
    private final Object mProcessLock = new Object();
    private OpenVPNThread mProcessThread;
    private OpenVPNManagementThread mManagementThread;
    private OpenVPNNotificationHelper mNotificationHelper;

    private final IBinder mBinder = new IOpenVPNServiceInternal.Stub() {

        @Override
        public boolean protect(int fd) {
            return OpenVPNService.this.protect(fd);
        }

        @Override
        public void userPause(boolean shouldbePaused) {
            OpenVPNService.this.userPause(shouldbePaused);
        }

        @Override
        public boolean stopVPN(boolean replaceConnection) {
            return OpenVPNService.this.stopVPN(replaceConnection);
        }

        @Override
        public void addAllowedExternalApp(@NonNull String packagename) {
            ExternalAppDatabase.addAllowedApp(OpenVPNService.this, packagename);
        }

        @Override
        public boolean isAllowedExternalApp(@NonNull String packagename) {
            return ExternalAppDatabase.checkRemoteActionPermission(OpenVPNService.this, packagename);
        }

        @Override
        public void challengeResponse(@NonNull String response) {
            if (mManagementThread != null) {
                String base64 = Base64.encodeToString(response.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
                mManagementThread.sendCRResponse(base64);
            }
        }

    };

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(START_SERVICE)) {
            return mBinder;
        } else {
            return super.onBind(intent);
        }
    }

    @Override
    public void onRevoke() {
        VpnStatus.logError(R.string.permission_revoked);
        if (mManagementThread != null) {
            mManagementThread.stopVPN(false);
        }
        endVpnService();
    }

    // Similar to revoke but do not try to stop process
    public void openvpnStopped() {
        endVpnService();
    }

    private void endVpnService() {
        new Handler(getMainLooper()).post(() -> {
            if (mDeviceStateReceiver != null)
                unregisterDeviceStateReceiver();
        });

        synchronized (mProcessLock) {
            mProcessThread = null;
        }

        ProfileManager.getInstance(this).setConnectedVpnProfile(this, null);

        if (!mStarting) {
            stopForeground(!mNotificationAlwaysVisible);
            if (!mNotificationAlwaysVisible) {
                stopSelf();
                VpnStatus.removeStatusListener(this);
            }
        }
    }

    public PendingIntent getGraphPendingIntent() {
        // Let the configure Button show the Log
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(this, "de.blinkt.openvpn.activities.MainActivity"));

        intent.putExtra("PAGE", "graph");
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent startLW = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return startLW;
    }

    private void registerDeviceStateReceiver(@NonNull OpenVPNManagement magnagement) {
        // Registers BroadcastReceiver to track network connection changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        mDeviceStateReceiver = new DeviceStateReceiver(magnagement);
        // Fetch initial network state
        mDeviceStateReceiver.networkStateChange(this);
        registerReceiver(mDeviceStateReceiver, filter);
        VpnStatus.addByteCountListener(mDeviceStateReceiver);
    }

    private void unregisterDeviceStateReceiver() {
        if (mDeviceStateReceiver != null) {
            try {
                VpnStatus.removeByteCountListener(mDeviceStateReceiver);
                unregisterReceiver(mDeviceStateReceiver);
            } catch (IllegalArgumentException iae) {
                // I don't know why  this happens:
                // java.lang.IllegalArgumentException: Receiver not registered: de.blinkt.openvpn.NetworkSateReceiver@41a61a10
                // Ignore for now ...
                iae.printStackTrace();
            }
            mDeviceStateReceiver = null;
        }
    }

    public DeviceStateReceiver getDeviceStateReceiver() {
        return mDeviceStateReceiver;
    }

    public void userPause(boolean shouldBePaused) {
        if (mDeviceStateReceiver != null) {
            mDeviceStateReceiver.userPause(shouldBePaused);
        }
    }

    public boolean stopVPN(boolean replaceConnection) {
        if (mManagementThread != null) {
            return mManagementThread.stopVPN(replaceConnection);
        } else {
            return false;
        }
    }

    @Override
    public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
        /* The intent is null when we are set as always-on or the service has been restarted. */

        /* 通常Context.bindService不会导致Service.onStartCommand(...)被回调。下面这些代码是容错处理。 */
        if (intent != null && PAUSE_VPN.equals(intent.getAction())) {
            if (mDeviceStateReceiver != null)
                mDeviceStateReceiver.userPause(true);
            return START_NOT_STICKY;
        }

        if (intent != null && RESUME_VPN.equals(intent.getAction())) {
            if (mDeviceStateReceiver != null)
                mDeviceStateReceiver.userPause(false);
            return START_NOT_STICKY;
        }

        if (intent != null && START_SERVICE.equals(intent.getAction())) {
            return START_NOT_STICKY;
        }

        if (intent != null) {
            mNotificationAlwaysVisible = intent.getBooleanExtra(ALWAYS_SHOW_NOTIFICATION, false);
        }

        ProfileManager profileManager = ProfileManager.getInstance(this);

        // Always show notification here to avoid problem with startForeground timeout
        VpnStatus.logInfo(R.string.building_configration);

        String lastStatusMessage;
        synchronized (VpnStatus.STATUS_LOCK) {
            lastStatusMessage = VpnStatus.LAST_CONNECTION_STATUS.getString(this);
            VpnStatus.LAST_VPN_TUNNEL.clearDefaults();
        }

        VpnStatus.updateStatus("VPN_GENERATE_CONFIG", R.string.building_configration);
        mNotificationHelper.showNotification(mProfile, lastStatusMessage, lastStatusMessage,
            OpenVPNNotificationHelper.NOTIFICATION_CHANNEL_NEWSTATUS_ID,
            0, ConnectionStatus.LEVEL_START, null);

        if (intent != null && intent.hasExtra(VpnProfile.EXTRA_PROFILE_UUID)) {
            String profileUUID = intent.getStringExtra(VpnProfile.EXTRA_PROFILE_UUID);
            int profileVersion = intent.getIntExtra(VpnProfile.EXTRA_PROFILE_VERSION, 0);

            // Try for 10s to get current version of the profile
            mProfile = profileManager.get(this, profileUUID, profileVersion, 100);
            if (mProfile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                updateShortCutUsage(mProfile);
            }

        } else {
            /* The intent is null when we are set as always-on or the service has been restarted. */
            VpnStatus.logInfo(R.string.service_restarted);
            mProfile = profileManager.getConnectedVpnProfile(this);
            if (mProfile == null) {
                Log.d("OpenVPN", "Got no last connected profile on null intent. Assuming always on.");
                mProfile = profileManager.getAlwaysOnVPN(this);
            }

            /* Do the asynchronous keychain certificate stuff */
            // TODO zzy request start
        }

        /* Got no profile, just stop */
        if (mProfile == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        profileManager.setConnectedVpnProfile(this, mProfile);
        VpnStatus.setConnectedVpnProfile(mProfile.getUUIDString());
        VpnStatus.addByteCountListener(this);

        /* start the OpenVPN process itself in a background thread */
        new Thread(() -> startOpenVPN(intent)).start();

        return START_STICKY;
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private void updateShortCutUsage(@NonNull VpnProfile profile) {
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        shortcutManager.reportShortcutUsed(profile.getUUIDString());
    }

    private void startOpenVPN(@NonNull Intent intent) {
        // Set a flag that we are starting a new VPN
        mStarting = true;
        // Stop the previous session by interrupting the thread.
        stopOldOpenVPNProcess();
        // An old running VPN should now be exited
        mStarting = false;

        // 写OpenVPN二进制文件, 生产命令行
        String[] argv = OpenVPNLaunchHelper.buildOpenVPNArgv(this, mProfile, intent);

        // 写OpenVPN配置文件, 已移到LaunchOpenVPN.java

        String nativeLibraryDir = getApplicationInfo().nativeLibraryDir;

        // Start a new session by creating a new thread.
        // Open the Management Interface
        // start a Thread that handles incoming messages of the managment socket
        mManagementThread = new OpenVPNManagementThread("OpenVPNManagementThread", mProfile, this);
        if (mManagementThread.openManagementInterface(this)) {
            mManagementThread.start();
            VpnStatus.logInfo("started Socket Thread");
        } else {
            endVpnService();
            return;
        }

        synchronized (mProcessLock) {
            mProcessThread = new OpenVPNThread("OpenVPNProcessThread", this, argv, nativeLibraryDir, getCacheDir());
            mProcessThread.start();
        }

        new Handler(getMainLooper()).post(() -> {
            if (mDeviceStateReceiver != null)
                unregisterDeviceStateReceiver();
            registerDeviceStateReceiver(mManagementThread);
        });
    }

    private void stopOldOpenVPNProcess() {
        if (mManagementThread != null) {
            if (mManagementThread.stopVPN(true)) {
                // an old was asked to exit, wait 1s
                OpenVPNUtils.sleep(1000);
            }
        }

        synchronized (mProcessLock) {
            if (mProcessThread != null) {
                mProcessThread.setReplaceConnection();
                mProcessThread.interrupt();
                OpenVPNUtils.sleep(1000);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        VpnStatus.addStatusListener(this);
        mNotificationHelper = new OpenVPNNotificationHelper(this);
    }

    @Override
    public void onDestroy() {
        if (mManagementThread != null) {
            mManagementThread.stopVPN(true);
        }

        if (mDeviceStateReceiver != null) {
            unregisterReceiver(mDeviceStateReceiver);
        }

        // Just in case unregister for state
        VpnStatus.removeStatusListener(this);
        VpnStatus.flushLog();
    }

    private String getTunConfigString() {
        // The format of the string is not important, only that
        // two identical configurations produce the same result
        String cfg = "TUNCFG UNQIUE STRING ips:";

        if (mLocalIP != null)
            cfg += mLocalIP.toString();
        if (mLocalIPv6 != null)
            cfg += mLocalIPv6;

        cfg += "routes: " + TextUtils.join("|", mRoutes.getNetworks(true)) + TextUtils.join("|", mRoutesv6.getNetworks(true));
        cfg += "excl. routes:" + TextUtils.join("|", mRoutes.getNetworks(false)) + TextUtils.join("|", mRoutesv6.getNetworks(false));
        cfg += "dns: " + TextUtils.join("|", mDnslist);
        cfg += "domain: " + mDomain;
        cfg += "mtu: " + mMtu;

        return cfg;
    }

    public ParcelFileDescriptor openTun() {
        //Debug.startMethodTracing(getExternalFilesDir(null).toString() + "/opentun.trace", 40* 1024 * 1024);
        VpnStatus.logInfo(R.string.last_openvpn_tun_config);

        if (mLocalIP == null && mLocalIPv6 == null) {
            VpnStatus.logError(getString(R.string.opentun_no_ipaddr));
            return null;
        }

        Builder builder = new VpnService.Builder();
        if (!mProfile.isBlockUnusedAddressFamilies()) {
            allowAllAFFamilies(builder);
        }

        if (mLocalIP != null) {
            addLocalNetworksToRoutes();

            try {
                builder.addAddress(mLocalIP.ip, mLocalIP.len);
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.ip_add_error, mLocalIP, iae.getLocalizedMessage());
                return null;
            }
        }

        if (mLocalIPv6 != null) {
            String[] ipv6parts = mLocalIPv6.split("/");
            try {
                builder.addAddress(ipv6parts[0], Integer.parseInt(ipv6parts[1]));
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.ip_add_error, mLocalIPv6, iae.getLocalizedMessage());
                return null;
            }
        }

        if (mDnslist.size() == 0) {
            // No DNS Server, log a warning
            VpnStatus.logInfo(R.string.warn_no_dns);
        } else {
            for (String dns : mDnslist) {
                try {
                    builder.addDnsServer(dns);
                } catch (IllegalArgumentException iae) {
                    VpnStatus.logError(R.string.dns_add_error, dns, iae.getLocalizedMessage());
                }
            }
        }

        builder.setMtu(mMtu);
        if (mDomain != null) {
            builder.addSearchDomain(mDomain);
        }

        Collection<NetworkSpace.IpAddress> positiveIPv4Routes = mRoutes.getPositiveIPList();
        Collection<NetworkSpace.IpAddress> positiveIPv6Routes = mRoutesv6.getPositiveIPList();

        if ("samsung".equals(Build.BRAND) && mDnslist.size() >= 1) {
            // Check if the first DNS Server is in the VPN range
            try {
                NetworkSpace.IpAddress dnsServer = new NetworkSpace.IpAddress(new CIDRIP(mDnslist.get(0), 32), true);
                boolean dnsIncluded = false;
                for (NetworkSpace.IpAddress net : positiveIPv4Routes) {
                    if (net.containsNet(dnsServer)) {
                        dnsIncluded = true;
                    }
                }
                if (!dnsIncluded) {
                    String warning = String.format("Warning Samsung Android 5.0+ devices ignore DNS servers outside the VPN range. To enable DNS resolution a route to your DNS Server (%s) has been added.", mDnslist.get(0));
                    VpnStatus.logWarning(warning);
                    positiveIPv4Routes.add(dnsServer);
                }

            } catch (Exception e) {
                // If it looks like IPv6 ignore error
                if (!mDnslist.get(0).contains(":"))
                    VpnStatus.logError("Error parsing DNS Server IP: " + mDnslist.get(0));
            }
        }

        NetworkSpace.IpAddress multicastRange = new NetworkSpace.IpAddress(new CIDRIP("224.0.0.0", 3), true);

        for (NetworkSpace.IpAddress route : positiveIPv4Routes) {
            try {
                if (multicastRange.containsNet(route))
                    VpnStatus.logDebug(R.string.ignore_multicast_route, route.toString());
                else
                    builder.addRoute(route.getIPv4Address(), route.networkMask);

            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + route + " " + ia.getLocalizedMessage());
            }
        }

        for (NetworkSpace.IpAddress route6 : positiveIPv6Routes) {
            try {
                builder.addRoute(route6.getIPv6Address(), route6.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + route6 + " " + ia.getLocalizedMessage());
            }
        }

        int ipv4len = -1;
        String ipv4info = "(not set)";
        String ipv6info = "(not set)";

        if (!mProfile.isBlockUnusedAddressFamilies()) {
            ipv4info = "(not set, allowed)";
            ipv6info = "(not set, allowed)";
        }
        if (mLocalIP != null) {
            ipv4len = mLocalIP.len;
            ipv4info = mLocalIP.ip;
        }
        if (mLocalIPv6 != null) {
            ipv6info = mLocalIPv6;
        }

        if ((!mRoutes.getNetworks(false).isEmpty() || !mRoutesv6.getNetworks(false).isEmpty()) && isLockdownEnabledCompat()) {
            VpnStatus.logInfo("VPN lockdown enabled (do not allow apps to bypass VPN) enabled. Route exclusion will not allow apps to bypass VPN (e.g. bypass VPN for local networks)");
        }

        VpnStatus.logInfo(R.string.local_ip_info, ipv4info, ipv4len, ipv6info, mMtu);
        VpnStatus.logInfo(R.string.dns_server_info, TextUtils.join(", ", mDnslist), mDomain);
        VpnStatus.logInfo(R.string.routes_info_incl, TextUtils.join(", ", mRoutes.getNetworks(true)), TextUtils.join(", ", mRoutesv6.getNetworks(true)));
        VpnStatus.logInfo(R.string.routes_info_excl, TextUtils.join(", ", mRoutes.getNetworks(false)), TextUtils.join(", ", mRoutesv6.getNetworks(false)));
        VpnStatus.logDebug(R.string.routes_debug, TextUtils.join(", ", positiveIPv4Routes), TextUtils.join(", ", positiveIPv6Routes));

        setAllowedVpnPackages(builder);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // VPN always uses the default network
            builder.setUnderlyingNetworks(null);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Setting this false, will cause the VPN to inherit the underlying network metered value
            builder.setMetered(false);
        }

        String session;
        if (mLocalIP != null && mLocalIPv6 != null)
            session = getString(R.string.session_ipv6string, mProfile.getName(), mLocalIP, mLocalIPv6);
        else if (mLocalIP != null)
            session = getString(R.string.session_ipv4string, mProfile.getName(), mLocalIP);
        else
            session = getString(R.string.session_ipv4string, mProfile.getName(), mLocalIPv6);
        builder.setSession(session);

        builder.setConfigureIntent(getGraphPendingIntent());

        try {
            //Debug.stopMethodTracing();
            ParcelFileDescriptor tun = builder.establish();
            if (tun == null)
                throw new NullPointerException("Android establish() method returned null (Really broken network configuration?)");
            mLastTunCfg = getTunConfigString();
            return tun;

        } catch (Exception e) {
            VpnStatus.logError(R.string.tun_open_error);
            VpnStatus.logError(getString(R.string.error) + ": " + e.getLocalizedMessage());
            return null;

        } finally {
            // Reset information
            mDnslist.clear();
            mRoutes.clear();
            mRoutesv6.clear();
            mLocalIP = null;
            mLocalIPv6 = null;
            mDomain = null;
        }
    }

    private boolean isLockdownEnabledCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return isLockdownEnabled();
        } else {
            /* We cannot determine this, return false */
            return false;
        }
    }

    private void allowAllAFFamilies(@NonNull Builder builder) {
        builder.allowFamily(OsConstants.AF_INET);
        builder.allowFamily(OsConstants.AF_INET6);
    }

    private void addLocalNetworksToRoutes() {
        for (String localNet: NetworkUtils.getLocalNetworks(this, false)) {
            String[] parts = localNet.split("/");
            String ipAddr = parts[0];

            if (!ipAddr.equals(mLocalIP.ip)) {
                int netmask = Integer.parseInt(parts[1]);

                if (mProfile.isAllowLocalLAN()) {
                    mRoutes.addIP(new CIDRIP(ipAddr, netmask), false);
                }
            }
        }

        // IPv6 is Lollipop+ only so we can skip the lower than KITKAT case
        if (mProfile.isAllowLocalLAN()) {
            for (String localNet : NetworkUtils.getLocalNetworks(this, true)) {
                addRoutev6(localNet, false);
            }
        }
    }

    private void setAllowedVpnPackages(@NonNull Builder builder) {
        boolean atLeastOneAllowedApp = false;

        for (String pkg : mProfile.getAllowedAppsVpn()) {
            try {
                // VPN is used for all apps but exclude selected
                if (mProfile.isAllowedAppsVpnAreDisallowed()) {
                    builder.addDisallowedApplication(pkg);
                } else {
                    builder.addAllowedApplication(pkg);
                    atLeastOneAllowedApp = true;
                }
            } catch (PackageManager.NameNotFoundException ex) {
                // 不要清理，保留；用户可能随后安装APP
//              mProfile.getAllowedAppsVpn().remove(pkg);
//              VpnStatus.logInfo(R.string.app_no_longer_exists, pkg);
            }
        }

        if (!mProfile.isAllowedAppsVpnAreDisallowed() && !atLeastOneAllowedApp) {
            VpnStatus.logDebug(R.string.no_allowed_app, getPackageName());
            try {
                builder.addAllowedApplication(getPackageName());
            } catch (PackageManager.NameNotFoundException ex) {
                VpnStatus.logError("This should not happen: " + ex.getLocalizedMessage());
            }
        }

        if (mProfile.isAllowedAppsVpnAreDisallowed()) {
            VpnStatus.logDebug(R.string.disallowed_vpn_apps_info, TextUtils.join(", ", mProfile.getAllowedAppsVpn()));
        } else {
            VpnStatus.logDebug(R.string.allowed_vpn_apps_info, TextUtils.join(", ", mProfile.getAllowedAppsVpn()));
        }

        if (mProfile.isAllowAppVpnBypass()) {
            builder.allowBypass();
            VpnStatus.logDebug("Apps may bypass VPN");
        }
    }

    public void addDNS(@NonNull String dns) {
        mDnslist.add(dns);
    }

    public void setDomain(@NonNull String domain) {
        if (mDomain == null) {
            mDomain = domain;
        }
    }

    public void addRoute(@NonNull CIDRIP route, boolean include) {
        mRoutes.addIP(route, include);
    }

    public void addRoute(@NonNull String dest, @NonNull String mask, @NonNull String gateway, String device) {
        if (mLocalIP == null) {
            VpnStatus.logError("Local IP address unset and received. Neither pushed server config nor local config specifies an IP addresses. Opening tun device is most likely going to fail.");
            return;
        }

        boolean include = isAndroidTunDevice(device);

        NetworkSpace.IpAddress gatewayIP = new NetworkSpace.IpAddress(new CIDRIP(gateway, 32), false);
        NetworkSpace.IpAddress localNet = new NetworkSpace.IpAddress(mLocalIP, true);
        if (localNet.containsNet(gatewayIP))
            include = true;

        if (gateway != null && (gateway.equals("255.255.255.255") || gateway.equals(mRemoteGW)))
            include = true;

        CIDRIP route = new CIDRIP(dest, mask);

        if (route.len == 32 && !mask.equals("255.255.255.255")) {
            VpnStatus.logWarning(R.string.route_not_cidr, dest, mask);
        }

        if (route.normalise()) {
            VpnStatus.logWarning(R.string.route_not_netip, dest, route.len, route.ip);
        }

        mRoutes.addIP(route, include);
    }

    public void addRoutev6(@NonNull String network, @NonNull String device) {
        // Tun is opened after ROUTE6, no device name may be present
        boolean included = isAndroidTunDevice(device);
        addRoutev6(network, included);
    }

    public void addRoutev6(@NonNull String network, boolean included) {
        String[] v6parts = network.split("/");

        try {
            Inet6Address ip = (Inet6Address) InetAddress.getAllByName(v6parts[0])[0];
            int mask = Integer.parseInt(v6parts[1]);
            mRoutesv6.addIPv6(ip, mask, included);

        } catch (UnknownHostException e) {
            VpnStatus.logThrowable(e);
        }
    }

    private boolean isAndroidTunDevice(String device) {
        return device != null && (device.startsWith("tun") || "(null)".equals(device) || VPNSERVICE_TUN.equals(device));
    }

    public void setMtu(int mtu) {
        mMtu = mtu;
    }

    public void setLocalIP(@NonNull String local, @NonNull String netmask, int mtu, String mode) {
        mLocalIP = new CIDRIP(local, netmask);
        mMtu = mtu;

        long netMaskAsInt = NetworkUtils.getIntAddress(netmask);

        if (mLocalIP.len == 32 && !netmask.equals("255.255.255.255")) {
            // get the netmask as IP
            int masklen;
            long mask;
            if ("net30".equals(mode)) {
                masklen = 30;
                mask = 0xfffffffc;
            } else {
                masklen = 31;
                mask = 0xfffffffe;
            }

            // Netmask is Ip address +/-1, assume net30/p2p with small net
            if ((netMaskAsInt & mask) == (mLocalIP.getInt() & mask)) {
                mLocalIP.len = masklen;
            } else {
                mLocalIP.len = 32;
                if (!"p2p".equals(mode))
                    VpnStatus.logWarning(R.string.ip_not_cidr, local, netmask, mode);
            }
        }
        if (("p2p".equals(mode) && mLocalIP.len < 32) || ("net30".equals(mode) && mLocalIP.len < 30)) {
            VpnStatus.logWarning(R.string.ip_looks_like_subnet, local, netmask, mode);
        }

        /* Workaround for Lollipop, it  does not route traffic to the VPNs own network mask */
        if (mLocalIP.len <= 31 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CIDRIP interfaceRoute = new CIDRIP(mLocalIP.ip, mLocalIP.len);
            interfaceRoute.normalise();
            addRoute(interfaceRoute, true);
        }

        // Configurations are sometimes really broken...
        mRemoteGW = netmask;
    }

    public void setLocalIPv6(@NonNull String ipv6addr) {
        mLocalIPv6 = ipv6addr;
    }

    @Override
    public void updateStatus(@NonNull ConnectionStatus status, Bundle extra) {
        // If the process is not running, ignore any state, Notification should be invisible in this state
        doSendBroadcast(status);
        if (mProcessThread == null && !mNotificationAlwaysVisible)
            return;

        String channel = OpenVPNNotificationHelper.NOTIFICATION_CHANNEL_NEWSTATUS_ID;

        // Display byte count only after being connected
        if (status.getLevel() == ConnectionStatus.LEVEL_CONNECTED) {
            UiModeManager uiModeManager = (UiModeManager) getSystemService(Service.UI_MODE_SERVICE);
            if (uiModeManager.getCurrentModeType() != Configuration.UI_MODE_TYPE_TELEVISION)
                channel = OpenVPNNotificationHelper.NOTIFICATION_CHANNEL_BG_ID;
            mDisplayBytecount = true;
            mConnecttime = System.currentTimeMillis();
        } else {
            mDisplayBytecount = false;
        }

        String lastStatusMessage = null;
        Intent lastIntent = null;

        if (extra != null) {
            synchronized (VpnStatus.STATUS_LOCK) {
                lastStatusMessage = VpnStatus.LAST_CONNECTION_STATUS.getString(this);
                lastIntent = extra.getParcelable("LAST_INTENT");
            }
        }

        // Other notifications are shown,
        // This also mean we are no longer connected, ignore bytecount messages until next CONNECTED
        // Does not work :(
        mNotificationHelper.showNotification(mProfile, lastStatusMessage, lastStatusMessage, channel, 0,
            status.getLevel(), lastIntent);
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

    private void doSendBroadcast(ConnectionStatus status) {
        Intent intent = new Intent();
        intent.setAction("de.blinkt.openvpn.VPN_STATUS");
        intent.putExtra("status", status.getLevelString());
        intent.putExtra("detailstatus", status);
        sendBroadcast(intent, permission.ACCESS_NETWORK_STATE);
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (mDisplayBytecount) {
            String netstat = String.format(getString(R.string.statusline_bytecount),
                NetworkUtils.humanReadableByteCount(in, false, getResources()),
                NetworkUtils.humanReadableByteCount(diffIn / OpenVPNManagement.BYTECOUNT_INTERVAL, true, getResources()),
                NetworkUtils.humanReadableByteCount(out, false, getResources()),
                NetworkUtils.humanReadableByteCount(diffOut / OpenVPNManagement.BYTECOUNT_INTERVAL, true, getResources()));

            mNotificationHelper.showNotification(mProfile, netstat, null, OpenVPNNotificationHelper.NOTIFICATION_CHANNEL_BG_ID,
                mConnecttime, ConnectionStatus.LEVEL_CONNECTED, null);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        Runnable runnable = msg.getCallback();
        if (runnable != null) {
            runnable.run();
            return true;
        } else {
            return false;
        }
    }

    public OpenVPNManagement getManagement() {
        return mManagementThread;
    }

    public String getTunReopenStatus() {
        String currentConfiguration = getTunConfigString();
        if (currentConfiguration.equals(mLastTunCfg)) {
            return "NOACTION";
        } else {
            return "OPEN_BEFORE_CLOSE";
        }
    }

    public void requestInputFromUser(int resid, String needed) {
        VpnStatus.updateStatus("NEED", "need " + needed, resid);
        mNotificationHelper.showNotification(mProfile, getString(resid), getString(resid),
            OpenVPNNotificationHelper.NOTIFICATION_CHANNEL_NEWSTATUS_ID, 0, ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT, null);
    }

    public void trigger_sso(String info) {
        mNotificationHelper.trigger_sso(info);
    }

}
