/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import de.blinkt.xp.openvpn.BuildConfig;
import de.blinkt.xp.openvpn.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.blinkt.openvpn.utils.StringUtils;

public class OpenVPNThread extends Thread {

    public static final int M_FATAL    = (1 << 4);
    public static final int M_NONFATAL = (1 << 5);
    public static final int M_WARN     = (1 << 6);
    public static final int M_DEBUG    = (1 << 7);

    private static final String TAG = "OpenVPN";

    private String[] mArgv;
    private String mNativeLibraryDir;
    private File mCacheDir;
    private Process mProcess;
    private OpenVPNService mService;
    private volatile boolean mNoProcessExitStatus = false;

    public OpenVPNThread(@NonNull String name, @NonNull OpenVPNService service, @NonNull String[] argv,
            @NonNull String nativeLibraryDir, @NonNull File cacheDir) {
        super(name);
        mService = service;
        mArgv = argv;
        mNativeLibraryDir = nativeLibraryDir;
        mCacheDir = cacheDir;
    }

    public void stopProcess() {
        if (mProcess != null) {
            mProcess.destroy();
        }
    }

    void setReplaceConnection() {
        mNoProcessExitStatus = true;
    }

    @Override
    public void run() {
        int exitValue = 0;

        synchronized (VpnStatus.STATUS_LOCK) {
            VpnStatus.LAST_CONNECTION_STATUS.setStatus("NOPROCESS");
            VpnStatus.LAST_VPN_TUNNEL.clearDefaults();
            VpnStatus.LAST_ACCESSIBLE_RESOURCES.clear();
        }

        try {
            Log.i(TAG, "Starting OpenVPN");
            startOpenVPNThreadArgs(mArgv);
            Log.i(TAG, "OpenVPN process exited");

        } catch (Exception ex) {
            Log.e(TAG, "OpenVPNThread Got " + ex.toString());
            VpnStatus.logThrowable("Starting OpenVPN Thread", ex);

        } finally {
            if (mProcess != null) {
                try {
                    exitValue = mProcess.waitFor();
                    if (exitValue != 0) {
                        VpnStatus.logError("Process exited with exit value " + exitValue);
                    }

                } catch (IllegalThreadStateException ite) {
                    VpnStatus.logError("Illegal Thread state: " + ite.getLocalizedMessage());
                } catch (InterruptedException ie) {
                    VpnStatus.logError("InterruptedException: " + ie.getLocalizedMessage());
                }
            }

            if (!mNoProcessExitStatus) {
                mService.openvpnStopped();
            }

            if (!mNoProcessExitStatus) {
                VpnStatus.updateStatus("NOPROCESS", R.string.state_noprocess);
                VpnStatus.logOpenVPNConsole(LogLevel.INFO, "Process exited with exit value " + exitValue);
            }

            Log.i(TAG, "Exiting");
        }
    }

    private void startOpenVPNThreadArgs(@NonNull String[] argv) {
        LinkedList<String> argvlist = new LinkedList<>();
        Collections.addAll(argvlist, argv);

        ProcessBuilder pb = new ProcessBuilder(argvlist);
        pb.redirectErrorStream(true);

        try {
            setProcessEnvironment(pb, argv);
            logProcessDetails(pb);

            mProcess = pb.start();
            // Close the output, since we don't need it
            mProcess.getOutputStream().close();

            InputStream in = mProcess.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            while (true) {
                String line = br.readLine();
                if (line == null)
                    return;

                processTunnelInfo(line);
                processPolicy(line);
                processLogline(line);

                if (Thread.interrupted()) {
                    throw new InterruptedException("OpenVPN process was killed from java code");
                }
            }

        } catch (InterruptedException | IOException ex) {
            VpnStatus.logThrowable("Error reading from output of OpenVPN process", ex);
            stopProcess();
        }
    }

    private void processAccessibleResource(@NonNull String line) {
        // I/StatusListener: POLICY: "resource" "https://192.168.1.1/erp/" "https://192.168.1.1/erp/" "all"
        // I/StatusListener: POLICY: "resource" "公司文件共享" "ftp://192.168.1.17/share"
        Pattern pattern = Pattern.compile("POLICY:[\\x20|\\t|\\\"|']+resource[\\\"|'|\\x20|\\t]+(.+)");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            String[] parts = matcher.group(1).split(" ");
            if (parts.length > 1) {
                String platform = "any", program = null, description = null;
                String name = StringUtils.removeHeadTrail(parts[0], "\"'");
                String uri = StringUtils.removeHeadTrail(parts[1], "\"'");
                if (parts.length > 2)
                    platform = StringUtils.removeHeadTrail(parts[2], "\"'");
                if (parts.length > 3)
                    program = StringUtils.removeHeadTrail(parts[3], "\"'");
                if (parts.length > 4)
                    description = StringUtils.removeHeadTrail(parts[4], "\"'");

                if (StringUtils.isEmpty(platform) || StringUtils.contains("android|any|all", platform)) {
                    AccessibleResource ar = new AccessibleResource(name, uri, platform, program, description);
                    if (ar != null) {
                        synchronized (VpnStatus.STATUS_LOCK) {
                            VpnStatus.LAST_ACCESSIBLE_RESOURCES.add(ar);
                        }
                    }
                }
            }
        }
    }

    private void processTunnelInfo(@NonNull String line) {
        if (StringUtils.contains("PUSH:[\\x20|\\t]+Received[\\x20|\\t]+control[\\x20|\\t]+message", line)) {
            // PUSH: Received control message: 'PUSH_REPLY,...,route-gateway 172.14.0.1,
            int startIdx = line.indexOf("route-gateway ");
            int endIdx = -1;

            if (startIdx != -1) {
                startIdx += "route-gateway ".length();
                endIdx = line.indexOf(",", startIdx);
                if (endIdx != -1) {
                    String gatewayAddr = line.substring(startIdx, endIdx);
                    synchronized (VpnStatus.STATUS_LOCK) {
                        if (gatewayAddr.contains(":"))
                            VpnStatus.LAST_VPN_TUNNEL.setVirtualIPv6Gateway(gatewayAddr);
                        else
                            VpnStatus.LAST_VPN_TUNNEL.setVirtualIPv4Gateway(gatewayAddr);
                    }
                }
            }

        } else if (StringUtils.contains("Control[\\x20|\\t]+Channel:", line)) {
            // Control Channel: TLSv1, cipher TLSv1/SSLv3 RC4-MD5, 1024 bit RSA
            Pattern pattern = Pattern.compile("Control[\\x20|\\t]+Channel:([^,]+)");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                synchronized (VpnStatus.STATUS_LOCK) {
                    VpnStatus.LAST_VPN_TUNNEL.setTlsVersion(matcher.group(1));
                }
            }

            // Control Channel: TLSv1, cipher TLSv1/SSLv3 RC4-MD5, 1024 bit RSA
            pattern = Pattern.compile("Control[\\x20|\\t]+Channel:[\\x20|\\t]+[^,]+,([^,]+),");
            matcher = pattern.matcher(line);
            if (matcher.find()) {
                synchronized (VpnStatus.STATUS_LOCK) {
                    String[] parts = matcher.group(1).split(" ");
                    VpnStatus.LAST_VPN_TUNNEL.setTlsCipher(parts[parts.length - 1]);
                }
            }

        } else if (StringUtils.contains("Data[\\x20|\\t]+Channel:", line)) {
            // Data Channel Encrypt: Cipher 'AES-128-CBC' initialized with 128 bit key
            Pattern pattern = Pattern.compile("Cipher[\\x20|\\t|']+([^']+)");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                synchronized (VpnStatus.STATUS_LOCK) {
                    VpnStatus.LAST_VPN_TUNNEL.setCipher(matcher.group(1));
                }
            }

            // Data Channel Encrypt: Using 160 bit message hash 'SHA1' for HMAC authentication
            pattern = Pattern.compile("message[\\x20|\\t]+hash[\\x20|\\t|']+([^']+)");
            matcher = pattern.matcher(line);
            if (matcher.find()) {
                synchronized (VpnStatus.STATUS_LOCK) {
                    VpnStatus.LAST_VPN_TUNNEL.setAuth(matcher.group(1));
                }
            }
        }
    }

    private void processPolicy(@NonNull String line) {
        if (line.contains("POLICY:")) {
            if (StringUtils.contains("POLICY:[\\x20|\\t|\\\"]+resource[\\x20|\\t|\\\"]", line)) {
                // I/StatusListener: POLICY: "resource" "https://192.168.1.1/erp/" "https://192.168.1.1/erp/" "all"
                // I/StatusListener: POLICY: "resource" "公司文件共享" "ftp://192.168.1.17/share"
                processAccessibleResource(line);
            }

            // TODO
        }
    }

    private void processLogline(@NonNull String logline) {
        String status = ConnectionStatus.getLevelString(ConnectionStatus.LEVEL_GENERAL_ERROR);

        if (StringUtils.contains("TLS[\\x20|\\t]+Error:[\\x20|\\t]+TLS[\\x20|\\t]+start[\\x20|\\t]+hello[\\x20|\\t]+failed", logline)) {
            // TLS Error: TLS start hello failed to occur within 60 seconds
            VpnStatus.updateStatus(status, R.string.tls_start_hello_failed);
        } else if (StringUtils.contains("can't[\\x20|\\t]+ask[\\x20|\\t]+for[\\x20|\\t|']+Enter[\\x20|\\t]+Client[\\x20|\\t]+certificate", logline)) {
            // can't ask for 'Enter Client certificate:TLSv1.2
            VpnStatus.updateStatus(status, R.string.ask_for_client_certificate);
        } else if (StringUtils.contains("TLS[\\x20|\\t]+Error:[\\x20|\\t]+TLS[\\x20|\\t]+key[\\x20|\\t]+negotiation[\\x20|\\t]+failed", logline)) {
            // TLS Error: TLS key negotiation failed to occur within 600 seconds
            VpnStatus.updateStatus(status, R.string.tls_negotiation_failed);
        } else if (StringUtils.contains("Failed[\\x20|\\t]+to[\\x20|\\t]+negotiation[\\x20|\\t]+tunnel", logline)) {
            // HALT,Failed to negotiation tunnel options' (status=1)
            VpnStatus.updateStatus(status, R.string.tunnel_negotiation_failed);

        } else if (StringUtils.contains("certificate[\\x20|\\t]+is[\\x20|\\t]+not[\\x20|\\t]+yet[\\x20|\\t]+valid", logline)) {
            // (服务端|客户端)证书未生效
            VpnStatus.updateStatus(status, R.string.cert_no_yet_valid);

        } else if (StringUtils.contains("certificate[\\x20|\\t]+has[\\x20|\\t]+expired:", logline)) {
            // VERIFY ERROR: depth=1, error=certificate has expired: C=CN, ST=ShangHai ...
            VpnStatus.updateStatus(status, R.string.server_cert_expired);
        } else if (StringUtils.contains("self[\\x20|\\t]+signed[\\x20|\\t]+certificate[\\x20|\\t]+in[\\x20|\\t]+certificate[\\x20|\\t]+chain", logline)) {
            // VERIFY ERROR: depth=1, error=self signed certificate in certificate chain: CN=sm2_test, ...
            VpnStatus.updateStatus(status, R.string.server_cert_unknown_ca);

        } else if (StringUtils.contains("alert[\\x20|\\t]+certificate[\\x20|\\t]+revoked", logline)) {
            VpnStatus.updateStatus(status, R.string.client_cert_revoked);
        } else if (StringUtils.contains("alert[\\x20|\\t]+certificate[\\x20|\\t]+expired", logline)) {
            VpnStatus.updateStatus(status, R.string.client_cert_expired);
        } else if (StringUtils.contains("alert[\\x20|\\t]+unknown[\\x20|\\t]+ca", logline)) {
            // OpenSSL: error:14094418:SSL routines:SSL3_READ_BYTES:tlsv1 alert unknown ca
            VpnStatus.updateStatus(status, R.string.client_cert_unknown_ca);

        } else {
            // TODO ...
        }

        // OpenVPN 有machine-readable-output选项时
        // 1380308330.240114 18000002 Send to HTTP proxy: 'X-Online-Host: bla.blabla.com'
        Pattern pattern = Pattern.compile("(\\d+).(\\d+) ([0-9a-f])+ (.*)");
        Matcher matcher = pattern.matcher(logline);

        if (matcher.matches()) {
            int flags = Integer.parseInt(matcher.group(3), 16);
            LogLevel level = LogLevel.INFO;
            String msg = matcher.group(4);

            if ((flags & M_FATAL) != 0)
                level = LogLevel.ERROR;
            else if ((flags & M_NONFATAL) != 0 || (flags & M_WARN) != 0)
                level = LogLevel.WARNING;
            else if ((flags & M_DEBUG) != 0)
                level = LogLevel.DEBUG;

            if (msg != null) {
                if ((msg.endsWith("md too weak") && msg.startsWith("OpenSSL: error")) || msg.contains("error:140AB18E"))
                    VpnStatus.logError("OpenSSL reported a certificate with a weak hash, please the in app FAQ about weak hashes");

                VpnStatus.logOpenVPNConsole(level, msg);
            }

        } else {
            // Mon Mar 16 15:18:24 2020 us=543552 MANAGEMENT: CMD 'hold release
            VpnStatus.logOpenVPNConsole(LogLevel.INFO, logline);
        }
    }

    private void setProcessEnvironment(@NonNull ProcessBuilder pb, @NonNull String[] argv) throws IOException {
        String lbpath = pb.environment().get("LD_LIBRARY_PATH");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // Hack until I find a good way to get the real library path
            String applibpath = argv[0].replaceFirst("/cache/.*$", "/lib");
            if (TextUtils.isEmpty(lbpath))
                lbpath = applibpath;
            else
                lbpath = applibpath + ":" + lbpath;
            if (!applibpath.equals(mNativeLibraryDir))
                lbpath = mNativeLibraryDir + ":" + lbpath;

        } else {
            if (TextUtils.isEmpty(lbpath))
                lbpath = mNativeLibraryDir;
            else
                lbpath = mNativeLibraryDir + ":" + lbpath;
        }

        pb.environment().put("LD_LIBRARY_PATH", lbpath);
        if (BuildConfig.DEBUG)
            pb.environment().put("SHELL", "/system/bin/sh");

        String tmpDir = pb.environment().get("TMPDIR");
        if (TextUtils.isEmpty(tmpDir)) {
            pb.environment().put("TMPDIR", mCacheDir.getCanonicalPath());
        }
    }

    private void logProcessDetails(@NonNull ProcessBuilder pb) throws IOException {
        StringBuilder buffer = new StringBuilder(1024);

        buffer.append("exec process details\n");
        buffer.append("environment:\n");
        if (pb.environment() != null) {
            for (Map.Entry<String, String> env : pb.environment().entrySet()) {
                buffer.append(env.getKey()).append('=').append(env.getValue()).append('\n');
            }
        }

        buffer.append("command line:\n");
        if (pb.command() != null) {
            buffer.append(TextUtils.join(" ", pb.command()));
        }

        String message = buffer.toString();
        if (BuildConfig.DEBUG)
            System.out.println(message);

        VpnStatus.logDebug(message);
    }

}
