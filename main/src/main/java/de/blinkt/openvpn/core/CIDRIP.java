/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import androidx.annotation.NonNull;

import java.util.Locale;

import de.blinkt.openvpn.utils.NetworkUtils;

class CIDRIP {

    String ip;
    int len;

    public CIDRIP(@NonNull String ip, @NonNull String mask) {
        this.ip = ip;
        this.len = NetworkUtils.calculateMaskLengeh(mask);
    }

    public CIDRIP(@NonNull String address, int prefix_length) {
        ip = address;
        len = prefix_length;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s/%d", ip, len);
    }

    public boolean normalise() {
        long addr = NetworkUtils.getIntAddress(this.ip);
        long normalAddr = addr & (0xffffffffL << (32 - len));
        if (normalAddr != addr) {
            this.ip = String.format(Locale.US, "%d.%d.%d.%d",
                (normalAddr & 0xff000000) >> 24, (normalAddr & 0xff0000) >> 16, (normalAddr & 0xff00) >> 8, normalAddr & 0xff);
            return true;
        } else {
            return false;
        }
    }

    public long getInt() {
        return NetworkUtils.getIntAddress(ip);
    }

}
