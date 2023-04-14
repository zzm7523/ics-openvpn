/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class VpnTunnel implements Parcelable {

    private long establishedTime;

    private String tlsVersion;
    private String tlsCipher;

    private String cipher;
    private String auth;

    private String virtualIPv4Gateway;
    private String virtualIPv6Gateway;

    private String virtualIPv4Addr;
    private String virtualIPv6Addr;

    public VpnTunnel() {
    }

    public VpnTunnel(@NonNull VpnTunnel other) {
        establishedTime = other.establishedTime;
        tlsVersion = other.tlsVersion;
        tlsCipher = other.tlsCipher;
        cipher = other.cipher;
        auth = other.auth;
        virtualIPv4Gateway = other.virtualIPv4Gateway;
        virtualIPv6Gateway = other.virtualIPv6Gateway;
        virtualIPv4Addr = other.virtualIPv4Addr;
        virtualIPv6Addr = other.virtualIPv6Addr;
    }

    public VpnTunnel(@NonNull Parcel in) {
        establishedTime = in.readLong();
        tlsVersion = in.readString();
        tlsCipher = in.readString();
        cipher = in.readString();
        auth = in.readString();
        virtualIPv4Gateway = in.readString();
        virtualIPv6Gateway = in.readString();
        virtualIPv4Addr = in.readString();
        virtualIPv6Addr = in.readString();
    }

    public void copyFrom(@NonNull VpnTunnel other) {
        establishedTime = other.establishedTime;
        tlsVersion = other.tlsVersion;
        tlsCipher = other.tlsCipher;
        cipher = other.cipher;
        auth = other.auth;
        virtualIPv4Gateway = other.virtualIPv4Gateway;
        virtualIPv6Gateway = other.virtualIPv6Gateway;
        virtualIPv4Addr = other.virtualIPv4Addr;
        virtualIPv6Addr = other.virtualIPv6Addr;
    }

    public void clearDefaults() {
        establishedTime = 0L;
        tlsVersion = null;
        tlsCipher = null;
        cipher = null;
        auth = null;
        virtualIPv4Gateway = null;
        virtualIPv6Gateway = null;
        virtualIPv4Addr = null;
        virtualIPv6Addr = null;
    }

    public long getEstablishedTime() {
        return establishedTime;
    }

    public void setEstablishedTime(long establishedTime) {
        this.establishedTime = establishedTime;
    }

    public String getTlsVersion() {
        return tlsVersion;
    }

    public void setTlsVersion(String tlsVersion) {
        this.tlsVersion = tlsVersion;
    }

    public String getTlsCipher() {
        return tlsCipher;
    }

    public void setTlsCipher(String tlsCipher) {
        this.tlsCipher = tlsCipher;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getVirtualIPv4Gateway() {
        return virtualIPv4Gateway;
    }

    public void setVirtualIPv4Gateway(String virtualIPv4Gateway) {
        this.virtualIPv4Gateway = virtualIPv4Gateway;
    }

    public String getVirtualIPv6Gateway() {
        return virtualIPv6Gateway;
    }

    public void setVirtualIPv6Gateway(String virtualIPv6Gateway) {
        this.virtualIPv6Gateway = virtualIPv6Gateway;
    }

    public String getVirtualIPv4Addr() {
        return virtualIPv4Addr;
    }

    public void setVirtualIPv4Addr(String virtualIPv4Addr) {
        this.virtualIPv4Addr = virtualIPv4Addr;
    }

    public String getVirtualIPv6Addr() {
        return virtualIPv6Addr;
    }

    public void setVirtualIPv6Addr(String virtualIPv6Addr) {
        this.virtualIPv6Addr = virtualIPv6Addr;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(establishedTime);
        dest.writeString(tlsVersion);
        dest.writeString(tlsCipher);
        dest.writeString(cipher);
        dest.writeString(auth);
        dest.writeString(virtualIPv4Gateway);
        dest.writeString(virtualIPv6Gateway);
        dest.writeString(virtualIPv4Addr);
        dest.writeString(virtualIPv6Addr);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<VpnTunnel> CREATOR = new Creator<VpnTunnel>() {
        @Override
        public VpnTunnel createFromParcel(Parcel in) {
            return new VpnTunnel(in);
        }

        @Override
        public VpnTunnel[] newArray(int size) {
            return new VpnTunnel[size];
        }
    };

}
