/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ProfileManager;

public abstract class Settings_Fragment extends Fragment {

    protected String mProfileUUID;
    protected VpnProfile mProfile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProfileUUID = getArguments().getString(VpnProfile.EXTRA_PROFILE_UUID);
        mProfile = ProfileManager.getInstance(getActivity()).get(getActivity(), mProfileUUID);
        getActivity().setTitle(getString(R.string.edit_profile_title, mProfile.getName()));
    }

    @Override
    public void onPause() {
        super.onPause();
        savePreferences();
    }

    protected abstract void loadPreferences();

    protected abstract void savePreferences();

}
