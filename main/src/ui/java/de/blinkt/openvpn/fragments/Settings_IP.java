/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import de.blinkt.xp.openvpn.R;

public class Settings_IP extends Settings_PreferenceFragment implements Preference.OnPreferenceChangeListener {

    private EditTextPreference mIPv4;
    private EditTextPreference mIPv6;
    private SwitchPreference mUsePull;
    private CheckBoxPreference mOverrideDNS;
    private EditTextPreference mSearchdomain;
    private EditTextPreference mDNS1;
    private EditTextPreference mDNS2;
    private CheckBoxPreference mNobind;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make sure default values are applied.  In a real app, you would
        // want this in a shared function that is used to retrieve the
        // SharedPreferences wherever they are needed.
        PreferenceManager.setDefaultValues(requireActivity(), R.xml.vpn_ipsettings, false);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.vpn_ipsettings);

        mIPv4 = findPreference("ipv4_address");
        mIPv4.setOnPreferenceChangeListener(this);

        mIPv6 = findPreference("ipv6_address");
        mIPv6.setOnPreferenceChangeListener(this);

        mUsePull = findPreference("usePull");
        mUsePull.setOnPreferenceChangeListener(this);

        mOverrideDNS = findPreference("overrideDNS");
        mOverrideDNS.setOnPreferenceChangeListener(this);

        mSearchdomain = findPreference("searchdomain");
        mSearchdomain.setOnPreferenceChangeListener(this);

        mDNS1 = findPreference("dns1");
        mDNS1.setOnPreferenceChangeListener(this);

        mDNS2 = findPreference("dns2");
        mDNS2.setOnPreferenceChangeListener(this);

        mNobind = findPreference("nobind");

        loadPreferences();
    }

    @Override
    protected void loadPreferences() {
        mUsePull.setChecked(mProfile.isUsePull());
        mIPv4.setText(mProfile.getIPv4Address());
        mIPv6.setText(mProfile.getIPv6Address());
        mOverrideDNS.setChecked(mProfile.isOverrideDNS());
        mDNS1.setText(mProfile.getDNS1());
        mDNS2.setText(mProfile.getDNS2());
        mSearchdomain.setText(mProfile.getSearchDomain());
        mNobind.setChecked(mProfile.isNobind());

        // Sets Summary
        onPreferenceChange(mIPv4, mIPv4.getText());
        onPreferenceChange(mIPv6, mIPv6.getText());
        onPreferenceChange(mDNS1, mDNS1.getText());
        onPreferenceChange(mDNS2, mDNS2.getText());
        onPreferenceChange(mSearchdomain, mSearchdomain.getText());

        setDNSState();
    }

    @Override
    protected void savePreferences() {
        mProfile.setUsePull(mUsePull.isChecked());
        mProfile.setIPv4Address(mIPv4.getText());
        mProfile.setIPv6Address(mIPv6.getText());
        mProfile.setOverrideDNS(mOverrideDNS.isChecked());
        mProfile.setDNS1(mDNS1.getText());
        mProfile.setDNS2(mDNS2.getText());
        mProfile.setSearchDomain(mSearchdomain.getText());
        mProfile.setNobind(mNobind.isChecked());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mIPv4 || preference == mIPv6 || preference == mDNS1 || preference == mDNS2
            || preference == mSearchdomain) {
            preference.setSummary((String) newValue);
        }

        if (preference == mUsePull || preference == mOverrideDNS) {
            if (preference == mOverrideDNS) {
                // Set so the function gets the right value
                mOverrideDNS.setChecked((Boolean) newValue);
            }
        }

        setDNSState();
        return true;
    }

    private void setDNSState() {
        boolean enabled;
        mOverrideDNS.setEnabled(mUsePull.isChecked());
        if (!mUsePull.isChecked())
            enabled = true;
        else
            enabled = mOverrideDNS.isChecked();

        mDNS1.setEnabled(enabled);
        mDNS2.setEnabled(enabled);
        mSearchdomain.setEnabled(enabled);
    }

}
