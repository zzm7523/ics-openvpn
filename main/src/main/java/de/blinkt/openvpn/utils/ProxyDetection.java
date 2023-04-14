/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import androidx.annotation.NonNull;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.Connection;
import de.blinkt.openvpn.core.VpnStatus;

public class ProxyDetection {

    public static SocketAddress detectProxy(@NonNull VpnProfile profile) {
        Proxy proxy = null;

        try {
            // Construct a new url with https as protocol
            for (Connection conn : profile.getConnections()) {
                URL url = new URL(String.format("https://%s:%s", conn.getServerName(), conn.getServerPort()));
                proxy = getFirstProxy(url);
                if (proxy != null)
                    break;
            }

            if (proxy != null) {
                SocketAddress addr = proxy.address();
                if (addr instanceof InetSocketAddress) {
                    return addr;
                }
            }

        } catch (MalformedURLException e) {
            VpnStatus.logError(R.string.getproxy_error, e.getLocalizedMessage());
        } catch (URISyntaxException e) {
            VpnStatus.logError(R.string.getproxy_error, e.getLocalizedMessage());
        }

        return null;
    }

    public static Proxy getFirstProxy(@NonNull URL url) throws URISyntaxException {
        System.setProperty("java.net.useSystemProxies", "true");
        List<Proxy> proxies = ProxySelector.getDefault().select(url.toURI());

        if (proxies != null) {
            for (Proxy proxy : proxies) {
                SocketAddress addr = proxy.address();
                if (addr != null) {
                    return proxy;
                }
            }

        }

        return null;
    }

}
