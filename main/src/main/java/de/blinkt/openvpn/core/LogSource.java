/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public enum LogSource implements Parcelable {

    OPENVPN_FRONT(0),
    OPENVPN_CONSOLE(1),
    OPENVPN_MANAGEMENT(2);

    private int mValue;

    LogSource(int value) {
        mValue = value;
    }

    public int getInt() {
        return mValue;
    }

    public static LogSource getEnumByValue(int value) {
        switch (value) {
            case 0:
                return OPENVPN_FRONT;
            case 1:
                return OPENVPN_CONSOLE;
            case 2:
                return OPENVPN_MANAGEMENT;
            default:
                assert false : "Not a valid log source";
                return OPENVPN_FRONT;
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<LogSource> CREATOR = new Creator<LogSource>() {
        @Override
        public LogSource createFromParcel(Parcel in) {
            return LogSource.values()[in.readInt()];
        }

        @Override
        public LogSource[] newArray(int size) {
            return new LogSource[size];
        }

    };

}
