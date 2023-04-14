/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import de.blinkt.xp.openvpn.R;

import java.util.Set;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.api.ExternalAppDatabase;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.utils.ActivityUtils;


public class GeneralSettings extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener,
        Preference.OnPreferenceChangeListener {

    private ListPreference mAlwaysOnVPN;
    private CheckBoxPreference mRestartvpnOnboot;
    private Preference mClearapi;
    private String mAllowAppsString;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.general_settings);

        ProfileManager profileManager = ProfileManager.getInstance(getActivity());

        mAlwaysOnVPN = findPreference("always_on_vpn");
        if (mAlwaysOnVPN != null) {
            mAlwaysOnVPN.setOnPreferenceChangeListener(this);
            VpnProfile profile = profileManager.getAlwaysOnVPN(getActivity());
            onPreferenceChange(mAlwaysOnVPN, profile == null ? "" : profile.getUUIDString());
        }

        mRestartvpnOnboot = findPreference("restart_vpn_onboot");
        if (mRestartvpnOnboot != null) {
            mRestartvpnOnboot.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue.equals(true)) {
                    VpnProfile profile = profileManager.getAlwaysOnVPN(requireActivity());
                    if (profile == null) {
                        Toast.makeText(requireContext(), R.string.no_default_vpn_set, Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
                return true;
            });
        }

        mClearapi = findPreference("clear_api");
        if (mClearapi != null) {
            mClearapi.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mClearapi != null) {
            setClearApiSummary(false);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAlwaysOnVPN) {
            StringBuilder buffer = new StringBuilder(getString(R.string.defaultvpnsummary));
            VpnProfile profile = null;

            if (!TextUtils.isEmpty((String) newValue)) {
                profile = ProfileManager.getInstance(getActivity()).get(getActivity(), (String) newValue);
            }
            if (profile == null)
                buffer.append('\n').append(getString(R.string.novpn_selected));
            else
                buffer.append('\n').append(getString(R.string.vpnselected, profile.getName()));
            mAlwaysOnVPN.setSummary(buffer.toString());
        }

        return true;
    }

    private String getAllowAppsString(Set<String> allowApps, String delim) {
        PackageManager pm = getActivity().getPackageManager();
        StringBuilder builder = new StringBuilder();
        ApplicationInfo appInfo;

        if (allowApps != null && !allowApps.isEmpty()) {
            for (String packageName : allowApps) {
                if (builder.length() != 0)
                    builder.append(delim == null ? "" : delim);

                try {
                    appInfo = pm.getApplicationInfo(packageName, 0);
                    builder.append(appInfo.loadLabel(pm));

                } catch (PackageManager.NameNotFoundException ex) {
                    builder.append(packageName);
                    // App不存在, 不要从列表删除, 用户可能随后安装App
//                  ExternalAppDatabase.removeAllowedApp(getActivity(), packageName);
                }
            }
        }

        return builder.toString();
    }

    private void setClearApiSummary(boolean clear) {
        new Thread(() -> {
            Set<String> allowApps = clear ? null : ExternalAppDatabase.getAllowedApps(getActivity());
            String allowAppsString = clear ? null : getAllowAppsString(allowApps, ", ");
            if (clear) {
                ExternalAppDatabase.clearAllowedApps(getActivity());
            }

            ActivityUtils.runOnUiThread(getActivity(), () -> {
                mAllowAppsString = allowAppsString;
                if (allowApps == null || allowApps.isEmpty()) {
                    mClearapi.setEnabled(false);
                    mClearapi.setSummary(R.string.no_external_app_allowed);
                } else {
                    mClearapi.setEnabled(true);
                    mClearapi.setSummary(getString(R.string.allowed_apps, mAllowAppsString));
                }
            });
        }).start();
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        if (preference == mClearapi) {
            Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(getString(R.string.clearappsdialog, mAllowAppsString));
            builder.setPositiveButton(R.string.clear, (dialog, which) -> {
                if (which == AppCompatDialog.BUTTON_POSITIVE) {
                    setClearApiSummary(true);
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        }

        return true;
    }

}
