/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.blinkt.openvpn.api;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatActivity;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.OpenVPNService;


public class ConfirmDialog extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener, DialogInterface.OnClickListener {

    public static final String EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME";
    public static final String ANONYMOUS_PACKAGE = "de.blinkt.openvpn.ANYPACKAGE";

    private static final String TAG = "ConfirmDialog";

    private String mPackage;
    private Button mButton;
    private IOpenVPNServiceInternal mService;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = IOpenVPNServiceInternal.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (intent != null)
            mPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (mPackage == null)
            mPackage = getCallingPackage();
        if (mPackage == null) {
            finish();
            return;
        }

        intent = new Intent(this, OpenVPNService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        try {
            View view = View.inflate(this, R.layout.api_confirm, null);
            String appString;
            if (mPackage.equals(ANONYMOUS_PACKAGE)) {
                appString = getString(R.string.all_app_prompt, getString(R.string.app));
            } else {
                PackageManager pm = getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(mPackage, 0);
                appString = getString(R.string.prompt, appInfo.loadLabel(pm), getString(R.string.app));
                ((ImageView) view.findViewById(R.id.icon)).setImageDrawable(appInfo.loadIcon(pm));
            }
            ((TextView) view.findViewById(R.id.prompt)).setText(appString);
            ((CompoundButton) view.findViewById(R.id.check)).setOnCheckedChangeListener(this);

            Builder builder = new AlertDialog.Builder(this);
            builder.setView(view);
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setTitle(android.R.string.dialog_alert_title);
            builder.setPositiveButton(android.R.string.ok, this);
            builder.setNegativeButton(android.R.string.cancel, this);

            AlertDialog dialog = builder.create();
            dialog.setOnShowListener((v) -> {
                mButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                mButton.setEnabled(false);
            });
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();

        } catch (Exception ex) {
            Log.e(TAG, "onResume", ex);
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mConnection);
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean checked) {
        mButton.setEnabled(checked);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            try {
                mService.addAllowedExternalApp(mPackage);
            } catch (RemoteException ex) {
                ex.printStackTrace();
                throw new RuntimeException(ex);
            }
            setResult(RESULT_OK);
            finish();

        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

}
