/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import de.blinkt.xp.openvpn.R;

import java.util.Locale;
import java.util.Objects;

/**
 * Created by arne on 08.11.16.
 */
public class ConnectionStatus implements Parcelable {

    public final static int LEVEL_CONNECTED = 1;
    public final static int LEVEL_VPNPAUSED = 2;
    public final static int LEVEL_CONNECTING_SERVER_REPLIED = 3;
    public final static int LEVEL_CONNECTING_NO_SERVER_REPLY_YET = 4;
    public final static int LEVEL_NONETWORK = 5;
    public final static int LEVEL_NOTCONNECTED = 6;
    public final static int LEVEL_START = 7;
    public final static int LEVEL_WAITING_FOR_USER_INPUT = 8;
    public final static int LEVEL_GENERAL_ERROR = 9;
    public final static int LEVEL_AUTH_FAILED = 10;
    public final static int LEVEL_UNKNOWN = 11;

    private String status;
    private String message;
    private int resid;

    public ConnectionStatus(@NonNull String status, String message, int resid) {
        this.status = status;
        this.message = message;
        this.resid = resid;
    }

    public ConnectionStatus(@NonNull String status, int resid) {
        this.status = status;
        this.resid = resid;
    }

    public ConnectionStatus(@NonNull ConnectionStatus other) {
        status = other.status;
        message = other.message;
        resid = other.resid;
    }

    public ConnectionStatus(@NonNull Parcel in) {
        status = in.readString();
        message = in.readString();
        resid = in.readInt();
    }

    public int getLocalizedStatus() {
        return ConnectionStatus.getLocalizedStatus(status);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getResid() {
        return resid;
    }

    public void setResid(int resid) {
        this.resid = resid;
    }

    public String getString(@NonNull Context c) {
        if (getLevel() == ConnectionStatus.LEVEL_CONNECTED && !TextUtils.isEmpty(message)) {
            message = message.replaceFirst("SUCCESS[\\x20|\\t]*,", "");
            String[] parts = message.split(",");
            /*
             (a) the integer unix date/time,
             (b) the state name,
             0 (c) optional descriptive string (used mostly on RECONNECTING and EXITING to show the reason for the disconnect),
             1 (d) optional TUN/TAP local IPv4 address
             2 (e) optional address of remote server,
             3 (f) optional port of remote server,
             4 (g) optional local address,
             5 (h) optional local port, and
             6 (i) optional TUN/TAP local IPv6 address.
            */
            // Return only the assigned IP addresses in the UI
            if (parts.length >= 7)
                message = String.format(Locale.US, "%s %s", parts[1], parts[6]);
        }

        if (!TextUtils.isEmpty(message)) {
            while (message.endsWith(","))
                message = message.substring(0, message.length() - 1);
        }

        if (TextUtils.equals(status, "NOPROCESS") && !TextUtils.isEmpty(message)) {
            return message;
        }

        if (resid == R.string.state_waitconnectretry) {
            return c.getString(R.string.state_waitconnectretry, message);
        }

        if (resid == R.string.unknown_state) {
            message = getStatus() + (TextUtils.isEmpty(message) ? "" : message);
        }

        return c.getString(resid) + (TextUtils.isEmpty(message) ? "" : ": " + message);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(status);
        dest.writeString(message);
        dest.writeInt(resid);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ConnectionStatus> CREATOR = new Creator<ConnectionStatus>() {

        @Override
        public ConnectionStatus createFromParcel(Parcel in) {
            return new ConnectionStatus(in);
        }

        @Override
        public ConnectionStatus[] newArray(int size) {
            return new ConnectionStatus[size];
        }

    };

    public String getLevelString() {
        int level = getLevel();
        return ConnectionStatus.getLevelString(level);
    }

    public int getLevel() {
        return ConnectionStatus.getLevel(this);
    }

    public static String getLevelString(int level) {
        switch (level) {
            case LEVEL_CONNECTED:
                return "CONNECTED";
            case LEVEL_VPNPAUSED:
                return "VPNPAUSED";
            case LEVEL_CONNECTING_SERVER_REPLIED:
                return "CONNECTING_SERVER_REPLIED";
            case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
                return "CONNECTING_NO_SERVER_REPLY_YET";
            case LEVEL_NONETWORK:
                return "NONETWORK";
            case LEVEL_NOTCONNECTED:
                return "NOTCONNECTED";
            case LEVEL_START:
                return "START";
            case LEVEL_WAITING_FOR_USER_INPUT:
                return "WAITING_FOR_USER_INPUT";
            case LEVEL_GENERAL_ERROR:
                return "GENERAL_ERROR";
            case LEVEL_AUTH_FAILED:
                return "AUTH_FAILED";
            default:
                return "UNKNOWN";
        }
    }

    public static int getLevel(@NonNull ConnectionStatus status) {
        return ConnectionStatus.getLevel(status.status);
    }

    public static int getLevel(@NonNull String status) {
        final String[] noreplyet = {"CONNECTING", "WAIT", "RECONNECTING", "RESOLVE", "TCP_CONNECT"};
        final String[] reply = {"AUTH", "GET_CONFIG", "ASSIGN_IP", "ADD_ROUTES", "AUTH_PENDING"};
        final String[] connected = {"CONNECTED"};
        final String[] notconnected = {"DISCONNECTED", "EXITING"};

        // OpenVPN state -> level
        for (String x : noreplyet) {
            if (status.equals(x))
                return ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET;
        }

        for (String x : reply) {
            if (status.equals(x))
                return ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED;
        }

        for (String x : connected) {
            if (status.equals(x))
                return ConnectionStatus.LEVEL_CONNECTED;
        }

        for (String x : notconnected) {
            if (status.equals(x))
                return ConnectionStatus.LEVEL_NOTCONNECTED;
        }

        // expanded state -> level
        if ("USER_VPN_PASSWORD_CANCELLED".equals(status))
            return ConnectionStatus.LEVEL_NOTCONNECTED;

        if ("USER_VPN_PERMISSION".equals(status))
            return ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT;

        if ("USER_VPN_PASSWORD".equals(status))
            return ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT;

        if ("USER_VPN_PERMISSION_CANCELLED".equals(status))
            return ConnectionStatus.LEVEL_NOTCONNECTED;

        if ("NONETWORK".equals(status))
            return ConnectionStatus.LEVEL_NONETWORK;

        if ("SCREENOFF".equals(status))
            return ConnectionStatus.LEVEL_VPNPAUSED;

        if ("USERPAUSE".equals(status))
            return ConnectionStatus.LEVEL_VPNPAUSED;

        if ("NOPROCESS".equals(status))
            return ConnectionStatus.LEVEL_NOTCONNECTED;

        if ("VPN_GENERATE_CONFIG".equals(status))
            return ConnectionStatus.LEVEL_START;

        if ("NEED".equals(status))
            return ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT;

        if ("CONNECTRETRY".equals(status))
            return ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET;

        if ("USER_INPUT".equals(status))
            return ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT;

        if ("GENERAL_ERROR".equals(status))
            return ConnectionStatus.LEVEL_GENERAL_ERROR;

        if ("AUTH_FAILED".equals(status))
            return ConnectionStatus.LEVEL_AUTH_FAILED;

        return ConnectionStatus.LEVEL_UNKNOWN;
    }

    public static int getLocalizedStatus(@NonNull String status) {
        switch (status) {
            case "CONNECTING":
                return R.string.state_connecting;
            case "WAIT":
                return R.string.state_wait;
            case "AUTH":
                return R.string.state_auth;
            case "GET_CONFIG":
                return R.string.state_get_config;
            case "ASSIGN_IP":
                return R.string.state_assign_ip;
            case "ADD_ROUTES":
                return R.string.state_add_routes;
            case "CONNECTED":
                return R.string.state_connected;
            case "DISCONNECTED":
                return R.string.state_disconnected;
            case "RECONNECTING":
                return R.string.state_reconnecting;
            case "EXITING":
                // OpenVPN进入EXITING状态后, 就没有后续状态了 (如果这里返回R.string.state_exiting, 可能导致UI一直显示"正在断开连接")
//              return R.string.state_exiting;
                return R.string.state_noprocess;
            case "RESOLVE":
                return R.string.state_resolve;
            case "TCP_CONNECT":
                return R.string.state_tcp_connect;
            case "AUTH_PENDING":
                return R.string.state_auth_pending;

                // 扩展
            case "VPN_GENERATE_CONFIG":
                return R.string.building_configration;
            default:
                return R.string.unknown_state;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionStatus that = (ConnectionStatus) o;
        return resid == that.resid && Objects.equals(status, that.status) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, message, resid);
    }

}
