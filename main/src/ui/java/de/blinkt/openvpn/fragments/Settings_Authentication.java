/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import java.io.IOException;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.views.RemoteCNPreference;
import de.blinkt.openvpn.views.RemoteCNPreferenceDialog;


public class Settings_Authentication extends Settings_PreferenceFragment implements Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    // Can only use lower 16 bits for requestCode
    private static final int SELECT_TLS_FILE = 2323;

    private CheckBoxPreference mExpectTLSCert;
    private CheckBoxPreference mCheckRemoteCN;
    private RemoteCNPreference mRemoteCN;
    private ListPreference mTLSAuthDirection;
    private Preference mTLSAuthFile;
    private SwitchPreference mUseTLSAuth;
    private ListPreference mCipher;
    private String mTlsAuthFileData;
    private ListPreference mAuth;
    private EditTextPreference mRemoteX509Name;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.vpn_authentification);

        mExpectTLSCert = findPreference("remoteServerTLS");
        mCheckRemoteCN = findPreference("checkRemoteCN");
        mRemoteCN = findPreference("remotecn");
        mRemoteCN.setOnPreferenceChangeListener(this);

        mRemoteX509Name = findPreference("remotex509name");
        mRemoteX509Name.setOnPreferenceChangeListener(this);

        mUseTLSAuth = findPreference("useTLSAuth");
        mTLSAuthFile = findPreference("tlsAuthFile");
        mTLSAuthFile.setOnPreferenceClickListener(this);

        mTLSAuthDirection = findPreference("tls_direction");
        mTLSAuthDirection.setOnPreferenceChangeListener(this);

        mCipher = findPreference("cipher");
        mCipher.setOnPreferenceChangeListener(this);

        mAuth = findPreference("auth");
        mAuth.setOnPreferenceChangeListener(this);

        loadPreferences();
    }

    protected void loadPreferences() {
        mExpectTLSCert.setChecked(mProfile.isExpectTLSCert());
        mCheckRemoteCN.setChecked(mProfile.isCheckRemoteCN());
        mRemoteCN.setDN(mProfile.getRemoteCN());
        mRemoteCN.setAuthType(mProfile.getX509AuthType());
        onPreferenceChange(mRemoteCN, new Pair<>(mProfile.getX509AuthType(), mProfile.getRemoteCN()));

        mRemoteX509Name.setText(mProfile.getX509UsernameField());
        onPreferenceChange(mRemoteX509Name, mProfile.getX509UsernameField());

        mUseTLSAuth.setChecked(mProfile.isUseTLSAuth());
        mTlsAuthFileData = mProfile.getTLSAuthFilename();
        setTlsAuthSummary(mTlsAuthFileData);
        mTLSAuthDirection.setValue(mProfile.getTLSAuthDirection());
        onPreferenceChange(mTLSAuthDirection, mProfile.getTLSAuthDirection());

        mCipher.setValue(mProfile.getCipher());
        onPreferenceChange(mCipher, mProfile.getCipher());
        mAuth.setValue(mProfile.getAuth());
        onPreferenceChange(mAuth, mProfile.getAuth());
    }

    protected void savePreferences() {
        mProfile.setExpectTLSCert(mExpectTLSCert.isChecked());
        mProfile.setCheckRemoteCN(mCheckRemoteCN.isChecked());
        mProfile.setRemoteCN(mRemoteCN.getCNText());
        mProfile.setX509AuthType(mRemoteCN.getAuthtype());

        mProfile.setCipher(mCipher.getValue());
        mProfile.setAuth(mAuth.getValue());

        mProfile.setUseTLSAuth(mUseTLSAuth.isChecked());
        mProfile.setTLSAuthFilename(mTlsAuthFileData);
        mProfile.setX509UsernameField(mRemoteX509Name.getText());
        mProfile.setTLSAuthDirection(mTLSAuthDirection.getValue());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mRemoteCN) {
            @SuppressWarnings("unchecked")
            int authType = ((Pair<Integer, String>) newValue).first;
            @SuppressWarnings("unchecked")
            String dn = ((Pair<Integer, String>) newValue).second;

            if (TextUtils.isEmpty(dn)) {
                if (mProfile.getConnections().length == 0) {
                    preference.setSummary(R.string.no_remote_defined);
                } else {
                    dn = mProfile.getConnections()[0].getServerName();
                    preference.setSummary(getX509String(VpnProfile.X509_VERIFY_TLSREMOTE_RDN, dn == null ? "" : dn));
                }
            } else {
                preference.setSummary(getX509String(authType, dn));
            }

        } else if (preference == mRemoteX509Name) {
            preference.setSummary(TextUtils.isEmpty((CharSequence) newValue) ? "CN" : (CharSequence) newValue);

        } else if (preference == mCipher) {
            for (int i = 0; i < mCipher.getEntryValues().length; ++i) {
                if (mCipher.getEntryValues()[i].equals(newValue == null ? "" : newValue)) {
                    mCipher.setSummary(mCipher.getEntries()[i]);
                    break;
                }
            }

        } else if (preference == mAuth) {
            for (int i = 0; i < mAuth.getEntryValues().length; ++i) {
                if (mAuth.getEntryValues()[i].equals(newValue == null ? "" : newValue)) {
                    mAuth.setSummary(mAuth.getEntries()[i]);
                    break;
                }
            }

        } else if (preference == mTLSAuthDirection) {
            for (int i = 0; i < mTLSAuthDirection.getEntryValues().length; ++i) {
                if (mTLSAuthDirection.getEntryValues()[i].equals(newValue == null ? "" : newValue)) {
                    mTLSAuthDirection.setSummary(mTLSAuthDirection.getEntries()[i]);
                    break;
                }
            }
        }

        return true;
    }

    private CharSequence getX509String(int authtype, String dn) {
        String ret = "";
        switch (authtype) {
            case VpnProfile.X509_VERIFY_TLSREMOTE:
            case VpnProfile.X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING:
                ret += "tls-remote ";
                break;

            case VpnProfile.X509_VERIFY_TLSREMOTE_DN:
                ret = "dn: ";
                break;

            case VpnProfile.X509_VERIFY_TLSREMOTE_RDN:
                ret = "rdn: ";
                break;

            case VpnProfile.X509_VERIFY_TLSREMOTE_RDN_PREFIX:
                ret = "rdn prefix: ";
                break;
        }

        return ret + dn;
    }

    private void startFileDialog() {
        Intent startFC = FilePickerUtils.getFilePickerIntent(getActivity(), FilePickerUtils.FileType.TLS_AUTH_FILE);
        if (startFC != null) {
            startActivityForResult(startFC, SELECT_TLS_FILE);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        startFileDialog();
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SELECT_TLS_FILE && resultCode == AppCompatActivity.RESULT_OK) {
            try {
                mTlsAuthFileData = FilePickerUtils.getFilePickerResult(FilePickerUtils.FileType.TLS_AUTH_FILE, data, getActivity());
                setTlsAuthSummary(mTlsAuthFileData);
            } catch (IOException ex) {
                VpnStatus.logThrowable(ex);
            }
        }
    }

    private void setTlsAuthSummary(String result) {
        if (TextUtils.isEmpty(result))
            result = getString(R.string.no_certificate);

        if (result.startsWith(VpnProfile.INLINE_TAG))
            mTLSAuthFile.setSummary(R.string.inline_file_data);
        else if (result.startsWith(VpnProfile.DISPLAYNAME_TAG))
            mTLSAuthFile.setSummary(getString(R.string.imported_from_file, VpnProfile.getDisplayName(result)));
        else
            mTLSAuthFile.setSummary(result);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment dialogFragment = null;
        if (preference instanceof RemoteCNPreference) {
            dialogFragment = RemoteCNPreferenceDialog.newInstance(preference.getKey());
        }

        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(requireFragmentManager(), "RemoteCNDialog");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

}
