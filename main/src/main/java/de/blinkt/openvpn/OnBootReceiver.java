/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;


public class OnBootReceiver extends BroadcastReceiver {

    // Debug: am broadcast -a android.intent.action.BOOT_COMPLETED
    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);
        boolean useStartOnBoot = prefs.getBoolean("restart_vpn_onboot", false);
        if (!useStartOnBoot)
            return;

        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            VpnProfile bootProfile = ProfileManager.getInstance(context).getAlwaysOnVPN(context);
            if (bootProfile != null) {
                launchVPN(bootProfile, context);
            }
        }
    }

    void launchVPN(@NonNull VpnProfile profile, @NonNull Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(context, LaunchOpenVPN.class);
        intent.putExtra(LaunchOpenVPN.EXTRA_KEY, profile.getUUIDString());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LaunchOpenVPN.EXTRA_HIDELOG, true);
        context.startActivity(intent);
    }

}
