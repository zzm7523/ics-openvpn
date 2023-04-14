/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;

import de.blinkt.xp.openvpn.R;

public class Settings_Basic extends KeyChainSettingsFragment {

    private View mLayout;
    private EditText mProfileName;
    private CheckBox mUseLzo;
    private EditText mUserName;
    private EditText mPassword;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mLayout = inflater.inflate(R.layout.basic_settings, container, false);

        mProfileName = mLayout.findViewById(R.id.profilename);
        mUseLzo = mLayout.findViewById(R.id.lzo);

        mUserName = mLayout.findViewById(R.id.auth_username);
        mPassword = mLayout.findViewById(R.id.auth_password);

        initKeychainViews(mLayout);
        return mLayout;
    }

    protected void loadPreferences() {
        super.loadPreferences();
        mProfileName.setText(mProfile.getName());
        mUseLzo.setChecked(mProfile.isUseLzo());
        mUserName.setText(mProfile.getUsername());
        mPassword.setText(mProfile.getPassword());
    }

    protected void savePreferences() {
        super.savePreferences();
        mProfile.setName(mProfileName.getText().toString());
        mProfile.setUseLzo(mUseLzo.isChecked());
        mProfile.setUsername(mUserName.getText().toString());
        mProfile.setPassword(mPassword.getText().toString());
    }

    /* 不需要重载onSaveInstanceState(...)
    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // !!未回调onCreateView(...)的情况下，也可能回调onSaveInstanceState(...)
        if (mView != null) {
            savePreferences();
        }
    }
    */

}
