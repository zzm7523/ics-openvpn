/*
 * Copyright (c) 2012-2017 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.api;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.StringReader;

import de.blinkt.openvpn.LaunchOpenVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.xp.openvpn.R;

public class RemoteAction extends AppCompatActivity {

    public static final String EXTRA_PROFILE_NAME = "de.blinkt.openvpn.api.profileName";
    public static final String EXTRA_INLINE_CONFIG = "de.blinkt.openvpn.api.inlineConfig";

    private static final String TAG = "RemoteAction";

    private IOpenVPNServiceInternal mService;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = IOpenVPNServiceInternal.Stub.asInterface(service);
            try {
                performAction();
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = new Intent(this, OpenVPNService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
        getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private VpnProfile parseConfig(@NonNull String inlineConfig, String callingPackage) {
        ConfigParser parser = new ConfigParser();

        try {
            parser.parseConfig(new StringReader(inlineConfig));
            VpnProfile profile = parser.convertProfile();
            profile.setName("Remote APP VPN");
            profile.setProfileCreator(callingPackage);

            int checked = profile.checkProfile(getApplicationContext());
            if (checked != R.string.no_error_found)
                Log.d(TAG, getString(checked));
            else {
                ProfileManager.getInstance(this).setTemporaryProfile(this, profile);
                return profile;
            }

        } catch (Exception ex) {
            Log.d(TAG, "Parse inline config fail", ex);
        }

        return null;
    }

    private void startProfile(@NonNull VpnProfile profile) {
        Intent startVPN = new Intent(this, LaunchOpenVPN.class);
        startVPN.putExtra(LaunchOpenVPN.EXTRA_KEY, profile.getUUIDString());
        startVPN.setAction(Intent.ACTION_MAIN);
        startActivity(startVPN);
    }

    private void performAction() throws RemoteException {
        String callingPackage = getCallingPackage();
        if (!mService.isAllowedExternalApp(callingPackage)) {
            Toast.makeText(this, String.format("%s disallow control OpenVPN for android", callingPackage), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Intent intent = getIntent();
        ComponentName component = intent.getComponent();
        setIntent(null);

        if (component.getClassName().endsWith(".api.DisconnectVPN")) {
            mService.stopVPN(false);
        } else if (component.getClassName().endsWith(".api.ConnectVPN")) {
            VpnProfile profile = null;
            String name = intent.getStringExtra(EXTRA_PROFILE_NAME);
            if (name != null)
                profile = ProfileManager.getInstance(this).getProfileByName(name);

            if (profile == null) {
                String inlineConfig = intent.getStringExtra(EXTRA_INLINE_CONFIG);
                if (inlineConfig != null)
                    profile = parseConfig(inlineConfig, callingPackage);
            }

            if (profile == null) {
                Toast.makeText(this, String.format("Vpn profile %s from API call not found", name), Toast.LENGTH_LONG).show();
            } else {
                startProfile(profile);
            }
        }

        finish();
    }

    @Override
    public void finish() {
        if (mService != null) {
            mService = null;
            getApplicationContext().unbindService(mConnection);
        }
        super.finish();
    }

}
