/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Created by arne on 08.01.17.
 */

// Until I find a good solution

public class Preferences {

    public static SharedPreferences getDefaultSharedPreferences(@NonNull Context c) {
        return c.getSharedPreferences(c.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
    }

    public static SharedPreferences getProfileSharedPreferences(@NonNull Context c) {
        return c.getSharedPreferences("profile_preferences", Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
    }

    public static SharedPreferences getVariableSharedPreferences(@NonNull Context c) {
        return c.getSharedPreferences("variable_preferences", Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
    }

}
