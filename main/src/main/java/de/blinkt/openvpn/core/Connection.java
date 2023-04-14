/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.text.TextUtils;

import java.io.Serializable;
import java.util.Locale;

public class Connection implements Serializable, Cloneable {

    public enum ProxyType {
        NONE,
        HTTP,
        SOCKS5
    }

    public static final int DEFAULT_CONNECT_TIMEOUT = 120;

    private static final long serialVersionUID = 92031902903829089L;

    private boolean mEnabled = true;

    private String mServerName;
    private String mServerPort = "1194";
    private boolean mUseUdp = true;

    private int mConnectTimeout = 0;
    private boolean mUseCustomConfig = false;
    private String mCustomConfiguration;

    private ProxyType mProxyType = ProxyType.NONE;
    private String mProxyName;
    private String mProxyPort = "1080";

    private boolean mUseProxyAuth;
    private String mProxyAuthUsername;
    private String mProxyAuthPassword;

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enabled) {
        this.mEnabled = enabled;
    }

    public String getServerName() {
        return mServerName;
    }

    public void setServerName(String serverName) {
        this.mServerName = serverName;
    }

    public String getServerPort() {
        return mServerPort;
    }

    public void setServerPort(String serverPort) {
        this.mServerPort = serverPort;
    }

    public boolean isUseUdp() {
        return mUseUdp;
    }

    public void setUseUdp(boolean useUdp) {
        this.mUseUdp = useUdp;
    }

    public int getConnectTimeout() {
        return mConnectTimeout > 0 ? mConnectTimeout : DEFAULT_CONNECT_TIMEOUT;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.mConnectTimeout = connectTimeout;
    }

    public boolean isUseCustomConfig() {
        return mUseCustomConfig;
    }

    public void setUseCustomConfig(boolean useCustomConfig) {
        this.mUseCustomConfig = useCustomConfig;
    }

    public String getCustomConfiguration() {
        return mCustomConfiguration == null ? "" : mCustomConfiguration;
    }

    public void setCustomConfiguration(String customConfiguration) {
        this.mCustomConfiguration = customConfiguration;
    }

    public ProxyType getProxyType() {
        return mProxyType;
    }

    public void setProxyType(ProxyType proxyType) {
        this.mProxyType = proxyType;
    }

    public String getProxyName() {
        return mProxyName;
    }

    public void setProxyName(String proxyName) {
        this.mProxyName = proxyName;
    }

    public String getProxyPort() {
        return mProxyPort;
    }

    public void setProxyPort(String proxyPort) {
        this.mProxyPort = proxyPort;
    }

    public boolean isUseProxyAuth() {
        return mUseProxyAuth;
    }

    public void setUseProxyAuth(boolean useProxyAuth) {
        this.mUseProxyAuth = useProxyAuth;
    }

    public String getProxyAuthUsername() {
        return mProxyAuthUsername;
    }

    public void setProxyAuthUsername(String proxyAuthUsername) {
        this.mProxyAuthUsername = proxyAuthUsername;
    }

    public String getProxyAuthPassword() {
        return mProxyAuthPassword;
    }

    public void setProxyAuthPassword(String proxyAuthPassword) {
        this.mProxyAuthPassword = proxyAuthPassword;
    }

    public boolean usesExtraProxyOptions() {
        return mUseCustomConfig && mCustomConfiguration != null && mCustomConfiguration.contains("http-proxy-option ");
    }

    public String getConnectionBlock() {
        String cfg = "";

        if (!checkConnection()) {
            throw new IllegalStateException("connection block format error");
        }

        // Server Address
        boolean ipv6Addr = mServerName.indexOf(":") >= 0;
        cfg += ("remote " + mServerName + " " + mServerPort);
        if (mUseUdp) {
            cfg += (ipv6Addr ? " udp6\n" : " udp\n");
        } else {
            cfg += (ipv6Addr ? " tcp6-client\n" : " tcp-client\n");
            if (mConnectTimeout != 0)
                cfg += String.format(Locale.US, "connect-timeout %d\n", mConnectTimeout);
        }

        // OpenVPN 6.x manages proxy connection via management interface
        if (mProxyType == ProxyType.HTTP && usesExtraProxyOptions()) {
            cfg += String.format(Locale.US, "http-proxy %s %s\n", mProxyName, mProxyPort);
            if (mUseProxyAuth)
                cfg += String.format(Locale.US, "<http-proxy-user-pass>\n%s\n%s\n</http-proxy-user-pass>\n",
                    mProxyAuthUsername, mProxyAuthPassword);
        }

        if (mProxyType == ProxyType.SOCKS5) {
            cfg += String.format(Locale.US, "socks-proxy %s %s\n", mProxyName, mProxyPort);
        }

        if (mUseCustomConfig && !TextUtils.isEmpty(mCustomConfiguration)) {
            cfg += String.format(Locale.US, "%s\n", mCustomConfiguration);
        }

        return cfg.trim();
    }

    public boolean checkConnection() {
        if (TextUtils.isEmpty(mServerName) || TextUtils.isEmpty(mServerPort))
            return false;

        try {
          Integer.parseInt(mServerPort);
        } catch (NumberFormatException ex) {
            return false;
        }

        if (mProxyType != ProxyType.NONE) {
            if (TextUtils.isEmpty(mProxyName) || TextUtils.isEmpty(mProxyPort))
                return false;

            try {
                Integer.parseInt(mProxyPort);
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Connection clone() throws CloneNotSupportedException {
        return (Connection) super.clone();
    }

}
