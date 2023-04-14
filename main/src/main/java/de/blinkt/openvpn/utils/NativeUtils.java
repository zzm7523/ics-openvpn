/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import android.os.Build;

import java.security.InvalidKeyException;

public class NativeUtils {

    public static native byte[] rsasign(byte[] input, int pkey, boolean pkcs1padding) throws InvalidKeyException;

    public static native String[] getIfconfig() throws IllegalArgumentException;

    static native void jniclose(int fdint);

    public static String getNativeAPI() {
        if (isRoboUnitTest())
            return "ROBO";
        else
            return getJNIAPI();
    }

    private static native String getJNIAPI();

    public static native String getOpenVPNGitVersion();

    static {
        if (!isRoboUnitTest()) {
            System.loadLibrary("opvpnutil");
        }
    }

    public static boolean isRoboUnitTest() {
        return "robolectric".equals(Build.FINGERPRINT);
    }

}
