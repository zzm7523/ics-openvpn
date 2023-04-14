/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import de.blinkt.xp.openvpn.R;


public class Settings_Routing extends Settings_PreferenceFragment implements Preference.OnPreferenceChangeListener {

    private EditTextPreference mCustomRoutes;
    private CheckBoxPreference mUseDefaultRoute;
    private EditTextPreference mCustomRoutesv6;
    private CheckBoxPreference mUseDefaultRoutev6;
    private CheckBoxPreference mRouteNoPull;
    private CheckBoxPreference mLocalVPNAccess;
    private EditTextPreference mExcludedRoutes;
    private EditTextPreference mExcludedRoutesv6;
    private CheckBoxPreference mBlockUnusedAF;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.vpn_routing);

        mCustomRoutes = findPreference("customRoutes");
        mUseDefaultRoute = findPreference("useDefaultRoute");
        mCustomRoutesv6 = findPreference("customRoutesv6");
        mUseDefaultRoutev6 = findPreference("useDefaultRoutev6");
        mExcludedRoutes = findPreference("excludedRoutes");
        mExcludedRoutesv6 = findPreference("excludedRoutesv6");

        mRouteNoPull = findPreference("routenopull");
        mLocalVPNAccess = findPreference("unblockLocal");

        mBlockUnusedAF = findPreference("blockUnusedAF");

        mCustomRoutes.setOnPreferenceChangeListener(this);
        mCustomRoutesv6.setOnPreferenceChangeListener(this);
        mExcludedRoutes.setOnPreferenceChangeListener(this);
        mExcludedRoutesv6.setOnPreferenceChangeListener(this);
        mBlockUnusedAF.setOnPreferenceChangeListener(this);

        loadPreferences();
    }

    @Override
    protected void loadPreferences() {
        mRouteNoPull.setChecked(mProfile.isRoutenopull());
        mLocalVPNAccess.setChecked(mProfile.isAllowLocalLAN());
        mBlockUnusedAF.setChecked(mProfile.isBlockUnusedAddressFamilies());

        mUseDefaultRoute.setChecked(mProfile.isUseDefaultRoute());
        mCustomRoutes.setText(mProfile.getCustomRoutes());
        mExcludedRoutes.setText(mProfile.getExcludedRoutes());

        mUseDefaultRoutev6.setChecked(mProfile.isUseDefaultRoutev6());
        mCustomRoutesv6.setText(mProfile.getCustomRoutesv6());
        mExcludedRoutesv6.setText(mProfile.getExcludedRoutesv6());

        // Sets Summary
        onPreferenceChange(mCustomRoutes, mCustomRoutes.getText());
        onPreferenceChange(mCustomRoutesv6, mCustomRoutesv6.getText());
        onPreferenceChange(mExcludedRoutes, mExcludedRoutes.getText());
        onPreferenceChange(mExcludedRoutesv6, mExcludedRoutesv6.getText());
    }

    @Override
    protected void savePreferences() {
        mProfile.setRoutenopull(mRouteNoPull.isChecked());
        mProfile.setAllowLocalLAN(mLocalVPNAccess.isChecked());
        mProfile.setBlockUnusedAddressFamilies(mBlockUnusedAF.isChecked());

        mProfile.setUseDefaultRoute(mUseDefaultRoute.isChecked());
        mProfile.setCustomRoutes(mCustomRoutes.getText());
        mProfile.setExcludedRoutes(mExcludedRoutes.getText());

        mProfile.setUseDefaultRoutev6(mUseDefaultRoutev6.isChecked());
        mProfile.setCustomRoutesv6(mCustomRoutesv6.getText());
        mProfile.setExcludedRoutesv6(mExcludedRoutesv6.getText());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mCustomRoutes || preference == mCustomRoutesv6
            || preference == mExcludedRoutes || preference == mExcludedRoutesv6) {
            preference.setSummary((String) newValue);
        }
        return true;
    }

}
