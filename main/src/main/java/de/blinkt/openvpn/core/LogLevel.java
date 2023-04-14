/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

public enum LogLevel {

    ERROR(4),   // M_FATAL
    WARNING(3), // M_NONFATAL, M_WARN
    INFO(2),    // M_INFO
    DEBUG(1),   // M_DEBUG
    VERBOSE(0);

    private int mValue;

    LogLevel(int value) {
        mValue = value;
    }

    public int getInt() {
        return mValue;
    }

    public static LogLevel getEnumByValue(int value) {
        switch (value) {
            case 4:
                return ERROR;
            case 3:
                return WARNING;
            case 2:
                return INFO;
            case 1:
                return DEBUG;
            case 0:
                return VERBOSE;
            default:
                assert false : "Not a valid log level";
                return DEBUG;
        }
    }

}
