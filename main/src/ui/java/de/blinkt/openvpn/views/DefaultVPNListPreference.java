/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

import java.util.Collection;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ProfileManager;

public class DefaultVPNListPreference extends ListPreference {

    public DefaultVPNListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVPNs(context);
    }

    private void setVPNs(Context c) {
        ProfileManager profileManager = ProfileManager.getInstance(c);
        Collection<VpnProfile> profiles = profileManager.getProfiles();
        CharSequence[] entries = new CharSequence[profiles.size()];
        CharSequence[] entryValues = new CharSequence[profiles.size()];

        int i = 0;
        for (VpnProfile profile : profiles) {
            entries[i] = profile.getName();
            entryValues[i] = profile.getUUIDString();
            i++;
        }

        setEntries(entries);
        setEntryValues(entryValues);
    }

}
