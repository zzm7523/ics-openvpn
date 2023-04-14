/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.utils.StringUtils;

//! Openvpn Config FIle Parser, probably not 100% accurate but close enough

// And remember, this is valid :)
// --<foo>
// bar
// </foo>
public class ConfigParser {

    public static class ParseError extends Exception {

        private static final long serialVersionUID = 27060L;

        public ParseError(String msg) {
            super(msg);
        }

    }

    public static final String CONVERTED_PROFILE = "converted Profile";

    private static final String[] unsupportedOptions = {
        "config",
        "tls-server"
    };

    // Ignore all scripts
    // in most cases these won't work and user who wish to execute scripts will
    // figure out themselves
    private static final String[] ignoreOptions = {
        "tls-client",
        "allow-recursive-routing",
        "askpass",
        "auth-nocache",
        "up",
        "down",
        "route-up",
        "ipchange",
        "route-pre-down",
        "auth-user-pass-verify",
        "block-outside-dns",
        "client-cert-not-required",
        "dhcp-release",
        "dhcp-renew",
        "dh",
        "group",
        "ip-win32",
        "ifconfig-nowarn",
        "management-hold",
        "management",
        "management-client",
        "management-query-remote",
        "management-query-passwords",
        "management-query-proxy",
        "management-external-key",
        "management-forget-disconnect",
        "management-signal",
        "management-log-cache",
        "management-up-down",
        "management-client-user",
        "management-client-group",
        "pause-exit",
        "preresolve",
        "plugin",
        "machine-readable-output",
        "persist-key",
        "push",
        "register-dns",
        "route-delay",
        "route-gateway",
        "route-metric",
        "route-method",
        "status",
        "script-security",
        "show-net-up",
        "suppress-timestamps",
        "tap-sleep",
        "tmp-dir",
        "tun-ipv6",
        "topology",
        "user",
        "verb",
        "push-peer-info",
        "win-sys",
        "capath",
        "cert",
        "key",
        /* 需要忽略的扩展选项 */
        "tls-version",
    };

    private static final String[][] ignoreOptionsWithArg = {
        {"setenv", "IV_GUI_VER"},
        {"setenv", "IV_SSO"},
        {"setenv", "IV_PLAT_VER"},
        {"setenv", "IV_OPENVPN_GUI_VERSION"},
        {"engine", "dynamic"},
        {"setenv", "CLIENT_CERT"},
        {"resolv-retry", "60"}
    };

    private static final String[] connectionOptions = {
        "local",
        "remote",
        "float",
        "port",
        "connect-retry",
        "connect-timeout",
        "connect-retry-max",
        "link-mtu",
        "tun-mtu",
        "tun-mtu-extra",
        "fragment",
        "mtu-disc",
        "local-port",
        "remote-port",
        "bind",
        "nobind",
        "proto",
        "http-proxy",
        "http-proxy-retry",
        "http-proxy-timeout",
        "http-proxy-option",
        "socks-proxy",
        "socks-proxy-retry",
        "http-proxy-user-pass",
        "explicit-exit-notify",
    };

    private enum linestate {
        initial, readin_single_quote, reading_quoted, reading_unquoted, done
    }

    private HashSet<String> connectionOptionsSet = new HashSet<>(Arrays.asList(connectionOptions));
    private HashMap<String, Vector<Vector<String>>> options = new HashMap<>();
    private String authUserPassFile;

    public static void useEmbbedUserAuth(@NonNull VpnProfile profile, @NonNull String inlinedata) {
        String data = VpnProfile.getEmbeddedContent(inlinedata);
        if (data != null) {
            String[] parts = data.split("\n");
            if (parts.length >= 2) {
                profile.setUsername(parts[0]);
                profile.setPassword(parts[1]);
            }
        }
    }

    public static void useEmbbedHttpAuth(@NonNull Connection conn, @NonNull String inlinedata) {
        String data = VpnProfile.getEmbeddedContent(inlinedata);
        if (data != null) {
            String[] parts = data.split("\n");
            if (parts.length >= 2) {
                conn.setProxyAuthUsername(parts[0]);
                conn.setProxyAuthPassword(parts[1]);
                conn.setUseProxyAuth(true);
            }
        }
    }

    private String checkFileHeader(String line) throws ParseError {
        if ((line.startsWith("PK\003\004") || line.startsWith("PK\007\008"))) {
            throw new ParseError("Input looks like a ZIP Archive. Import is only possible for OpenVPN config files (.ovpn/.conf)");
        }
        if (line.startsWith("\uFEFF")) {
            line = line.substring(1);
        }
        return line;
    }

    public void parseConfig(@NonNull Reader reader) throws IOException, ParseError {
        Map<String, String> optionAliases = new HashMap<>();
        optionAliases.put("server-poll-timeout", "timeout-connect");

        BufferedReader br = new BufferedReader(reader);
        int lineno = 0;

        try {
            while (true) {
                String line = br.readLine();
                lineno++;
                if (line == null)
                    break;

                if (lineno == 1) {
                    line = checkFileHeader(line);
                }

                Vector<String> args = parseline(line);
                if (args.size() == 0)
                    continue;

                if (args.get(0).startsWith("--"))
                    args.set(0, args.get(0).substring(2));

                checkinlinefile(args, br);

                String optionname = args.get(0);
                if (optionAliases.get(optionname) != null)
                    optionname = optionAliases.get(optionname);

                if (!options.containsKey(optionname))
                    options.put(optionname, new Vector<>());
                options.get(optionname).add(args);
            }

        } catch (java.lang.OutOfMemoryError memoryError) {
            throw new ParseError("File too large to parse: " + memoryError.getLocalizedMessage());
        }
    }

    private void checkinlinefile(Vector<String> args, BufferedReader br) throws IOException, ParseError {
        String arg0 = args.get(0).trim();

        // CHeck for <foo>
        if (arg0.startsWith("<") && arg0.endsWith(">")) {
            String argname = arg0.substring(1, arg0.length() - 1);
            String inlinefile = VpnProfile.INLINE_TAG;
            String endtag = String.format("</%s>", argname);

            do {
                String line = br.readLine();
                if (line == null) {
                    throw new ParseError(String.format("No endtag </%s> for starttag <%s> found", argname, argname));
                }
                if (line.trim().equals(endtag))
                    break;
                else {
                    inlinefile += line;
                    inlinefile += "\n";
                }
            } while (true);

            if (inlinefile.endsWith("\n"))
                inlinefile = inlinefile.substring(0, inlinefile.length() - 1);

            args.clear();
            args.add(argname);
            args.add(inlinefile);
        }
    }

    public String getAuthUserPassFile() {
        return authUserPassFile;
    }

    private boolean space(char c) {
        // I really hope nobody is using zero bytes inside his/her config file
        // to sperate parameter but here we go:
        return Character.isWhitespace(c) || c == '\0';
    }

    // adapted openvpn's parse function to java
    private Vector<String> parseline(String line) throws ParseError {
        Vector<String> parameters = new Vector<>();

        if (line.length() == 0)
            return parameters;

        linestate state = linestate.initial;
        boolean backslash = false;
        char out = 0;

        int pos = 0;
        String currentarg = "";

        do {
            // Emulate the c parsing ...
            char in;
            if (pos < line.length())
                in = line.charAt(pos);
            else
                in = '\0';

            if (!backslash && in == '\\' && state != linestate.readin_single_quote) {
                backslash = true;
            } else {
                if (state == linestate.initial) {
                    if (!space(in)) {
                        if (in == ';' || in == '#') { /* comment */
                            if (pos + 1 < line.length()) {
                                char q = line.charAt(pos + 1);
                                if (q != '?') /* #? 有特殊意义, 不是注解 */
                                    break;
                            } else
                                break;
                        }
                        if (!backslash && in == '\"')
                            state = linestate.reading_quoted;
                        else if (!backslash && in == '\'')
                            state = linestate.readin_single_quote;
                        else {
                            out = in;
                            state = linestate.reading_unquoted;
                        }
                    }
                } else if (state == linestate.reading_unquoted) {
                    if (!backslash && space(in))
                        state = linestate.done;
                    else
                        out = in;
                } else if (state == linestate.reading_quoted) {
                    if (!backslash && in == '\"')
                        state = linestate.done;
                    else
                        out = in;
                } else if (state == linestate.readin_single_quote) {
                    if (in == '\'')
                        state = linestate.done;
                    else
                        out = in;
                }

                if (state == linestate.done) {
                    state = linestate.initial;
                    parameters.add(currentarg);
                    currentarg = "";
                    out = 0;
                }

                if (backslash && out != 0) {
                    if (!(out == '\\' || out == '\"' || space(out))) {
                        throw new ParseError("Options warning: Bad backslash ('\\') usage");
                    }
                }
                backslash = false;
            }

            /* store parameter character */
            if (out != 0) {
                currentarg += out;
            }

        } while (pos++ < line.length());

        return parameters;
    }

    // This method is far too long
    @SuppressWarnings("ConstantConditions")
    public VpnProfile convertProfile() throws ParseError, IOException {
        VpnProfile profile = new VpnProfile(CONVERTED_PROFILE);
        // Pull, client, tls-client
        profile.clearDefaults();
        if (options.containsKey("client") || options.containsKey("pull")) {
            profile.setUsePull(true);
            options.remove("pull");
            options.remove("client");
        }

        Vector<Vector<String>> routes = getAllOption("route", 1, 4);
        if (routes != null) {
            String routeopt = "";
            String routeExcluded = "";

            for (Vector<String> route : routes) {
                String netmask = "255.255.255.255";
                String gateway = "vpn_gateway";

                if (route.size() >= 3)
                    netmask = route.get(2);
                if (route.size() >= 4)
                    gateway = route.get(3);

                try {
                    String net = route.get(1);
                    CIDRIP cidr = new CIDRIP(net, netmask);
                    if (gateway.equals("net_gateway"))
                        routeExcluded += cidr.toString() + " ";
                    else
                        routeopt += cidr.toString() + " ";
                } catch (ArrayIndexOutOfBoundsException | NumberFormatException ex) {
                    throw new ParseError("Could not parse netmask of route " + netmask);
                }
            }

            profile.setCustomRoutes(routeopt);
            profile.setExcludedRoutes(routeExcluded);
        }

        Vector<Vector<String>> routesV6 = getAllOption("route-ipv6", 1, 4);
        if (routesV6 != null) {
            String customIPv6Routes = "";
            for (Vector<String> route : routesV6) {
                customIPv6Routes += route.get(1) + " ";
            }

            profile.setCustomRoutesv6(customIPv6Routes);
        }

        Vector<String> routeNoPull = getOption("route-nopull", 0, 0);
        if (routeNoPull != null)
            profile.setRoutenopull(true);

        // Also recognize tls-auth [inline] direction ...
        Vector<Vector<String>> tlsauthoptions = getAllOption("tls-auth", 1, 2);
        if (tlsauthoptions != null) {
            for (Vector<String> tlsauth : tlsauthoptions) {
                if (tlsauth != null) {
                    if (!tlsauth.get(1).equals("[inline]")) {
                        profile.setTLSAuthFilename(tlsauth.get(1));
                        profile.setUseTLSAuth(true);
                    }
                    if (tlsauth.size() == 3)
                        profile.setTLSAuthDirection(tlsauth.get(2));
                }
            }
        }

        Vector<String> direction = getOption("key-direction", 1, 1);
        if (direction != null)
            profile.setTLSAuthDirection(direction.get(1));

        for (String crypt : new String[]{"tls-crypt", "tls-crypt-v2"}) {
            Vector<String> tlscrypt = getOption(crypt, 1, 1);
            if (tlscrypt != null) {
                profile.setUseTLSAuth(true);
                profile.setTLSAuthFilename(tlscrypt.get(1));
                profile.setTLSAuthDirection(crypt);
            }
        }

        Vector<Vector<String>> defgw = getAllOption("redirect-gateway", 0, 7);
        if (defgw != null) {
            checkRedirectParameters(profile, defgw, true);
        }

        Vector<Vector<String>> redirectPrivate = getAllOption("redirect-private", 0, 5);
        if (redirectPrivate != null) {
            checkRedirectParameters(profile, redirectPrivate, false);
        }
        Vector<String> dev = getOption("dev", 1, 1);
        Vector<String> devtype = getOption("dev-type", 1, 1);

        if ((devtype != null && devtype.get(1).equals("tun")) || (dev != null && dev.get(1).startsWith("tun"))
            || (devtype == null && dev == null)) {
            //everything okay
        } else {
            throw new ParseError("Sorry. Only tun mode is supported. See the FAQ for more detail");
        }

        Vector<String> mssfix = getOption("mssfix", 0, 2);
        if (mssfix != null) {
            if (mssfix.size() >= 2) {
                try {
                    profile.setMssFix(Integer.parseInt(mssfix.get(1)));
                } catch (NumberFormatException e) {
                    throw new ParseError("Argument to --mssfix has to be an integer");
                }
            } else {
                profile.setMssFix(1450); // OpenVPN default size
            }
            // Ignore mtu argument of OpenVPN3 and report error otherwise
            if (mssfix.size() >= 3 && !(mssfix.get(2).equals("mtu"))) {
                throw new ParseError("Second argument to --mssfix unkonwn");
            }
        }


        Vector<String> tunmtu = getOption("tun-mtu", 1, 1);
        if (tunmtu != null) {
            try {
                profile.setTunMtu(Integer.parseInt(tunmtu.get(1)));
            } catch (NumberFormatException e) {
                throw new ParseError("Argument to --tun-mtu has to be an integer");
            }
        }

        Vector<String> mode = getOption("mode", 1, 1);
        if (mode != null) {
            if (!mode.get(1).equals("p2p"))
                throw new ParseError("Invalid mode for --mode specified, need p2p");
        }

        Vector<Vector<String>> dhcpoptions = getAllOption("dhcp-option", 2, 2);
        if (dhcpoptions != null) {
            for (Vector<String> dhcpoption : dhcpoptions) {
                String type = dhcpoption.get(1);
                String arg = dhcpoption.get(2);

                if (type.equals("DOMAIN")) {
                    profile.setSearchDomain(dhcpoption.get(2));
                } else if (type.equals("DNS")) {
                    profile.setOverrideDNS(true);
                    if (VpnProfile.DEFAULT_DNS1.equals(profile.getDNS1()))
                        profile.setDNS1(arg);
                    else
                        profile.setDNS2(arg);
                }
            }
        }

        Vector<String> ifconfig = getOption("ifconfig", 2, 2);
        if (ifconfig != null) {
            try {
                CIDRIP cidr = new CIDRIP(ifconfig.get(1), ifconfig.get(2));
                profile.setIPv4Address(cidr.toString());
            } catch (NumberFormatException nfe) {
                throw new ParseError("Could not pase ifconfig IP address: " + nfe.getLocalizedMessage());
            }
        }

        if (getOption("remote-random-hostname", 0, 0) != null)
            profile.setUseRandomHostname(true);

        if (getOption("float", 0, 0) != null)
            profile.setUseFloat(true);

        if (getOption("comp-lzo", 0, 1) != null)
            profile.setUseLzo(true);

        Vector<String> cipher = getOption("cipher", 1, 1);
        if (cipher != null)
            profile.setCipher(cipher.get(1));

        Vector<String> auth = getOption("auth", 1, 1);
        if (auth != null)
            profile.setAuth(auth.get(1));

        Vector<String> ca = getOption("ca", 1, 1);
        if (ca != null)
            profile.setCaFilename(ca.get(1));

        Vector<String> authuser = getOption("auth-user-pass", 0, 1);
        if (authuser != null) {
            if (authuser.size() > 1) {
                if (!authuser.get(1).startsWith(VpnProfile.INLINE_TAG))
                    authUserPassFile = authuser.get(1);
                profile.setUsername(null);
                useEmbbedUserAuth(profile, authuser.get(1));
            }
        }

        Vector<String> pkcs12 = getOption("pkcs12", 1, 1);
        if (pkcs12 != null) {
            profile.setPkcs12Filename(pkcs12.get(1));
            profile.setAuthenticationType(authuser == null ? VpnProfile.TYPE_PKCS12 : VpnProfile.TYPE_USERPASS_PKCS12);
        }

        Vector<String> cryptoapicert = getOption("cryptoapicert", 1, 1);
        if (cryptoapicert != null) {
            profile.setAuthenticationType(authuser == null ? VpnProfile.TYPE_KEYSTORE : VpnProfile.TYPE_USERPASS_KEYSTORE);
        }

        Vector<String> compatnames = getOption("compat-names", 1, 2);
        Vector<String> nonameremapping = getOption("no-name-remapping", 1, 1);
        Vector<String> tlsremote = getOption("tls-remote", 1, 1);
        if (tlsremote != null) {
            profile.setRemoteCN(tlsremote.get(1));
            profile.setCheckRemoteCN(true);
            profile.setX509AuthType(VpnProfile.X509_VERIFY_TLSREMOTE);

            if ((compatnames != null && compatnames.size() > 2) || (nonameremapping != null))
                profile.setX509AuthType(VpnProfile.X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING);
        }

        Vector<String> verifyx509name = getOption("verify-x509-name", 1, 2);
        if (verifyx509name != null) {
            profile.setRemoteCN(verifyx509name.get(1));
            profile.setCheckRemoteCN(true);

            if (verifyx509name.size() > 2) {
                if (verifyx509name.get(2).equals("name"))
                    profile.setX509AuthType(VpnProfile.X509_VERIFY_TLSREMOTE_RDN);
                else if (verifyx509name.get(2).equals("subject"))
                    profile.setX509AuthType(VpnProfile.X509_VERIFY_TLSREMOTE_DN);
                else if (verifyx509name.get(2).equals("name-prefix"))
                    profile.setX509AuthType(VpnProfile.X509_VERIFY_TLSREMOTE_RDN_PREFIX);
                else
                    throw new ParseError("Unknown parameter to verify-x509-name: " + verifyx509name.get(2));
            } else {
                profile.setX509AuthType(VpnProfile.X509_VERIFY_TLSREMOTE_DN);
            }
        }

        Vector<String> x509usernamefield = getOption("x509-username-field", 1, 1);
        if (x509usernamefield != null) {
            profile.setX509UsernameField(x509usernamefield.get(1));
        }

        if (getOption("nobind", 0, 0) != null)
            profile.setNobind(true);

        if (getOption("persist-tun", 0, 0) != null)
            profile.setPersistTun(true);

        Vector<String> connectretry = getOption("connect-retry", 1, 2);
        if (connectretry != null) {
            profile.setConnectRetry(connectretry.get(1));
            if (connectretry.size() > 2)
                profile.setConnectRetryMaxTime(connectretry.get(2));
        }

        Vector<String> connectretrymax = getOption("connect-retry-max", 1, 1);
        if (connectretrymax != null)
            profile.setConnectRetryMax(connectretrymax.get(1));

        Vector<Vector<String>> remotetls = getAllOption("remote-cert-tls", 1, 1);
        if (remotetls != null) {
            if (remotetls.get(0).get(1).equals("server"))
                profile.setExpectTLSCert(true);
            else
                options.put("remotetls", remotetls);
        }

        Vector<String> crlfile = getOption("crl-verify", 1, 2);
        if (crlfile != null) {
            // If the 'dir' parameter is present just add it as custom option ..
            if (crlfile.size() == 3 && crlfile.get(2).equals("dir"))
                profile.setCustomConfigOptions(profile.getCustomConfigOptions() + TextUtils.join(" ", crlfile) + "\n");
            else
                // Save the filename for the config converter to add later
                profile.setCrlFilename(crlfile.get(1));
        }

        Pair<Connection, Connection[]> conns = parseConnectionOptions(null);
        profile.setConnections(conns.second);

        Vector<Vector<String>> connectionBlocks = getAllOption("connection", 1, 1);
        if (conns.second.length > 0 && connectionBlocks != null) {
            throw new ParseError("Using a <connection> block and --remote is not allowed.");
        }

        if (connectionBlocks != null) {
            profile.setConnections(new Connection[connectionBlocks.size()]);
            int connIndex = 0;

            for (Vector<String> conn : connectionBlocks) {
                Pair<Connection, Connection[]> connectionBlockConnection = parseConnection(conn.get(1), conns.first);
                if (connectionBlockConnection.second.length != 1)
                    throw new ParseError("A <connection> block must have exactly one remote");
                profile.getConnections()[connIndex++] = connectionBlockConnection.second[0];
            }
        }
        if (getOption("remote-random", 0, 0) != null)
            profile.setRemoteRandom(true);

        Vector<String> protoforce = getOption("proto-force", 1, 1);
        if (protoforce != null) {
            boolean disableUDP;
            String protoToDisable = protoforce.get(1);
            if (protoToDisable.equals("udp"))
                disableUDP = true;
            else if (protoToDisable.equals("tcp"))
                disableUDP = false;
            else
                throw new ParseError(String.format("Unknown protocol %s in proto-force", protoToDisable));

            for (Connection conn : profile.getConnections()) {
                if (conn.isUseUdp() == disableUDP)
                    conn.setEnabled(false);
            }
        }

        checkExtensionOptions(profile);

        checkIgnoreAndInvalidOptions(profile);

        return profile;
    }

    private boolean isUdpProto(String proto) throws IllegalArgumentException {
        if (proto.equals("udp") || proto.equals("udp4") || proto.equals("udp6")) {
            return true;
        } else if (proto.equals("tcp-client") || proto.equals("tcp") || proto.equals("tcp4") ||
            proto.endsWith("tcp4-client") || proto.equals("tcp6") || proto.endsWith("tcp6-client")) {
            return false;
        } else {
            throw new IllegalArgumentException("Unsupported option to --proto " + proto);
        }
    }

    private Pair<Connection, Connection[]> parseConnection(String connection, Connection defaultValues)
            throws IOException, ParseError {
        // Parse a connection Block as a new configuration file
        ConfigParser parser = new ConfigParser();
        StringReader reader = new StringReader(connection.substring(VpnProfile.INLINE_TAG.length()));
        parser.parseConfig(reader);
        Pair<Connection, Connection[]> conn = parser.parseConnectionOptions(defaultValues);
        return conn;
    }

    private Pair<Connection, Connection[]> parseConnectionOptions(Connection connDefault) throws ParseError {
        Connection conn;
        if (connDefault != null) {
            try {
                conn = connDefault.clone();
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            conn = new Connection();
        }

        Vector<String> port = getOption("port", 1, 1);
        if (port != null)
            conn.setServerPort(port.get(1));

        Vector<String> rport = getOption("rport", 1, 1);
        if (rport != null)
            conn.setServerPort(rport.get(1));

        Vector<String> proto = getOption("proto", 1, 1);
        if (proto != null)
            conn.setUseUdp(isUdpProto(proto.get(1)));

        Vector<String> connectTimeout = getOption("connect-timeout", 1, 1);
        if (connectTimeout != null) {
            try {
                conn.setConnectTimeout(Integer.parseInt(connectTimeout.get(1)));
            } catch (NumberFormatException nfe) {
                throw new ParseError(String.format("Argument to connect-timeout (%s) must to be an integer: %s",
                    connectTimeout.get(1), nfe.getLocalizedMessage()));
            }
        }

        Vector<String> proxy = getOption("socks-proxy", 1, 2);
        if (proxy == null)
            proxy = getOption("http-proxy", 2, 2);

        if (proxy != null) {
            if (proxy.get(0).equals("socks-proxy")) {
                conn.setProxyType(Connection.ProxyType.SOCKS5);
                // socks defaults to 1080, http always sets port
                conn.setProxyPort("1080");
            } else {
                conn.setProxyType(Connection.ProxyType.HTTP);
            }

            conn.setProxyName(proxy.get(1));
            if (proxy.size() >= 3)
                conn.setProxyPort(proxy.get(2));
        }

        Vector<String> httpproxyauthhttp = getOption("http-proxy-user-pass", 1, 1);
        if (httpproxyauthhttp != null)
            useEmbbedHttpAuth(conn, httpproxyauthhttp.get(1));

        // Parse remote config
        Vector<Vector<String>> remotes = getAllOption("remote", 1, 3);

        Vector<String> optionsToRemove = new Vector<>();
        // Assume that we need custom options if connectionDefault are set or in the connection specific set
        for (Map.Entry<String, Vector<Vector<String>>> option : options.entrySet()) {
            if (connDefault != null || connectionOptionsSet.contains(option.getKey())) {
                conn.setCustomConfiguration(conn.getCustomConfiguration() + getOptionStrings(option.getValue()));
                optionsToRemove.add(option.getKey());
            }
        }
        for (String o : optionsToRemove)
            options.remove(o);

        if (!TextUtils.isEmpty(conn.getCustomConfiguration()))
            conn.setUseCustomConfig(true);

        // Make remotes empty to simplify code
        if (remotes == null)
            remotes = new Vector<>();

        Connection[] connections = new Connection[remotes.size()];

        int i = 0;
        for (Vector<String> remote : remotes) {
            try {
                connections[i] = conn.clone();
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
            switch (remote.size()) {
                case 4:
                    connections[i].setUseUdp(isUdpProto(remote.get(3)));
                case 3:
                    connections[i].setServerPort(remote.get(2));
                case 2:
                    connections[i].setServerName(remote.get(1));
            }
            i++;
        }

        return Pair.create(conn, connections);
    }

    private void checkRedirectParameters(VpnProfile profile, Vector<Vector<String>> defgw, boolean defaultRoute) {
        boolean noIpv4 = false;
        if (defaultRoute) {
            for (Vector<String> redirect : defgw) {
                for (int i = 1; i < redirect.size(); i++) {
                    if (redirect.get(i).equals("block-local"))
                        profile.setAllowLocalLAN(false);
                    else if (redirect.get(i).equals("unblock-local"))
                        profile.setAllowLocalLAN(true);
                    else if (redirect.get(i).equals("!ipv4"))
                        noIpv4 = true;
                    else if (redirect.get(i).equals("ipv6"))
                        profile.setUseDefaultRoutev6(true);
                }
            }
        }
        if (defaultRoute && !noIpv4) {
            profile.setUseDefaultRoute(true);
        }
    }

    private void checkExtensionOptions(VpnProfile profile) throws ParseError {
        Vector<Vector<String>> exts = getAllOption("#?", 1, 9);
        if (exts != null && !exts.isEmpty()) {
            for (Vector<String> ext : exts) {
                if (ext.size() >= 3) {
                    String param1 = ext.get(1);
                    String param2 = ext.get(2);

                    if (TextUtils.equals(param1, "uuid"))
                        profile.setUuid(UUID.fromString(param2));
                    else if (TextUtils.equals(param1, "name"))
                        profile.setName(param2);
                }
            }
        }
    }

    private void checkIgnoreAndInvalidOptions(VpnProfile profile) throws ParseError {
        for (String option : unsupportedOptions) {
            if (options.containsKey(option))
                throw new ParseError(String.format("Unsupported Option %s encountered in config file. Aborting", option));
        }

        for (String option : ignoreOptions) {
            // removing an item which is not in the map is no error
            options.remove(option);
        }

        boolean customOptions = false;
        for (Vector<Vector<String>> option : options.values()) {
            for (Vector<String> optionsline : option) {
                if (!ignoreThisOption(optionsline)) {
                    customOptions = true;
                }
            }
        }

        if (customOptions) {
            profile.setCustomConfigOptions("# These options found in the config file do not map to config settings:\n"
                + profile.getCustomConfigOptions());

            for (Vector<Vector<String>> option : options.values()) {
                profile.setCustomConfigOptions(profile.getCustomConfigOptions() + getOptionStrings(option));
            }
            profile.setUseCustomConfig(true);
        }
    }

    boolean ignoreThisOption(Vector<String> option) {
        for (String[] ignoreOption : ignoreOptionsWithArg) {
            if (option.size() < ignoreOption.length)
                continue;

            boolean ignore = true;
            for (int i = 0; i < ignoreOption.length; i++) {
                if (!ignoreOption[i].equals(option.get(i))) {
                    ignore = false;
                    break;
                }
            }
            if (ignore)
                return true;
        }
        return false;
    }

    //! Generate options for custom options
    private String getOptionStrings(Vector<Vector<String>> option) {
        String custom = "";
        for (Vector<String> optionsline : option) {
            if (!ignoreThisOption(optionsline)) {
                // Check if option had been inlined and inline again
                if (optionsline.size() == 2 && "extra-certs".equals(optionsline.get(0))) {
                    custom += VpnProfile.insertFileData(optionsline.get(0), optionsline.get(1));
                } else {
                    for (String arg : optionsline)
                        custom += StringUtils.escape(arg) + " ";
                    custom += "\n";
                }
            }
        }
        return custom;
    }

    private Vector<String> getOption(String option, int minarg, int maxarg) throws ParseError {
        Vector<Vector<String>> alloptions = getAllOption(option, minarg, maxarg);
        return alloptions == null ? null : alloptions.lastElement();
    }

    private Vector<Vector<String>> getAllOption(String option, int minarg, int maxarg) throws ParseError {
        Vector<Vector<String>> args = options.get(option);
        if (args == null)
            return null;

        for (Vector<String> optionline : args) {
            if (optionline.size() < (minarg + 1) || optionline.size() > maxarg + 1) {
                String err = String.format(Locale.getDefault(), "Option %s has %d parameters, expected between %d and %d",
                    option, optionline.size() - 1, minarg, maxarg);
                throw new ParseError(err);
            }
        }
        options.remove(option);
        return args;
    }

}
