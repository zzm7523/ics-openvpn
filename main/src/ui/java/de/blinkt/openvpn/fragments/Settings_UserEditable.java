/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.api.AppRestrictions;

public class Settings_UserEditable extends KeyChainSettingsFragment implements View.OnClickListener {

    private View mView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.settings_usereditable, container, false);
        TextView messageView = mView.findViewById(R.id.messageUserEdit);
        messageView.setText(getString(R.string.message_no_user_edit, getPackageString(mProfile.getProfileCreator())));
        initKeychainViews(this.mView);
        mView.findViewById(R.id.unchangeable_layout).setVisibility(View.GONE);
        return mView;
    }

    private String getPackageString(String packageName) {
        if (AppRestrictions.PROFILE_CREATOR.equals(packageName)) {
            return "Android Enterprise Management";
        }

        PackageManager pm = getActivity().getPackageManager();
        ApplicationInfo appInfo;
        try {
            appInfo = pm.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException ex) {
            appInfo = null;
        }

        String applicationName = (String) (appInfo != null ? pm.getApplicationLabel(appInfo) : "(unknown)");
        return String.format("%s (%s)", applicationName, packageName);
    }

    @Override
    protected void savePreferences() {

    }

    @Override
    public void onResume() {
        super.onResume();
        mView.findViewById(R.id.keystore).setVisibility(View.GONE);
        if (mProfile.getAuthenticationType() == VpnProfile.TYPE_USERPASS_KEYSTORE ||
            mProfile.getAuthenticationType() == VpnProfile.TYPE_KEYSTORE) {
            mView.findViewById(R.id.keystore).setVisibility(View.VISIBLE);
        }
    }

}
