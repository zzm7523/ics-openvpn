/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import java.util.Locale;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.VpnProfile;

public class Settings_Obscure extends Settings_PreferenceFragment implements Preference.OnPreferenceChangeListener {

    private CheckBoxPreference mPersistent;
    private CheckBoxPreference mUseRandomHostName;
    private CheckBoxPreference mUseFloat;
    private CheckBoxPreference mUseCustomConfig;
    private EditTextPreference mCustomConfig;
    private EditTextPreference mMssFixValue;
    private CheckBoxPreference mMssFixCheckBox;

    private ListPreference mConnectRetrymax;
    private EditTextPreference mConnectRetry;
    private EditTextPreference mConnectRetryMaxTime;
    private EditTextPreference mTunMtu;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.vpn_obscure);

        mPersistent = findPreference("usePersistTun");
        mUseRandomHostName = findPreference("useRandomHostname");
        mUseFloat = findPreference("useFloat");
        mUseCustomConfig = findPreference("enableCustomOptions");
        mCustomConfig = findPreference("customOptions");
        mMssFixCheckBox = findPreference("mssFix");

        mMssFixValue = findPreference("mssFixValue");
        mMssFixValue.setOnPreferenceChangeListener(this);
        mTunMtu = findPreference("tunmtu");
        mTunMtu.setOnPreferenceChangeListener(this);

        mConnectRetrymax = findPreference("connectretrymax");
        mConnectRetrymax.setOnPreferenceChangeListener(this);
        mConnectRetry = findPreference("connectretry");
        mConnectRetry.setOnPreferenceChangeListener(this);
        mConnectRetryMaxTime = findPreference("connectretrymaxtime");
        mConnectRetryMaxTime.setOnPreferenceChangeListener(this);

        loadPreferences();
    }

    protected void loadPreferences() {
        mPersistent.setChecked(mProfile.isPersistTun());
        mUseRandomHostName.setChecked(mProfile.isUseRandomHostname());
        mUseFloat.setChecked(mProfile.isUseFloat());
        mUseCustomConfig.setChecked(mProfile.isUseCustomConfig());
        mCustomConfig.setText(mProfile.getCustomConfigOptions());

        if (mProfile.getMssFix() == 0) {
            mMssFixCheckBox.setChecked(false);
            mMssFixValue.setText(String.valueOf(VpnProfile.DEFAULT_MSSFIX_SIZE));
            mMssFixValue.setSummary(String.valueOf(VpnProfile.DEFAULT_MSSFIX_SIZE));
        } else {
            mMssFixCheckBox.setChecked(true);
            mMssFixValue.setText(String.valueOf(mProfile.getMssFix()));
            mMssFixValue.setSummary(String.valueOf(mProfile.getMssFix()));
        }

        int tunmtu = mProfile.getTunMtu();
        if (tunmtu < 48)
            tunmtu = 1500;
        mTunMtu.setText(String.valueOf(tunmtu));
        mTunMtu.setSummary(String.valueOf(tunmtu));

        mConnectRetrymax.setValue(mProfile.getConnectRetryMax());
        onPreferenceChange(mConnectRetrymax, mProfile.getConnectRetryMax());

        mConnectRetry.setText(mProfile.getConnectRetry());
        onPreferenceChange(mConnectRetry, mProfile.getConnectRetry());

        mConnectRetryMaxTime.setText(mProfile.getConnectRetryMaxTime());
        onPreferenceChange(mConnectRetryMaxTime, mProfile.getConnectRetryMaxTime());
    }

    protected void savePreferences() {
        mProfile.setPersistTun(mPersistent.isChecked());
        mProfile.setUseRandomHostname(mUseRandomHostName.isChecked());
        mProfile.setUseFloat(mUseFloat.isChecked());
        mProfile.setUseCustomConfig(mUseCustomConfig.isChecked());
        mProfile.setCustomConfigOptions(mCustomConfig.getText());
        if (mMssFixCheckBox.isChecked())
            mProfile.setMssFix(Integer.parseInt(mMssFixValue.getText()));
        else
            mProfile.setMssFix(0);
        mProfile.setTunMtu(Integer.parseInt(mTunMtu.getText()));

        mProfile.setConnectRetryMax(mConnectRetrymax.getValue());
        mProfile.setConnectRetry(mConnectRetry.getText());
        mProfile.setConnectRetryMaxTime(mConnectRetryMaxTime.getText());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mMssFixValue) {
            try {
                int v = Integer.parseInt((String) newValue);
                if (v < 0 || v > 9000)
                    throw new NumberFormatException("mssfix value");
                mMssFixValue.setSummary(String.valueOf(v));
            } catch (NumberFormatException ex) {
                Toast.makeText(getActivity(), R.string.mssfix_invalid_value, Toast.LENGTH_LONG).show();
                return false;
            }

        } else if (preference == mTunMtu) {
            try {
                int v = Integer.parseInt((String) newValue);
                if (v < 48 || v > 9000) {
                    throw new NumberFormatException("mtu value");
                } else {
                    mTunMtu.setSummary(String.valueOf(v));
                }
            } catch (NumberFormatException ex) {
                Toast.makeText(getActivity(), R.string.mtu_invalid_value, Toast.LENGTH_LONG).show();
                return false;
            }

        } else if (preference == mConnectRetrymax) {
            if (newValue == null || newValue == "")
                newValue = "5";
            mConnectRetrymax.setDefaultValue(newValue);

            for (int i = 0; i < mConnectRetrymax.getEntryValues().length; i++) {
                if (mConnectRetrymax.getEntryValues()[i].equals(newValue)) {
                    mConnectRetrymax.setSummary(mConnectRetrymax.getEntries()[i]);
                    break;
                }
            }

        } else if (preference == mConnectRetry) {
            if (newValue == null || newValue == "")
                newValue = "2";
            mConnectRetry.setSummary(String.format("%s s", newValue));

        } else if (preference == mConnectRetryMaxTime) {
            if (newValue == null || newValue == "")
                newValue = "300";
            mConnectRetryMaxTime.setSummary(String.format("%s s", newValue));
        }

        return true;
    }

}
