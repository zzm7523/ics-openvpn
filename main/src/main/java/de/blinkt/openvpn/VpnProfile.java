/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn;

import android.content.Context;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.Connection;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.utils.IOUtils;
import de.blinkt.openvpn.utils.NetworkUtils;
import de.blinkt.openvpn.utils.OpenVPNUtils;
import de.blinkt.openvpn.utils.StringUtils;
import de.blinkt.openvpn.utils.X509Utils;
import de.blinkt.xp.openvpn.R;

public class VpnProfile implements Serializable, Cloneable {

    // Note that this class cannot be moved to core where it belongs since the profile loading
    // depends on it being here
    // The Serializable documentation mentions that class name change are possible
    // but the how is unclear
    //
    public static final long MAX_EMBED_FILE_SIZE = 2048 * 1024; // 2048kB

    // Don't change this, not all parts of the program use this constant
    public static final String EXTRA_PROFILE_HASH = "de.blinkt.openvpn.profileHASH";
    public static final String EXTRA_PROFILE_UUID = "de.blinkt.openvpn.profileUUID";
    public static final String EXTRA_PROFILE_VERSION = "de.blinkt.openvpn.profileVersion";

    public static final String INLINE_TAG = "[[INLINE]]";
    public static final String DISPLAYNAME_TAG = "[[NAME]]";

    public static final int DEFAULT_MSSFIX_SIZE = 1280;

    public static final int TYPE_USERPASS_PKCS12 = 0;
    public static final int TYPE_USERPASS_KEYSTORE = 1;
    public static final int TYPE_PKCS12 = 2;
    public static final int TYPE_KEYSTORE = 3;
    public static final int TYPE_USERPASS = 4;

    public static final int X509_VERIFY_TLSREMOTE = 0;
    public static final int X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING = 1;
    public static final int X509_VERIFY_TLSREMOTE_DN = 2;
    public static final int X509_VERIFY_TLSREMOTE_RDN = 3;
    public static final int X509_VERIFY_TLSREMOTE_RDN_PREFIX = 4;

    public static final String DEFAULT_DNS1 = "202.96.209.5";
    public static final String DEFAULT_DNS2 = "8.8.8.8";

    public static final List<String> TLS_VERSIONS = Arrays.asList("TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1");
    public static final List<String> CIPHER_LIST = Arrays.asList("AES-256-CBC", "AES-192-CBC", "AES-128-CBC", "BF-CBC");
    public static final List<String> AUTH_LIST = Arrays.asList("SHA256", "SHA1");

    public static final String TLS_AUTH_FILE = "ta.key";

    private static final long serialVersionUID = 7085688938959334563L;

    private int mVersion = 0;
    private String mName;
    private UUID mUuid;

    private int mAuthenticationType = TYPE_USERPASS_PKCS12;
    private String mCaFilename;
    private String mCrlFilename;
    private String mPKCS12Filename;
    private String mAlias;
    private String mProtectPassword;
    private String mUsername;
    private String mPassword;

    // 缺省不启用TLS认证
    private boolean mUseTLSAuth = false;
    // 0 Normal, 1 Inverse
    private String mTLSAuthDirection = "0";
    private String mTLSAuthFilename;

    private Connection[] mConnections;
    private boolean mRemoteRandom = false;
    private boolean mUseRandomHostname = false;
    private boolean mUseFloat = false;

    private String mConnectRetryMax = "-1";
    private String mConnectRetry = "2";
    private String mConnectRetryMaxTime = "300";

    private boolean mUsePull = true;
    private boolean mNobind = true;
    private String mIPv4Address;
    private String mIPv6Address;

    private int mTunMtu;
    private int mMssFix = 0; // -1 is default,
    private boolean mPersistTun = false;

    private boolean mRoutenopull = false;
    private boolean mAllowLocalLAN = false;
    private boolean mUseDefaultRoute = false;  // 缺省改为false
    private String mCustomRoutes;
    private String mExcludedRoutes;
    private boolean mUseDefaultRoutev6 = false;  // 缺省改为false
    private String mCustomRoutesv6;
    private String mExcludedRoutesv6;

    private boolean mOverrideDNS = false;
    private String mDNS1 = DEFAULT_DNS1;
    private String mDNS2 = DEFAULT_DNS2;
    private String mSearchDomain = "blinkt.de";

    // 缺省不检查证书TLS服务器端扩展
    private boolean mExpectTLSCert = false;
    // 缺省不检查证书主机名
    private boolean mCheckRemoteCN = false;
    private String mRemoteCN;
    private int mX509AuthType = X509_VERIFY_TLSREMOTE_RDN;
    private String mx509UsernameField = null;

    private boolean mUseCustomConfig = false;
    private String mCustomConfigOptions;

    private String mTLSVersion;
    private String mCipher;
    private String mAuth;
    private boolean mUseLzo = true;

    private HashSet<String> mAllowedAppsVpn = new HashSet<>();
    private boolean mBlockUnusedAddressFamilies = true;
    // VPN is used for all apps but exclude selected
    private boolean mAllowedAppsVpnAreDisallowed = true;
    private boolean mAllowAppVpnBypass = false;

    // timestamp when the profile was last used
    private long mLastUsed;
    private String mImportedProfileHash;

    private boolean mTemporaryProfile = false;
    private boolean mUserEditable = true;

    private String mProfileCreator;

    public VpnProfile(@NonNull String name) {
        mUuid = UUID.randomUUID();
        mName = name;

        mConnections = new Connection[1];
        mConnections[0] = new Connection();
        mLastUsed = System.currentTimeMillis();
    }

    public int getVersion() {
        return mVersion;
    }

    public void setVersion(int version) {
        assert version > this.mVersion : "version number must be incremented";
        this.mVersion = version;
    }

    public String getName() {
        if (TextUtils.isEmpty(mName))
            return "No profile name";
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public UUID getUuid() {
        return mUuid;
    }

    public void setUuid(UUID uuid) {
        this.mUuid = uuid;
    }

    public String getCaFilename() {
        return mCaFilename;
    }

    public void setCaFilename(String caFilename) {
        this.mCaFilename = caFilename;
    }

    public String getCrlFilename() {
        return mCrlFilename;
    }

    public void setCrlFilename(String crlFilename) {
        this.mCrlFilename = crlFilename;
    }

    public String getPkcs12Filename() {
        return mPKCS12Filename;
    }

    public void setPkcs12Filename(String pkcs12Filename) {
        this.mPKCS12Filename = pkcs12Filename;
    }

    public String getAlias() {
        return mAlias;
    }

    public void setAlias(String alias) {
        this.mAlias = alias;
    }

    public String getProtectPassword() {
        return mProtectPassword;
    }

    public void setProtectPassword(String protectPassword) {
        this.mProtectPassword = protectPassword;
    }

    public String getUsername() {
        return mUsername;
    }

    public void setUsername(String username) {
        this.mUsername = username;
    }

    public String getPassword() {
        return mPassword;
    }

    public void setPassword(String password) {
        this.mPassword = password;
    }

    public boolean isUseTLSAuth() {
        return mUseTLSAuth;
    }

    public void setUseTLSAuth(boolean useTLSAuth) {
        this.mUseTLSAuth = useTLSAuth;
    }

    public String getTLSAuthDirection() {
        return mTLSAuthDirection == null ? "" : mTLSAuthDirection;
    }

    public void setTLSAuthDirection(String tlsAuthDirection) {
        this.mTLSAuthDirection = tlsAuthDirection;
    }

    public String getTLSAuthFilename() {
        return mTLSAuthFilename;
    }

    public void setTLSAuthFilename(String tlsAuthFilename) {
        this.mTLSAuthFilename = tlsAuthFilename;
    }

    public Connection[] getConnections() {
        assert mConnections != null;
        return mConnections == null ? new Connection[0] : mConnections;
    }

    public void setConnections(Connection[] connections) {
        assert connections != null;
        this.mConnections = connections == null ? new Connection[0] : connections;
    }

    public boolean isRemoteRandom() {
        return mRemoteRandom;
    }

    public void setRemoteRandom(boolean remoteRandom) {
        this.mRemoteRandom = remoteRandom;
    }

    public boolean isUseRandomHostname() {
        return mUseRandomHostname;
    }

    public void setUseRandomHostname(boolean useRandomHostname) {
        this.mUseRandomHostname = useRandomHostname;
    }

    public boolean isUseFloat() {
        return mUseFloat;
    }

    public void setUseFloat(boolean useFloat) {
        this.mUseFloat = useFloat;
    }

    public String getConnectRetryMax() {
        return mConnectRetryMax;
    }

    public void setConnectRetryMax(String connectRetryMax) {
        this.mConnectRetryMax = connectRetryMax;
    }

    public String getConnectRetry() {
        return mConnectRetry;
    }

    public void setConnectRetry(String connectRetry) {
        this.mConnectRetry = connectRetry;
    }

    public String getConnectRetryMaxTime() {
        return mConnectRetryMaxTime;
    }

    public void setConnectRetryMaxTime(String connectRetryMaxTime) {
        this.mConnectRetryMaxTime = connectRetryMaxTime;
    }

    public boolean isUsePull() {
        return mUsePull;
    }

    public void setUsePull(boolean usePull) {
        this.mUsePull = usePull;
    }

    public boolean isNobind() {
        return mNobind;
    }

    public void setNobind(boolean nobind) {
        this.mNobind = nobind;
    }

    public String getIPv4Address() {
        return mIPv4Address;
    }

    public void setIPv4Address(String ipv4Address) {
        this.mIPv4Address = ipv4Address;
    }

    public String getIPv6Address() {
        return mIPv6Address;
    }

    public void setIPv6Address(String ipv6Address) {
        this.mIPv6Address = ipv6Address;
    }

    public int getTunMtu() {
        return mTunMtu;
    }

    public void setTunMtu(int tunMtu) {
        this.mTunMtu = tunMtu;
    }

    public int getMssFix() {
        return mMssFix;
    }

    public void setMssFix(int mssFix) {
        this.mMssFix = mssFix;
    }

    public boolean isPersistTun() {
        return mPersistTun;
    }

    public void setPersistTun(boolean persistTun) {
        this.mPersistTun = persistTun;
    }

    public boolean isRoutenopull() {
        return mRoutenopull;
    }

    public void setRoutenopull(boolean routenopull) {
        this.mRoutenopull = routenopull;
    }

    public boolean isAllowLocalLAN() {
        return mAllowLocalLAN;
    }

    public void setAllowLocalLAN(boolean allowLocalLAN) {
        this.mAllowLocalLAN = allowLocalLAN;
    }

    public boolean isUseDefaultRoute() {
        return mUseDefaultRoute;
    }

    public void setUseDefaultRoute(boolean useDefaultRoute) {
        this.mUseDefaultRoute = useDefaultRoute;
    }

    public String getCustomRoutes() {
        return mCustomRoutes;
    }

    public void setCustomRoutes(String customRoutes) {
        this.mCustomRoutes = customRoutes;
    }

    public String getExcludedRoutes() {
        return mExcludedRoutes;
    }

    public void setExcludedRoutes(String excludedRoutes) {
        this.mExcludedRoutes = excludedRoutes;
    }

    public boolean isUseDefaultRoutev6() {
        return mUseDefaultRoutev6;
    }

    public void setUseDefaultRoutev6(boolean useDefaultRoutev6) {
        this.mUseDefaultRoutev6 = useDefaultRoutev6;
    }

    public String getCustomRoutesv6() {
        return mCustomRoutesv6;
    }

    public void setCustomRoutesv6(String customRoutesv6) {
        this.mCustomRoutesv6 = customRoutesv6;
    }

    public String getExcludedRoutesv6() {
        return mExcludedRoutesv6;
    }

    public void setExcludedRoutesv6(String excludedRoutesv6) {
        this.mExcludedRoutesv6 = excludedRoutesv6;
    }

    public boolean isOverrideDNS() {
        return mOverrideDNS;
    }

    public void setOverrideDNS(boolean overrideDNS) {
        this.mOverrideDNS = overrideDNS;
    }

    public String getDNS1() {
        return mDNS1;
    }

    public void setDNS1(String dns1) {
        this.mDNS1 = dns1;
    }

    public String getDNS2() {
        return mDNS2;
    }

    public void setDNS2(String dns2) {
        this.mDNS2 = dns2;
    }

    public String getSearchDomain() {
        return mSearchDomain;
    }

    public void setSearchDomain(String searchDomain) {
        this.mSearchDomain = searchDomain;
    }

    public boolean isExpectTLSCert() {
        return mExpectTLSCert;
    }

    public void setExpectTLSCert(boolean expectTLSCert) {
        this.mExpectTLSCert = expectTLSCert;
    }

    public boolean isCheckRemoteCN() {
        return mCheckRemoteCN;
    }

    public void setCheckRemoteCN(boolean checkRemoteCN) {
        this.mCheckRemoteCN = checkRemoteCN;
    }

    public String getRemoteCN() {
        return mRemoteCN;
    }

    public void setRemoteCN(String remoteCN) {
        this.mRemoteCN = remoteCN;
    }

    public int getX509AuthType() {
        return mX509AuthType;
    }

    public void setX509AuthType(int x509AuthType) {
        this.mX509AuthType = x509AuthType;
    }

    public String getX509UsernameField() {
        return mx509UsernameField;
    }

    public void setX509UsernameField(String x509UsernameField) {
        this.mx509UsernameField = x509UsernameField;
    }

    public boolean isUseCustomConfig() {
        return mUseCustomConfig;
    }

    public void setUseCustomConfig(boolean useCustomConfig) {
        this.mUseCustomConfig = useCustomConfig;
    }

    public String getCustomConfigOptions() {
        return mCustomConfigOptions == null ? "" : mCustomConfigOptions;
    }

    public void setCustomConfigOptions(String customConfigOptions) {
        this.mCustomConfigOptions = customConfigOptions;
    }

    public boolean isUseLzo() {
        return mUseLzo;
    }

    public void setUseLzo(boolean useLzo) {
        this.mUseLzo = useLzo;
    }

    public String getTLSVersion() {
        return mTLSVersion == null ? "" : mTLSVersion;
    }

    public void setTLSVersion(String tlsVersion) {
        this.mTLSVersion = tlsVersion;
    }

    public String getCipher() {
        return mCipher == null ? "" : mCipher;
    }

    public void setCipher(String cipher) {
        this.mCipher = cipher;
    }

    public String getAuth() {
        return mAuth == null ? "" : mAuth;
    }

    public void setAuth(String auth) {
        this.mAuth = auth;
    }

    public HashSet<String> getAllowedAppsVpn() {
        if (mAllowedAppsVpn == null) {
            mAllowedAppsVpn = new HashSet<>();
        }
        return mAllowedAppsVpn;
    }

    public void setAllowedAppsVpn(HashSet<String> allowedAppsVpn) {
        this.mAllowedAppsVpn = allowedAppsVpn;
    }

    public boolean isBlockUnusedAddressFamilies() {
        return mBlockUnusedAddressFamilies;
    }

    public void setBlockUnusedAddressFamilies(boolean blockUnusedAddressFamilies) {
        this.mBlockUnusedAddressFamilies = blockUnusedAddressFamilies;
    }

    public boolean isAllowedAppsVpnAreDisallowed() {
        return mAllowedAppsVpnAreDisallowed;
    }

    public void setAllowedAppsVpnAreDisallowed(boolean allowedAppsVpnAreDisallowed) {
        this.mAllowedAppsVpnAreDisallowed = allowedAppsVpnAreDisallowed;
    }

    public boolean isAllowAppVpnBypass() {
        return mAllowAppVpnBypass;
    }

    public void setAllowAppVpnBypass(boolean allowAppVpnBypass) {
        this.mAllowAppVpnBypass = allowAppVpnBypass;
    }

    public long getLastUsed() {
        return mLastUsed;
    }

    public void setLastUsed(long lastUsed) {
        this.mLastUsed = lastUsed;
    }

    public String getImportedProfileHash() {
        return mImportedProfileHash;
    }

    public void setImportedProfileHash(String importedProfileHash) {
        this.mImportedProfileHash = importedProfileHash;
    }

    public boolean isTemporaryProfile() {
        return mTemporaryProfile;
    }

    public void setTemporaryProfile(boolean temporaryProfile) {
        this.mTemporaryProfile = temporaryProfile;
    }

    public boolean isUserEditable() {
        return mUserEditable;
    }

    public void setUserEditable(boolean userEditable) {
        this.mUserEditable = userEditable;
    }

    public String getProfileCreator() {
        return mProfileCreator;
    }

    public void setProfileCreator(String profileCreator) {
        this.mProfileCreator = profileCreator;
    }

    //! Put inline data inline and other data as normal escaped filename
    public static String insertFileData(String cfgentry, String filedata) {
        if (filedata == null) {
            return String.format("%s %s\n", cfgentry, "file missing in config profile");
        } else if (isEmbedded(filedata)) {
            String content = getEmbeddedContent(filedata);
            if (TextUtils.isEmpty(cfgentry))
                return content;
            else
                return String.format(Locale.US, "<%s>\n%s\n</%s>\n", cfgentry, content, cfgentry);
        } else {
            return String.format(Locale.US, "%s %s\n", cfgentry, StringUtils.escape(filedata));
        }
    }

    public static String getDisplayName(@NonNull String embeddedFile) {
        int start = DISPLAYNAME_TAG.length();
        int end = embeddedFile.indexOf(INLINE_TAG);
        return embeddedFile.substring(start, end);
    }

    public static String getEmbeddedContent(@NonNull String data) {
        if (!data.contains(INLINE_TAG))
            return data;

        int start = data.indexOf(INLINE_TAG) + INLINE_TAG.length();
        return data.substring(start);
    }

    public static boolean isEmbedded(String data) {
        if (data != null && (data.startsWith(INLINE_TAG) || data.startsWith(DISPLAYNAME_TAG))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VpnProfile) {
            VpnProfile other = (VpnProfile) obj;
            return mUuid.equals(other.mUuid);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mConnections, mAuthenticationType, mAlias, mProtectPassword,
            mPKCS12Filename, mCaFilename, mUsername, mPassword);
    }

    public void clearDefaults() {
        mUsePull = true;
        mUseLzo = true;
        mUseTLSAuth = false;    // 缺省不启用TLS认证
        mExpectTLSCert = false; // 缺省不检查证书TLS服务器端扩展
        mCheckRemoteCN = false; // 缺省不检查证书主机名
        mPersistTun = false;
        mAllowLocalLAN = false;
        mMssFix = 0;
        mNobind = true;
        mUseDefaultRoute = false;   // 缺省false
        mUseDefaultRoutev6 = false; // 缺省false
    }

    public String generateConfig(@NonNull Context context)
            throws ConfigParser.ParseError, KeyChainException, InterruptedException, CertificateException, IOException {
        File cacheDir = context.getCacheDir();
        StringBuilder cfg = new StringBuilder();

        cfg.append("# Config for OpenVPN 6.x\n");
        cfg.append("#? uuid ").append(mUuid.toString()).append('\n');
        cfg.append("#? name ").append(mName).append('\n');
        cfg.append("setenv FORWARD_COMPATIBLE 1\n\n");

        // Enable management interface
        cfg.append("# Enables connection to GUI\n");
        cfg.append("management ").append(cacheDir.getAbsolutePath()).append("/").append("mgmtsocket").append(" unix\n");
        cfg.append("management-client\n");
        // Not needed, If the client connection fail, management-client option always generate a SIGTERM
//      cfg.append("management-signal\n");
        cfg.append("management-query-passwords\n");
        cfg.append("management-hold\n\n");

        cfg.append(String.format("setenv IV_PLAT_VER %s\n", OpenVPNUtils.getPlatformInfoString(true)));
        cfg.append(String.format("setenv IV_GUI_VER %s \n", OpenVPNUtils.getProductInfoString(context, true)));
        cfg.append("setenv IV_SSO openurl,crtext\n\n");

        cfg.append("machine-readable-output\n");
        cfg.append("allow-recursive-routing\n");

        // We cannot use anything else than tun
        cfg.append("dev tun\n");
        cfg.append("verb 4\n\n");

        cfg.append(mUsePull ? "client\n" : "tls-client\n");
        cfg.append("tls-exit\n");

        StringBuilder cabuf = new StringBuilder();

        switch (getAuthenticationType()) {
            case VpnProfile.TYPE_USERPASS:
                cfg.append("auth-user-pass\n");
                break;

            case VpnProfile.TYPE_USERPASS_PKCS12:
                cfg.append("auth-user-pass\n");
            case VpnProfile.TYPE_PKCS12:
                cfg.append(insertFileData("pkcs12", mPKCS12Filename));
                break;

            case VpnProfile.TYPE_USERPASS_KEYSTORE:
                cfg.append("auth-user-pass\n");
            case VpnProfile.TYPE_KEYSTORE:
                cfg.append("management-external-key nopadding\n");
                X509Certificate[] certChain = getCertificateChain(context);
                if (certChain != null) {
                    if (certChain.length > 0) {
                        cfg.append("<cert>\n");
                        cfg.append(X509Utils.encodeToPem(certChain[0])).append(StringUtils.endWithNewLine(cfg) ? "" : "\n");
                        cfg.append("</cert>\n");
                    }
                    if (certChain.length > 1) {
                        cabuf.append(X509Utils.encodeToPemChain(certChain, 1));
                    }
                } else {
                    throw new CertificateException(context.getString(R.string.keychain_access));
                }
                break;
        }

        if (!TextUtils.isEmpty(mCaFilename)) {
            cabuf.append(insertFileData(null, mCaFilename)).append("\n");
        }
        OpenVPNUtils.loadCACerts(context, cabuf);
        cfg.append("<ca>\n");
        cfg.append(cabuf).append(StringUtils.endWithNewLine(cfg) ? "" : "\n");
        cfg.append("</ca>\n");
/*
        File capath = new File(context.getFilesDir(), "ca_path");
        cfg.append("capath ").append(capath.getCanonicalPath()).append("\n");
*/

        if (!TextUtils.isEmpty(mCrlFilename)) {
            cfg.append(insertFileData("crl-verify", mCrlFilename));
        }

        if (mUseTLSAuth) {
            cfg.append(insertFileData("tls-auth", mTLSAuthFilename));
            if (!TextUtils.isEmpty(mTLSAuthDirection))
                cfg.append("key-direction ").append(mTLSAuthDirection).append('\n');
        }

        if (TextUtils.isEmpty(mTLSVersion)) {
            cfg.append("tls-version ").append(TextUtils.join(":", TLS_VERSIONS)).append('\n');
        } else {
            cfg.append("tls-version ").append(mTLSVersion).append('\n');
        }

        // Authentication
        if (isCheckRemoteCN()) {
            if (TextUtils.isEmpty(mRemoteCN)) {
                cfg.append("verify-x509-name ").append(StringUtils.escape(mConnections[0].getServerName())).append(" name\n");
            } else {
                switch (mX509AuthType) {
                    // 2.2 style x509 checks
                    case X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING:
                        cfg.append("compat-names no-remapping\n");
                    case X509_VERIFY_TLSREMOTE:
                        cfg.append("tls-remote ").append(StringUtils.escape(mRemoteCN)).append('\n');
                        break;

                    case X509_VERIFY_TLSREMOTE_RDN:
                        cfg.append("verify-x509-name ").append(StringUtils.escape(mRemoteCN)).append(" name\n");
                        break;

                    case X509_VERIFY_TLSREMOTE_RDN_PREFIX:
                        cfg.append("verify-x509-name ").append(StringUtils.escape(mRemoteCN)).append(" name-prefix\n");
                        break;

                    case X509_VERIFY_TLSREMOTE_DN:
                        cfg.append("verify-x509-name ").append(StringUtils.escape(mRemoteCN)).append('\n');
                        break;
                }
            }

            if (!TextUtils.isEmpty(mx509UsernameField)) {
                cfg.append("x509-username-field ").append(StringUtils.escape(mx509UsernameField)).append('\n');
            }
        }
        if (isExpectTLSCert()) {
            cfg.append("remote-cert-tls server\n");
        }

        if (TextUtils.isEmpty(mCipher)) {
            for (String cipher : CIPHER_LIST)
                cfg.append("cipher ").append(cipher).append('\n');
        } else {
            cfg.append("cipher ").append(mCipher).append('\n');
        }

        if (TextUtils.isEmpty(mAuth)) {
            for (String auth : AUTH_LIST)
                cfg.append("auth ").append(auth).append('\n');
        } else {
            cfg.append("auth ").append(mAuth).append('\n');
        }

        if (mUseLzo) {
            cfg.append("comp-lzo\n");
        }

        // Obscure Settings dialog
        if (mUseRandomHostname) {
            cfg.append("# my favorite options :)\n");
            cfg.append("remote-random-hostname\n");
        }
        if (mUseFloat) {
            cfg.append("float\n");
        }
        if (mPersistTun) {
            cfg.append("persist-tun\n");
            cfg.append("# persist-tun also enables pre resolving to avoid DNS resolve problem\n");
            cfg.append("preresolve\n");
        }

        boolean usesystemproxy = Preferences.getDefaultSharedPreferences(context).getBoolean("usesystemproxy", true);
        if (usesystemproxy && !usesExtraProxyOptions()) {
            cfg.append("\n# Use system proxy setting\n");
            cfg.append("management-query-proxy\n\n");
        }

        if (mMssFix != 0) {
            if (mMssFix != 1450)
                cfg.append(String.format(Locale.US, "mssfix %d\n", mMssFix));
            else
                cfg.append("mssfix\n");
        }

        if (mTunMtu >= 48 && mTunMtu != 1500) {
            cfg.append(String.format(Locale.US, "tun-mtu %d\n", mTunMtu));
        }

        if (mNobind) {
            cfg.append("nobind\n");
        }

        if (mUseCustomConfig) {
            cfg.append("\n# Custom configuration options\n");
            cfg.append("# You are on your on own here :)\n");
            cfg.append(mCustomConfigOptions).append('\n');
        }

        cfg.append("\n# Connection Options are at the end to allow global options (and global custom options) to influence connection blocks\n");
        boolean useTcp = false;
        StringBuilder connbuf = new StringBuilder();

        boolean noRemoteEnabled = true;
        boolean remoteRandom = false;

        for (Connection conn : mConnections) {
            if (conn.isEnabled()) {
                if (conn.checkConnection()) {
                    if (!conn.isUseUdp())
                        useTcp = true;
                    noRemoteEnabled = false;
                    connbuf.append("<connection>\n");
                    connbuf.append(conn.getConnectionBlock()).append('\n');
                    connbuf.append("</connection>\n");
                }
            }
        }

        if (noRemoteEnabled) {
            throw new IllegalStateException(context.getString(R.string.remote_no_server_selected));
        }

        if (mRemoteRandom && mConnections.length > 1) {
            remoteRandom = true;
        }

        if (remoteRandom) {
            cfg.append("remote-random\n");
        }
        if (useTcp) {
            if (mConnectRetryMax == null)
                mConnectRetryMax = "-1";
            if (!mConnectRetryMax.equals("-1"))
                cfg.append("connect-retry-max ").append(mConnectRetryMax).append('\n');
            if (TextUtils.isEmpty(mConnectRetry))
                mConnectRetry = "2";
            if (TextUtils.isEmpty(mConnectRetryMaxTime))
                mConnectRetryMaxTime = "300";
            cfg.append("connect-retry ").append(mConnectRetry).append(" ").append(mConnectRetryMaxTime).append('\n');
        }

        // DNS解析失败, 立即退出
        cfg.append("resolv-retry 0\n");
        cfg.append(connbuf.toString()).append('\n');

        // Users are confused by warnings that are misleading...
        cfg.append("ifconfig-nowarn\n\n");

        if (!mUsePull) {
            if (!TextUtils.isEmpty(mIPv4Address))
                cfg.append("ifconfig ").append(NetworkUtils.cidrToIPAndNetmask(mIPv4Address)).append('\n');

            if (!TextUtils.isEmpty(mIPv6Address)) {
                // Use our own ip as gateway since we ignore it anyway
                String fakegw = mIPv6Address.split("/", 2)[0];
                cfg.append("ifconfig-ipv6 ").append(mIPv6Address).append(' ').append(fakegw).append('\n');
            }
        }

        if (mUsePull && mRoutenopull) {
            cfg.append("route-nopull\n");
        }

        StringBuilder routebuf = new StringBuilder();
        if (mUseDefaultRoute) {
            routebuf.append("route 0.0.0.0 0.0.0.0 vpn_gateway\n");
        } else {
            for (String route : getCustomRoutes(mCustomRoutes)) {
                routebuf.append("route ").append(route).append(" vpn_gateway\n");
            }

            for (String route : getCustomRoutes(mExcludedRoutes)) {
                routebuf.append("route ").append(route).append(" net_gateway\n");
            }
        }

        if (mUseDefaultRoutev6) {
            cfg.append("route-ipv6 ::/0\n");
        } else {
            for (String route : getCustomRoutesv6(mCustomRoutesv6)) {
                routebuf.append("route-ipv6 ").append(route).append('\n');
            }
        }
        cfg.append(routebuf);

        if (mOverrideDNS || !mUsePull) {
            if (!TextUtils.isEmpty(mDNS1))
                cfg.append("dhcp-option DNS ").append(mDNS1).append('\n');
            if (!TextUtils.isEmpty(mDNS2))
                cfg.append("dhcp-option DNS ").append(mDNS2).append('\n');
            if (!TextUtils.isEmpty(mSearchDomain))
                cfg.append("dhcp-option DOMAIN ").append(mSearchDomain).append('\n');
        }

        return cfg.toString();
    }

    public X509Certificate[] getCertificateChain(@NonNull Context context) throws KeyChainException, InterruptedException {
        if (getAuthenticationType() == VpnProfile.TYPE_USERPASS_PKCS12 || getAuthenticationType() == VpnProfile.TYPE_PKCS12) {
            try {
                KeyStore ks = KeyStore.getInstance("PKCS12", org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME);
                String content = VpnProfile.getEmbeddedContent(mPKCS12Filename);
                InputStream in = new ByteArrayInputStream(Base64.decode(content, Base64.DEFAULT));
                ks.load(in, mProtectPassword == null ? "".toCharArray() : mProtectPassword.toCharArray());
                String alias = ks.aliases().nextElement();
                Certificate[] certChain = ks.getCertificateChain(alias);
                X509Certificate[] x509CertChain = new X509Certificate[certChain.length];
                for (int i = 0; i < certChain.length; ++i) {
                    x509CertChain[i] = (X509Certificate) certChain[i];
                }
                return x509CertChain;

            } catch (Throwable ex) {
                throw new KeyChainException(ex.getMessage());
            }

        } else if (getAuthenticationType() == VpnProfile.TYPE_USERPASS_KEYSTORE || getAuthenticationType() == VpnProfile.TYPE_KEYSTORE) {
            return KeyChain.getCertificateChain(context, mAlias);

        } else {
            return null;
        }
    }

    @NonNull
    private Collection<String> getCustomRoutes(String routes) {
        List<String> cidrRoutes = new ArrayList<>();
        if (routes != null) {
            for (String route : routes.split("[\n]")) {
                route = route.trim();
                if (!route.isEmpty()) {
                    String cidrroute = NetworkUtils.cidrToIPAndNetmask(route);
                    if (cidrroute == null)
                        throw new IllegalArgumentException("invalid route, " + route);
                    else
                        cidrRoutes.add(cidrroute);
                }
            }
        }
        return cidrRoutes;
    }

    private Collection<String> getCustomRoutesv6(String routes) {
        List<String> cidrRoutes = new ArrayList<>();
        if (routes != null) {
            for (String route : routes.split("[\n]")) {
                route = route.trim();
                if (!route.isEmpty())
                    cidrRoutes.add(route);
            }
        }
        return cidrRoutes;
    }

    @Override
    protected VpnProfile clone() throws CloneNotSupportedException {
        VpnProfile copy = (VpnProfile) super.clone();
        copy.mUuid = UUID.randomUUID();
        copy.mConnections = new Connection[mConnections.length];
        for (int i = 0; i < mConnections.length; i++) {
            copy.mConnections[i] = mConnections[i].clone();
        }
        copy.mAllowedAppsVpn = (HashSet<String>) mAllowedAppsVpn.clone();
        return copy;
    }

    public VpnProfile copy(String name) {
        try {
            VpnProfile copy = clone();
            copy.mName = name;
            return copy;

        } catch (CloneNotSupportedException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public int getAuthenticationType() {
        return mAuthenticationType;
    }

    public void setAuthenticationType(int authenticationType) {
        mAuthenticationType = authenticationType;
    }

    //! Return an error if something is wrong
    public int checkProfile(Context context) {
        boolean noRemoteEnabled = true;

        if (mConnections != null && mConnections.length > 0) {
            for (Connection conn : mConnections) {
                if (conn.isEnabled()) {
                    noRemoteEnabled = false;
                    if (!conn.checkConnection())
                        return R.string.server_address_error;
                    break;
                }
            }
        }

        if (noRemoteEnabled) {
            return R.string.remote_no_server_selected;
        }

        if (getAuthenticationType() == TYPE_KEYSTORE || getAuthenticationType() == TYPE_USERPASS_KEYSTORE) {
            if (mAlias == null)
                return R.string.no_keystore_cert_selected;
        }

        if (getAuthenticationType() == TYPE_PKCS12 || getAuthenticationType() == TYPE_USERPASS_PKCS12) {
            if (TextUtils.isEmpty(mPKCS12Filename))
                return R.string.no_pkcs12_file_selected;
        }

        if (isCheckRemoteCN() && mX509AuthType == X509_VERIFY_TLSREMOTE) {
            return R.string.deprecated_tls_remote;
        }

        if (!mUseDefaultRoute) {
            if (!TextUtils.isEmpty(mCustomRoutes) && getCustomRoutes(mCustomRoutes).size() == 0)
                return R.string.custom_route_format_error;

            if (!TextUtils.isEmpty(mExcludedRoutes) && getCustomRoutes(mExcludedRoutes).size() == 0)
                return R.string.custom_route_format_error;
        }

        if (mUseTLSAuth && TextUtils.isEmpty(mTLSAuthFilename)) {
            return R.string.missing_tlsauth;
        }

        // Everything okay
        return R.string.no_error_found;
    }

    public int needUserPasswordInput(String transientAuthPW) {
        // "" 不是有效的用户名和密码
        if (getAuthenticationType() == TYPE_USERPASS || getAuthenticationType() == TYPE_USERPASS_PKCS12 ||
            getAuthenticationType() == TYPE_USERPASS_KEYSTORE) {
            if (TextUtils.isEmpty(mUsername) || (TextUtils.isEmpty(getPassword()) && TextUtils.isEmpty(transientAuthPW)))
                return R.string.password;
        }

        return 0;
    }

    public int needProtectPasswordInput(String transientProtectPW) {
        // "" 是有效的PKCS12密码
        if (getAuthenticationType() == TYPE_PKCS12 || getAuthenticationType() == TYPE_USERPASS_PKCS12) {
            if (mProtectPassword == null && transientProtectPW == null)
                return R.string.pkcs12_file_encryption_key;
        }

        return 0;
    }

    // Used by the Array Adapter
    @Override
    @NonNull
    public String toString() {
        return mName;
    }

    @NonNull
    public String getUUIDString() {
        return mUuid.toString();
    }

    private boolean usesExtraProxyOptions() {
        if (mUseCustomConfig && mCustomConfigOptions != null && mCustomConfigOptions.contains("http-proxy-option ")) {
            return true;
        }
        for (Connection conn : mConnections) {
            if (conn.usesExtraProxyOptions())
                return true;
        }
        return false;
    }

}
