/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;

import de.blinkt.xp.openvpn.R;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

public class NetworkUtils {

    public static List<String> getLocalNetworks(@NonNull Context c, boolean ipv6) {
        ConnectivityManager conn = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = conn.getAllNetworks();

        List<String> nets = new Vector<>();
        for (Network network : networks) {
            NetworkCapabilities nc = conn.getNetworkCapabilities(network);
            LinkProperties li = conn.getLinkProperties(network);

            // Skip VPN networks like ourselves
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                continue;

            // Also skip mobile networks
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                continue;

            for (LinkAddress la : li.getLinkAddresses()) {
                if ((la.getAddress() instanceof Inet4Address && !ipv6) ||
                    (la.getAddress() instanceof Inet6Address && ipv6))
                    // 2001:db8::1/64 or 192.0.2.1/24
                    nets.add(la.toString());
            }
        }

        return nets;
    }

    // From: http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
    public static String humanReadableByteCount(long bytes, boolean speed, @NonNull Resources res) {
        if (speed)
            bytes = bytes * 8;
        int unit = speed ? 1000 : 1024;

        int exp = Math.max(0, Math.min((int) (Math.log(bytes) / Math.log(unit)), 3));

        float bytesUnit = (float) (bytes / Math.pow(unit, exp));

        if (speed)
            switch (exp) {
                case 0:
                    return res.getString(R.string.bits_per_second, bytesUnit);
                case 1:
                    return res.getString(R.string.kbits_per_second, bytesUnit);
                case 2:
                    return res.getString(R.string.mbits_per_second, bytesUnit);
                default:
                    return res.getString(R.string.gbits_per_second, bytesUnit);
            }
        else
            switch (exp) {
                case 0:
                    return res.getString(R.string.volume_byte, bytesUnit);
                case 1:
                    return res.getString(R.string.volume_kbyte, bytesUnit);
                case 2:
                    return res.getString(R.string.volume_mbyte, bytesUnit);
                default:
                    return res.getString(R.string.volume_gbyte, bytesUnit);
            }
    }

    public static String cidrToIPAndNetmask(@NonNull String route) {
        String[] parts = route.trim().split("/");

        // No /xx, assume /32 as netmask
        if (parts.length == 1)
            parts = (route + "/32").split("/");

        if (parts.length != 2)
            return null;

        int len;
        try {
            len = Integer.parseInt(parts[1]);
            if (len < 0 || len > 32)
                return null;

        } catch (NumberFormatException ne) {
            return null;
        }

        long nm = 0xffffffffL;
        nm = (nm << (32 - len)) & 0xffffffffL;

        String netmask = String.format(Locale.US, "%d.%d.%d.%d",
            (nm & 0xff000000) >> 24, (nm & 0xff0000) >> 16, (nm & 0xff00) >> 8, nm & 0xff);
        return parts[0] + " " + netmask;
    }

    public static long getIntAddress(@NonNull String ipaddr) {
        String[] ipt = ipaddr.split("\\.");
        long ip = 0L;

        ip += Long.parseLong(ipt[0]) << 24;
        ip += Integer.parseInt(ipt[1]) << 16;
        ip += Integer.parseInt(ipt[2]) << 8;
        ip += Integer.parseInt(ipt[3]);
        return ip;
    }

    public static int calculateMaskLengeh(@NonNull String mask) {
        long netmask = getIntAddress(mask);
        // Add 33. bit to ensure the loop terminates
        netmask += 1l << 32;

        int lenZeros = 0;
        while ((netmask & 0x1) == 0) {
            lenZeros++;
            netmask = netmask >> 1;
        }

        int len;
        // Check if rest of netmask is only 1s
        if (netmask != (0x1ffffffffl >> lenZeros)) {
            // Asume no CIDR, set /32
            len = 32;
        } else {
            len = 32 - lenZeros;
        }
        return len;
    }

    /**
     * 判断是否在该地址范围内
     */
    public static boolean ipInRange(@NonNull String ip, @NonNull String ipSection) {
        ipSection = ipSection.trim();
        ip = ip.trim();
        int idx = ipSection.indexOf('-');
        String beginIP = ipSection.substring(0, idx);
        String endIP = ipSection.substring(idx + 1);
        return getIntAddress(beginIP) <= getIntAddress(ip) && getIntAddress(ip) <= getIntAddress(endIP);
    }

    public static boolean isWifiEnabled(@NonNull Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo == null ? 0 : wifiInfo.getIpAddress();
        return wifiManager.isWifiEnabled() && ipAddress != 0;
    }

}
