/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import de.blinkt.xp.openvpn.R;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ProfileManager;

public abstract class Settings_PreferenceFragment extends PreferenceFragmentCompat {

    protected VpnProfile mProfile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String profileUUID = getArguments().getString(VpnProfile.EXTRA_PROFILE_UUID);
        mProfile = ProfileManager.getInstance(getActivity()).get(getActivity(), profileUUID);
        getActivity().setTitle(getString(R.string.edit_profile_title, mProfile.getName()));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    }

    // 不需要重置onResume()，调用loadPreferences()
    /*
    @Override
    public void onResume() {
        super.onResume();
        loadPreferences();
    }
    */

    @Override
    public void onPause() {
        super.onPause();
        savePreferences();
    }

    protected abstract void loadPreferences();

    protected abstract void savePreferences();

}
