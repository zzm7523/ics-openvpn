/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.List;

import de.blinkt.openvpn.core.AccessibleResource;
import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.VpnTunnel;


/**
 * Created by arne on 22.04.16.
 */
@TargetApi(Build.VERSION_CODES.N)
public class OpenVPNTileService extends TileService implements VpnStatus.StatusListener {

    @SuppressLint("Override")
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onClick() {
        super.onClick();

        VpnProfile bootProfile = ProfileManager.getInstance(this).getAlwaysOnVPN(this);
        if (bootProfile == null) {
            Toast.makeText(this, R.string.novpn_selected, Toast.LENGTH_SHORT).show();
        } else {
            if (!isLocked()) {
                clickAction(bootProfile);
            } else {
                unlockAndRun(() -> clickAction(bootProfile));
            }
        }
    }

    private void clickAction(@NonNull VpnProfile profile) {
        if (VpnStatus.isVPNActive()) {
            stopVPN(profile, this);
        } else {
            launchVPN(profile, this);
        }
    }

    private void stopVPN(@NonNull VpnProfile profile, @NonNull Context context) {
        Intent intent = new Intent(context, OpenVPNService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
        bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder binder) {
                IOpenVPNServiceInternal service = IOpenVPNServiceInternal.Stub.asInterface(binder);
                if (service != null) {
                    try {
                        service.stopVPN(false);
                    } catch (RemoteException e) {
                        VpnStatus.logThrowable(e);
                    }
                }
                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {

            }
        }, Context.BIND_AUTO_CREATE);
    }

    @SuppressLint("Override")
    @TargetApi(Build.VERSION_CODES.N)
    private void launchVPN(@NonNull VpnProfile profile, @NonNull Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(context, LaunchOpenVPN.class);
        intent.putExtra(LaunchOpenVPN.EXTRA_KEY, profile.getUUIDString());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(LaunchOpenVPN.EXTRA_HIDELOG, true);
        context.startActivity(intent);
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onTileAdded() {
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        VpnStatus.addStatusListener(this);
    }

    @Override
    public void onStopListening() {
        VpnStatus.removeStatusListener(this);
        super.onStopListening();
    }

    @Override
    public void updateStatus(@NonNull ConnectionStatus status, Bundle extra) {
        Tile tile = getQsTile();
        VpnProfile profile;

        if (status.getLevel() == ConnectionStatus.LEVEL_AUTH_FAILED || status.getLevel() == ConnectionStatus.LEVEL_NOTCONNECTED) {
            // No VPN connected, use stadnard VPN
            profile = ProfileManager.getInstance(this).getAlwaysOnVPN(this);
            if (profile == null) {
                tile.setLabel(getString(R.string.novpn_selected));
                tile.setState(Tile.STATE_UNAVAILABLE);
            } else {
                tile.setLabel(getString(R.string.qs_connect, profile.getName()));
                tile.setState(Tile.STATE_INACTIVE);
            }

        } else {
            profile = ProfileManager.getInstance(this).get(this, VpnStatus.getConnectedVpnProfile());
            String name = profile == null ? "null?!" : profile.getName();
            tile.setLabel(getString(R.string.qs_disconnect, name));
            tile.setState(Tile.STATE_ACTIVE);
        }

        tile.updateTile();
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

}
