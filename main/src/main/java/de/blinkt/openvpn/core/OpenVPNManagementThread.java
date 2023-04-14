/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.security.KeyChain;
import android.system.Os;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.blinkt.xp.openvpn.BuildConfig;
import de.blinkt.xp.openvpn.R;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.utils.OpenVPNUtils;
import de.blinkt.openvpn.utils.ProxyDetection;
import de.blinkt.openvpn.utils.StringUtils;

public class OpenVPNManagementThread extends Thread implements OpenVPNManagement {

    private static final String TAG = "OpenVPNManagement";
    private static final List<OpenVPNManagementThread> active = new ArrayList<>();
    private final Handler mResumeHandler;
    private final VpnProfile mProfile;
    private final OpenVPNService mOpenVPNService;
    private final LinkedList<FileDescriptor> mFDList = new LinkedList<>();
    private LocalSocket mSocket;
    private LocalServerSocket mServerSocket;
    private LocalSocket mServerSocketLocal;
    private boolean mWaitingForRelease = false;
    private long mLastHoldRelease = 0;

    private PauseReason lastPauseReason = PauseReason.noNetwork;
    private PausedStateCallback mPauseCallback;
    private volatile boolean mShuttingDown = false;

    private Runnable mResumeHoldRunnable = () -> {
        if (shouldBeRunning()) {
            releaseHoldCmd();
        }
    };

    private transient Connection mCurrentProxyConnection;

    public OpenVPNManagementThread(@NonNull String name, @NonNull VpnProfile profile, @NonNull OpenVPNService service) {
        super(name);
        mProfile = profile;
        mOpenVPNService = service;
        mResumeHandler = new Handler(service.getMainLooper());
    }

    @Override
    public void pauseVPN(@NonNull PauseReason reason) {
        lastPauseReason = reason;
        signalUSR1();
    }

    @Override
    public void resumeVPN() {
        releaseHoldCmd();
        /* Reset the reason why we are disconnected */
        lastPauseReason = PauseReason.noNetwork;
    }

    @Override
    public boolean stopVPN(boolean replaceConnection) {
        boolean stopSucceed = stopOpenVPN();
        if (stopSucceed) {
            mShuttingDown = true;
        }
        return stopSucceed;
    }

    @Override
    public void networkChange(boolean samenetwork) {
        if (mWaitingForRelease)
            releaseHoldCmd();
        else if (samenetwork)
            managmentCommand("network-change samenetwork\n");
        else
            managmentCommand("network-change\n");
    }

    @Override
    public void setPauseCallback(@NonNull PausedStateCallback callback) {
        mPauseCallback = callback;
    }

    @Override
    public void sendCRResponse(@NonNull String response) {
        managmentCommand("cr-response " + response + "\n");
    }

    public boolean openManagementInterface(@NonNull Context c) {
        String socketName = c.getCacheDir().getAbsolutePath() + "/" + "mgmtsocket";
        // The mServerSocketLocal is transferred to the LocalServerSocket, ignore warning
        mServerSocketLocal = new LocalSocket();

        // Could take a while to open connection
        int tries = 8;

        while (tries > 0 && !mServerSocketLocal.isBound()) {
            try {
                mServerSocketLocal.bind(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.FILESYSTEM));
            } catch (IOException ex) {
                // wait 300 ms before retrying
                OpenVPNUtils.sleep(300);
            }
            tries--;
        }

        try {
            mServerSocket = new LocalServerSocket(mServerSocketLocal.getFileDescriptor());
            return true;
        } catch (IOException ex) {
            VpnStatus.logThrowable(ex);
        }

        return false;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[2048];
        String pendingInput = "";

        synchronized (active) {
            active.add(this);
        }

        try {
            // Wait for a client to connect
            mSocket = mServerSocket.accept();
            InputStream instream = mSocket.getInputStream();

            // Close the management socket after client connected
            try {
                mServerSocket.close();
                // Closing one of the two sockets also closes the other
                //mServerSocketLocal.close();
            } catch (IOException e) {
                VpnStatus.logThrowable(e);
            }

            managmentCommand("version 3\n");

            while (true) {
                int numbytesread = instream.read(buffer);
                if (numbytesread == -1)
                    break;

                try {
                    FileDescriptor[] fds = mSocket.getAncillaryFileDescriptors();
                    if (fds != null) {
                        Collections.addAll(mFDList, fds);
                    }

                } catch (IOException e) {
                    VpnStatus.logThrowable("Error reading fds from socket", e);
                }

                String input = new String(buffer, 0, numbytesread, StandardCharsets.UTF_8);
                pendingInput += input;
                pendingInput = processInput(pendingInput);
            }

        } catch (Exception ex) {
            if (!ex.getMessage().equals("socket closed") && !ex.getMessage().equals("Connection reset by peer")) {
                VpnStatus.logThrowable(ex);
            }

        } finally {
            synchronized (active) {
                active.remove(this);
            }

            VpnStatus.updateStatus("NOPROCESS", R.string.state_noprocess);
        }
    }

    /**
     * @param cmd command to write to management socket
     * @return true if command have been sent
     */
    public boolean managmentCommand(@NonNull String cmd) {
        String message = cmd.trim();
        if (!BuildConfig.DEBUG) {
            if (TextUtils.indexOf(cmd, "password") == 0) {
                int idx = cmd.lastIndexOf(' ');
                message = message.substring(0, idx) + "  ********";
            }
        }
        VpnStatus.logOpenVPNManagement(LogLevel.INFO, message);

        try {
            if (mSocket != null && mSocket.getOutputStream() != null) {
                mSocket.getOutputStream().write(cmd.getBytes());
                mSocket.getOutputStream().flush();
                return true;
            }

        } catch (IOException ex) {
            // Ignore socket stack traces
        }

        return false;
    }

    private String processInput(@NonNull String pendingInput) {
        while (pendingInput.contains("\n")) {
            String[] tokens = pendingInput.split("\\r?\\n", 2);

            try {
                processCommand(tokens[0]);

            } catch (Exception ex) {
                if (BuildConfig.DEBUG) {
                    // 调试时，捕获异常, 防止进程终止 (日志能发送到前端, 也方便attach调试)
                    VpnStatus.logThrowable(ex);
                    OpenVPNUtils.sleepForDebug(30000);
                }
                throw ex;

            } finally {
                if (tokens.length == 1) {
                    // No second part, newline was at the end
                    pendingInput = "";
                } else {
                    pendingInput = tokens[1];
                }
            }
        }

        return pendingInput;
    }

    private void processCommand(@NonNull String command) {
        // >NEED-OK:Need 'PROTECTFD' confirmation MSG:protect_fd_nonlocal
        if (command.startsWith(">") && command.contains(":")) {
            String[] parts = command.split(":", 2);
            String cmd = parts[0].substring(1);
            String argument = parts[1];

            switch (cmd) {
                case "INFO":
                    VpnStatus.logOpenVPNManagement(LogLevel.INFO, command);
                    /* Ignore greeting from management */
                    break;
                case "PASSWORD":
                    processPWCommand(argument);
                    break;
                case "HOLD":
                    handleHold(argument);
                    break;
                case "NEED-OK":
                    processNeedCommand(argument);
                    break;
                case "BYTECOUNT":
                    processByteCount(argument);
                    break;
                case "STATE":
                    processStatus(argument);
                    break;
                case "PROXY":
                    processProxyCMD(argument);
                    break;
                case "LOG":
                    processLogMessage(argument);
                    break;
                case "PK_SIGN":
                    processPK_SignCommand(argument);
                    break;
                case "INFOMSG":
                    processInfoMessage(argument);
                    break;
                default:
                    VpnStatus.logOpenVPNManagement(LogLevel.WARNING, command);
                    VpnStatus.logWarning("MGMT: Got unrecognized command" + command);
                    Log.w(TAG, "Got unrecognized command" + command);
                    break;
            }

        } else if (command.startsWith("PROTECTFD:")) {
            VpnStatus.logOpenVPNManagement(LogLevel.INFO, command);
            FileDescriptor fdtoprotect = mFDList.pollFirst();
            if (fdtoprotect != null) {
                protectFileDescriptor(fdtoprotect);
            }

        } else if (command.startsWith("SUCCESS:") || command.startsWith("OpenVPN Version:") ||
                command.startsWith("Management Version:") || command.equals("END")) {
            VpnStatus.logOpenVPNManagement(LogLevel.INFO, command);
            /* Ignore this kind of message too */

        } else {
            VpnStatus.logOpenVPNManagement(LogLevel.WARNING, command);
            Log.w(TAG, "Got unrecognized line from managment" + command);
        }
    }

    //! Hack O Rama 2000!
    private void protectFileDescriptor(@NonNull FileDescriptor fd) {
        try {
            Method getInt = FileDescriptor.class.getDeclaredMethod("getInt$");
            int fdint = (Integer) getInt.invoke(fd);

            // You can even get more evil by parsing toString() and extract the int from that :)
            boolean result = mOpenVPNService.protect(fdint);
            if (!result)
                VpnStatus.logWarning("Could not protect VPN socket");

            try {
                Os.close(fd);
            } catch (Exception ex) {
                VpnStatus.logThrowable("Failed to close fd (" + fd + ")", ex);
            }

        } catch (Exception ex) {
            VpnStatus.logThrowable("Failed to retrieve fd from socket (" + fd + ")", ex);
            Log.e(TAG, "Failed to retrieve fd from socket: " + fd);
        }
    }

    private void processInfoMessage(@NonNull String info) {
        VpnStatus.logOpenVPNManagement(LogLevel.INFO, info);

        if (info.startsWith("OPEN_URL:") || info.startsWith("CR_TEXT:")) {
            mOpenVPNService.trigger_sso(info);
        } else {
            VpnStatus.logDebug("Info message from server:" + info);
        }
    }

    private void processLogMessage(@NonNull String argument) {
        // 1585365664,D,MANAGEMENT: CMD 'signal SIGINT'

        // OpenVPN log_realtime为true时, log_entry_print(...)函数的flags参数为
        // LOG_PRINT_INT_DATE|LOG_PRINT_MSG_FLAGS|LOG_PRINT_LOG_PREFIX|LOG_PRINT_CRLF

        String[] args = argument.split(",", 3);
        if (args.length != 3)
            return;

        LogLevel level;
        switch (args[1]) {
            case "I":
                level = LogLevel.INFO;
                break;
            case "W":
                level = LogLevel.WARNING;
                break;
            case "D":
                level = LogLevel.DEBUG;
                break;
            case "F":
                level = LogLevel.ERROR;
                break;
            default:
                assert false : "Not a valid log level";
                level = LogLevel.DEBUG;
                break;
        }

        String message = args[2];
        VpnStatus.logOpenVPNManagement(level, message);
    }

    private boolean stopOpenVPN() {
        synchronized (active) {
            boolean sendCMD = false;
            for (OpenVPNManagementThread mt : active) {
                sendCMD = mt.managmentCommand("signal SIGINT\n");
                try {
                    if (mt.mSocket != null)
                        mt.mSocket.close();
                } catch (IOException ex) {
                    // Ignore close error on already closed socket
                }
            }
            return sendCMD;
        }
    }

    private boolean shouldBeRunning() {
        return mPauseCallback != null && mPauseCallback.shouldBeRunning();
    }

    private void handleHold(@NonNull String argument) {
        VpnStatus.logOpenVPNManagement(LogLevel.INFO, argument);

        // Waiting for hold release:10
        mWaitingForRelease = true;

        if (shouldBeRunning()) {
            int waittime = 1;
            String[] items = argument.split(":");
            if (items.length > 1)
                waittime = Integer.parseInt(argument.split(":")[1]);
            if (waittime > 1) {
                VpnStatus.updateStatus("CONNECTRETRY", String.valueOf(waittime), R.string.state_waitconnectretry);
            }
            mResumeHandler.postDelayed(mResumeHoldRunnable, waittime * 1000);
            VpnStatus.logInfo(R.string.state_waitconnectretry, String.valueOf(waittime));

        } else {
            VpnStatus.updateStatusPause(lastPauseReason);
        }
    }

    private void releaseHoldCmd() {
        if (!mWaitingForRelease)
            return;

        mWaitingForRelease = false;
        mResumeHandler.removeCallbacks(mResumeHoldRunnable);
        if ((System.currentTimeMillis() - mLastHoldRelease) < 5000) {
            OpenVPNUtils.sleep(2000);
        }
        mLastHoldRelease = System.currentTimeMillis();

        managmentCommand("hold release\n");
        managmentCommand("bytecount " + BYTECOUNT_INTERVAL + "\n");
        managmentCommand("state on\n");
    }

    private void processProxyCMD(@NonNull String argument) {
        VpnStatus.logOpenVPNManagement(LogLevel.INFO, argument);

        Connection.ProxyType proxyType = Connection.ProxyType.NONE;
        String[] args = argument.split(",", 3);
        int connectionEntryNumber = Integer.parseInt(args[0]) - 1;
        String proxyport = null;
        String proxyname = null;
        boolean proxyUseAuth = false;

        if (mProfile.getConnections().length > connectionEntryNumber) {
            Connection connection = mProfile.getConnections()[connectionEntryNumber];
            proxyType = connection.getProxyType();
            proxyname = connection.getProxyName();
            proxyport = connection.getProxyPort();
            proxyUseAuth = connection.isUseProxyAuth();

            // Use transient variable to remember http user/password
            mCurrentProxyConnection = connection;

        } else {
            VpnStatus.logError(String.format(Locale.US, "OpenVPN is asking for a proxy of an unknown connection entry (%d)",
                connectionEntryNumber));
        }

        // atuo detection of proxy
        if (proxyType == Connection.ProxyType.NONE) {
            SocketAddress proxyaddr = ProxyDetection.detectProxy(mProfile);
            if (proxyaddr instanceof InetSocketAddress) {
                InetSocketAddress isa = (InetSocketAddress) proxyaddr;
                proxyType = Connection.ProxyType.HTTP;
                proxyname = isa.getHostName();
                proxyport = String.valueOf(isa.getPort());
                proxyUseAuth = false;
            }
        }

        if (args.length >= 2 && proxyType == Connection.ProxyType.HTTP) {
            String proto = args[1];
            if (proto.equals("UDP")) {
                proxyname = null;
                VpnStatus.logInfo("Not using an HTTP proxy since the connection uses UDP");
            }
        }

        sendProxyCMD(proxyType, proxyname, proxyport, proxyUseAuth);
    }

    private void sendProxyCMD(Connection.ProxyType proxyType, String proxyname, String proxyport, boolean usePwAuth) {
        if (proxyType != Connection.ProxyType.NONE && proxyname != null) {
            VpnStatus.logInfo(R.string.using_proxy, proxyname, proxyname);
            String pwstr = usePwAuth ? " auto" : "";

            String proxycmd = String.format(Locale.US, "proxy %s %s %s%s\n",
                proxyType == Connection.ProxyType.HTTP ? "HTTP" : "SOCKS", proxyname, proxyport, pwstr);
            managmentCommand(proxycmd);
        } else {
            managmentCommand("proxy NONE\n");
        }
    }

    private void processStatus(@NonNull String argument) {
        VpnStatus.logOpenVPNManagement(LogLevel.INFO, argument);

        // >STATE:1584344148,EXITING,tls-error,,,,,
        if (!mShuttingDown) {
            String[] parts = argument.split(",", 3);
            int resid = ConnectionStatus.getLocalizedStatus(parts[1]);
            String message = parts[2].replaceFirst("^[,|\\x20|\\t]+", "").trim();

            if (ConnectionStatus.getLevel(parts[1]) == ConnectionStatus.LEVEL_CONNECTED) {
                ConnectionStatus status = new ConnectionStatus(parts[1], message, resid);
                Bundle extra = new Bundle();

                synchronized (VpnStatus.STATUS_LOCK) {
                    if (status.getLevel() == ConnectionStatus.LEVEL_CONNECTED) {
                        VpnTunnel tunnel = new VpnTunnel(VpnStatus.LAST_VPN_TUNNEL);
                        tunnel.setEstablishedTime(System.currentTimeMillis());
                        extra.putParcelable("LAST_VPN_TUNNEL", tunnel);
                        ArrayList<AccessibleResource> resources = new ArrayList<>(VpnStatus.LAST_ACCESSIBLE_RESOURCES);
                        extra.putSerializable("LAST_ACCESSIBLE_RESOURCES", resources);
                    }
                }
                VpnStatus.updateStatus(status, extra);

            } else {
                VpnStatus.updateStatus(parts[1], message, resid);
            }
        }
    }

    private void processByteCount(@NonNull String argument) {
        // >BYTECOUNT 不要记录到日志(太多了)
        // >BYTECOUNT:{BYTES_IN},{BYTES_OUT}
        int comma = argument.indexOf(',');
        long in = Long.parseLong(argument.substring(0, comma));
        long out = Long.parseLong(argument.substring(comma + 1));
        VpnStatus.updateByteCount(in, out);
    }

    private void processNeedCommand(@NonNull String argument) {
        VpnStatus.logOpenVPNManagement(LogLevel.INFO, argument);

        int p1 = argument.indexOf('\'');
        int p2 = argument.indexOf('\'', p1 + 1);

        String needed = argument.substring(p1 + 1, p2);
        String extra = argument.split(":", 2)[1];
        String status = "ok";

        switch (needed) {
            case "PROTECTFD":
                FileDescriptor fdtoprotect = mFDList.pollFirst();
                if (fdtoprotect != null)
                    protectFileDescriptor(fdtoprotect);
                break;

            case "DNSSERVER":
            case "DNS6SERVER":
                mOpenVPNService.addDNS(extra);
                break;
            case "DNSDOMAIN":
                mOpenVPNService.setDomain(extra);
                break;

            case "ROUTE": {
                String[] routeparts = extra.split(" ");

            /*
            buf_printf (&out, "%s %s %s dev %s", network, netmask, gateway, rgi->iface);
            else
            buf_printf (&out, "%s %s %s", network, netmask, gateway);
            */

                if (routeparts.length == 5) {
                    //if (BuildConfig.DEBUG)
                    //                assertEquals("dev", routeparts[3]);
                    mOpenVPNService.addRoute(routeparts[0], routeparts[1], routeparts[2], routeparts[4]);
                } else if (routeparts.length >= 3) {
                    mOpenVPNService.addRoute(routeparts[0], routeparts[1], routeparts[2], null);
                } else {
                    VpnStatus.logError("Unrecognized ROUTE cmd:" + Arrays.toString(routeparts) + " | " + argument);
                }
                break;
            }
            case "ROUTE6": {
                String[] routeparts = extra.split(" ");
                mOpenVPNService.addRoutev6(routeparts[0], routeparts[1]);
                break;
            }

            case "IFCONFIG": {
                String[] ifconfigparts = extra.split(" ");
                int mtu = Integer.parseInt(ifconfigparts[2]);
                mOpenVPNService.setLocalIP(ifconfigparts[0], ifconfigparts[1], mtu, ifconfigparts[3]);
                break;
            }
            case "IFCONFIG6": {
                String[] ifconfig6parts = extra.split(" ");
                int mtu = Integer.parseInt(ifconfig6parts[1]);
                mOpenVPNService.setMtu(mtu);
                mOpenVPNService.setLocalIPv6(ifconfig6parts[0]);
                break;
            }

            case "PERSIST_TUN_ACTION":
                // check if tun cfg stayed the same
                status = mOpenVPNService.getTunReopenStatus();
                break;
            case "OPENTUN":
                if (sendTunFD(needed, extra))
                    return;
                else
                    status = "cancel";
                // This not nice or anything but setFileDescriptors accepts only FilDescriptor class :(
                break;
            default:
                Log.e(TAG, "Unknown needok command " + argument);
                return;
        }

        String cmd = String.format("needok '%s' %s\n", needed, status);
        managmentCommand(cmd);
    }

    private boolean sendTunFD(@NonNull String needed, @NonNull String extra) {
        if (!extra.equals("tun")) {
            // We only support tun
            VpnStatus.logError(String.format("Device type %s requested, but only tun is possible with the Android API, sorry!", extra));
            return false;
        }

        ParcelFileDescriptor pfd = mOpenVPNService.openTun();
        if (pfd == null) {
            return false;
        }

        try {
            FileDescriptor fdtosend = new FileDescriptor();
            Method setInt = FileDescriptor.class.getDeclaredMethod("setInt$", int.class);
            setInt.invoke(fdtosend, pfd.getFd());

            FileDescriptor[] fds = { fdtosend };
            mSocket.setFileDescriptorsForSend(fds);

            // Trigger a send so we can close the fd on our side of the channel
            // The API documentation fails to mention that it will not reset the file descriptor to
            // be send and will happily send the file descriptor on every write ...
            String cmd = String.format("needok '%s' %s\n", needed, "ok");
            managmentCommand(cmd);

            // Set the FileDescriptor to null to stop this mad behavior
            mSocket.setFileDescriptorsForSend(null);
            pfd.close();
            return true;

        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException |
                IOException | IllegalAccessException exp) {
            VpnStatus.logThrowable("Could not send fd over socket", exp);
        }

        return false;
    }

    private void processPWCommand(@NonNull String argument) {
        VpnStatus.logOpenVPNManagement(LogLevel.INFO, argument);

        //argument has the form 	Need 'Private Key' password
        // or  ">PASSWORD:Verification Failed: '%s' ['%s']"
        String needed;

        try {
            // Ignore Auth token message, already managed by openvpn itself
            if (argument.startsWith("Auth-Token:")) {
                return;
            }

            int p1 = argument.indexOf('\'');
            int p2 = argument.indexOf('\'', p1 + 1);
            needed = argument.substring(p1 + 1, p2);
            if (argument.startsWith("Verification Failed")) {
                proccessPWFailed(needed, argument.substring(p2 + 1));
                return;
            }

        } catch (StringIndexOutOfBoundsException ex) {
            VpnStatus.logError("Could not parse management Password command: " + argument);
            return;
        }

        String username = null;
        String password = null;

        if (needed.equals("Private Key")) {
            password = PasswordCache.getProtectPassword(mProfile.getUuid(), true);
            if (password == null) {
                switch (mProfile.getAuthenticationType()) {
                    case VpnProfile.TYPE_PKCS12:
                    case VpnProfile.TYPE_USERPASS_PKCS12:
                        password = mProfile.getProtectPassword();
                        break;
                }
            }

        } else if (needed.equals("Auth")) {
            username = mProfile.getUsername();
            password = PasswordCache.getAuthPassword(mProfile.getUuid(), true);
            password = password == null ? mProfile.getPassword() : password;

        } else if (needed.equals("HTTP Proxy")) {
            if (mCurrentProxyConnection != null) {
                username = mCurrentProxyConnection.getProxyAuthUsername();
                password = mCurrentProxyConnection.getProxyAuthPassword();
            }
        }

        if (password != null) {
            if (username != null) {
                String usercmd = String.format("username '%s' %s\n", needed, StringUtils.escape(username));
                managmentCommand(usercmd);
            }
            String pwdcmd = String.format("password '%s' %s\n", needed, StringUtils.escape(password));
            managmentCommand(pwdcmd);

        } else {
            mOpenVPNService.requestInputFromUser(R.string.password, needed);
            VpnStatus.logError(String.format("Openvpn requires Authentication type '%s' but no password/key information available", needed));
        }
    }

    private void proccessPWFailed(@NonNull String needed, @NonNull String args) {
        int startIdx = args.indexOf("['");
        int endIdx = args.indexOf("']");
        String[] parts;
        if (startIdx != -1 && endIdx != -1)
            parts = args.substring(startIdx + "['".length(), endIdx).split("\\s+");
        else
            parts = args.split("\\s+");

        String errmsg = null;
        if (parts.length >= 2) {
            errmsg = parts[0].trim() + " " + parts[0].trim();
        }

        VpnStatus.updateStatus(ConnectionStatus.getLevelString(ConnectionStatus.LEVEL_AUTH_FAILED),
            errmsg == null ? needed + args : errmsg, R.string.state_auth_failed);
    }

    public void signalUSR1() {
        mResumeHandler.removeCallbacks(mResumeHoldRunnable);
        if (!mWaitingForRelease) {
            managmentCommand("signal SIGUSR1\n");
        } else {
            // If signalusr1 is called update the state string
            // if there is another for stopping
            VpnStatus.updateStatusPause(lastPauseReason);
        }
    }

    private void processPK_SignCommand(@NonNull String argument) {
        VpnStatus.logOpenVPNManagement(LogLevel.INFO, argument);

        String[] parts = argument.split(",");
        boolean pkcs1Padding = true;
        if (parts.length > 1)
            pkcs1Padding = parts[1].equals("RSA_PKCS1_PADDING");

        String signedData = getSignedData(parts[0], pkcs1Padding);

        if (TextUtils.isEmpty(signedData)) {
            managmentCommand("pk-sig\n");
            managmentCommand("\nEND\n");
            stopOpenVPN();
        } else {
            managmentCommand("pk-sig\n");
            managmentCommand(signedData);
            managmentCommand("\nEND\n");
        }
    }

    /**
     * 成功时返回签名; 任何错误, 记录日志然后返回null
     */
    @Nullable
    private String getSignedData(@NonNull String base64data, boolean pkcs1Padding) {
        byte[] signedBytes = null;

        if (mProfile.getAuthenticationType() == VpnProfile.TYPE_USERPASS_KEYSTORE || mProfile.getAuthenticationType() == VpnProfile.TYPE_KEYSTORE) {
            try {
                PrivateKey privKey = KeyChain.getPrivateKey(mOpenVPNService, mProfile.getAlias());
                if (privKey != null) {
                    byte[] data = Base64.decode(base64data, Base64.DEFAULT);
                    if (privKey.getAlgorithm().equals("EC")) {
                        java.security.Signature signer = java.security.Signature.getInstance("NONEwithECDSA");
                        signer.initSign(privKey);
                        signer.update(data);
                        signedBytes = signer.sign();

                    } else {
                        /* ECB is perfectly fine in this special case, since we are using it for
                         * the public/private part in the TLS exchange
                         */
                        Cipher signer = Cipher.getInstance(pkcs1Padding ? "RSA/ECB/PKCS1PADDING" : "RSA/ECB/NoPadding");
                        signer.init(Cipher.ENCRYPT_MODE, privKey);
                        signedBytes = signer.doFinal(data);
                    }
                }

                // KeyChainException | InterruptedException | GeneralSecurityException | ...
            } catch (Exception ex) {
                VpnStatus.logError(R.string.error_rsa_sign, ex.getClass().toString(), ex.getMessage());
            }
        }

        return signedBytes == null ? null : Base64.encodeToString(signedBytes, Base64.NO_WRAP);
    }

}
