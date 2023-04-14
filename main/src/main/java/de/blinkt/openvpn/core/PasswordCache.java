/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by arne on 15.12.16.
 */

public class PasswordCache {

    public static final int PROTECT_PASSWORD = 2;
    public static final int AUTH_PASSWORD    = 3;

    private static final Map<UUID, PasswordCache> mInstances = new HashMap<>();

    private String mProtectPassword;
    private String mAuthPassword;

    public static PasswordCache getInstance(@NonNull UUID uuid) {
        PasswordCache instance = mInstances.get(uuid);
        if (instance == null) {
            instance = new PasswordCache();
            mInstances.put(uuid, instance);
        }
        return instance;
    }

    public static String getProtectPassword(@NonNull UUID uuid, boolean resetPw) {
        String pwcopy = getInstance(uuid).mProtectPassword;
        if (resetPw)
            getInstance(uuid).mProtectPassword = null;
        return pwcopy;
    }

    public static String getAuthPassword(@NonNull UUID uuid, boolean resetPW) {
        String pwcopy = getInstance(uuid).mAuthPassword;
        if (resetPW)
            getInstance(uuid).mAuthPassword = null;
        return pwcopy;
    }

    public static void setCachedPassword(@NonNull String uuid, int type, String password) {
        PasswordCache instance = getInstance(UUID.fromString(uuid));
        switch (type) {
            case PROTECT_PASSWORD:
                instance.mProtectPassword = password;
                break;
            case AUTH_PASSWORD:
                instance.mAuthPassword = password;
                break;
        }
    }

}
