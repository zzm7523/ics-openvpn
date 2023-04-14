/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.api;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionsManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class AppRestrictions {

    public static final String PROFILE_CREATOR = "de.blinkt.openvpn.api.AppRestrictions";

    private final static int CONFIG_VERSION = 1;

    private static AppRestrictions mInstance;
    private static boolean alreadyChecked = false;

    private RestrictionsManager mRestrictionsMgr;
    private BroadcastReceiver mRestrictionsReceiver;

    private AppRestrictions(Context c) {

    }

    public static AppRestrictions getInstance(Context c) {
        if (mInstance == null)
            mInstance = new AppRestrictions(c);
        return mInstance;
    }

    private void addChangesListener(@NonNull Context c) {
        IntentFilter restrictionsFilter = new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED);
        mRestrictionsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                applyRestrictions(context);
            }
        };
        c.registerReceiver(mRestrictionsReceiver, restrictionsFilter);
    }

    private void removeChangesListener(@NonNull Context c) {
        c.unregisterReceiver(mRestrictionsReceiver);
    }

    private String hashConfig(@NonNull String config) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = config.getBytes();
            digest.update(bytes, 0, bytes.length);
            return new BigInteger(1, digest.digest()).toString(16);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void applyRestrictions(@NonNull Context c) {
        mRestrictionsMgr = (RestrictionsManager) c.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (mRestrictionsMgr == null)
            return;

        Bundle restrictions = mRestrictionsMgr.getApplicationRestrictions();
        if (restrictions == null)
            return;

        String configVersion = restrictions.getString("version", "(not set)");
        if ("(not set)".equals(configVersion))
            // Ignore error if no version present
            return;

        try {
            if (Integer.parseInt(configVersion) != CONFIG_VERSION)
                throw new NumberFormatException("Wrong version");
        } catch (NumberFormatException nex) {
            VpnStatus.logError(String.format(Locale.US, "App restriction version %s does not match expected version %d",
                configVersion, CONFIG_VERSION));
            return;
        }

        Parcelable[] profileList = restrictions.getParcelableArray(("vpn_configuration_list"));
        if (profileList == null) {
            VpnStatus.logError("App restriction does not contain a profile list (vpn_configuration_list)");
            return;
        }

        ProfileManager profileManager = ProfileManager.getInstance(c);
        Set<String> provisionedUuids = new HashSet<>();
        for (Parcelable profile : profileList) {
            if (!(profile instanceof Bundle)) {
                VpnStatus.logError("App restriction profile has wrong type");
                continue;
            }

            Bundle bundle = (Bundle) profile;
            String uuid = bundle.getString("uuid");
            String ovpn = bundle.getString("ovpn");
            String name = bundle.getString("name");

            if (uuid == null || ovpn == null || name == null) {
                VpnStatus.logError("App restriction profile misses uuid, ovpn or name key");
                continue;
            }

            String ovpnHash = hashConfig(ovpn);
            provisionedUuids.add(uuid.toLowerCase(Locale.US));

            // Check if the profile already exists
            VpnProfile vpnProfile = profileManager.get(c, uuid);
            if (vpnProfile != null) {
                // Profile exists, check if need to update it
                if (ovpnHash.equals(vpnProfile.getImportedProfileHash()))
                    // not modified skip to next profile
                    continue;
            }
            addProfile(c, ovpn, uuid, name, vpnProfile);
        }

        Vector<VpnProfile> profilesToRemove = new Vector<>();
        // get List of all managed profiles
        for (VpnProfile profile : profileManager.getProfiles()) {
            if (PROFILE_CREATOR.equals(profile.getProfileCreator())) {
                if (!provisionedUuids.contains(profile.getUUIDString()))
                    profilesToRemove.add(profile);
            }
        }
        for (VpnProfile profile : profilesToRemove) {
            VpnStatus.logInfo("Remove with uuid: %s and name: %s since it is no longer in the list of managed profiles");
            profileManager.removeProfile(c, profile);
        }
    }

    private void addProfile(Context c, String config, String uuid, String name, VpnProfile vpnProfile) {
        try {
            ConfigParser parser = new ConfigParser();
            config = prepareConfig(config);
            parser.parseConfig(new StringReader(config));
            VpnProfile vp = parser.convertProfile();
            vp.setProfileCreator(PROFILE_CREATOR);

            // We don't want provisioned profiles to be editable
            vp.setUserEditable(false);

            vp.setName(name);
            vp.setUuid(UUID.fromString(uuid));
            vp.setImportedProfileHash(hashConfig(config));

            if (vpnProfile != null) {
                vp.setVersion(vpnProfile.getVersion() + 1);
                vp.setAlias(vpnProfile.getAlias());
            }

            ProfileManager profileManager = ProfileManager.getInstance(c);

            // The add method will replace any older profiles with the same UUID
            profileManager.addProfile(vp);
            profileManager.saveProfile(c, vp);
            profileManager.saveProfileList(c);

        } catch (ConfigParser.ParseError | IOException | IllegalArgumentException ex) {
            VpnStatus.logThrowable("Error during import of managed profile", ex);
        }
    }

    private String prepareConfig(String config) {
        String newLine = System.getProperty("line.separator");
        if (!config.contains(newLine) && !config.contains(" ")) {
            try {
                byte[] decoded = android.util.Base64.decode(config.getBytes(), android.util.Base64.DEFAULT);
                config  = new String(decoded);
                return config;
            } catch(IllegalArgumentException ex) {
                // Ignore
            }
        }
        return config;
    };

    public void checkRestrictions(@NonNull Context c) {
        if (alreadyChecked) {
        } else {
            alreadyChecked = true;
            addChangesListener(c);
            applyRestrictions(c);
        }
    }

    public void pauseCheckRestrictions(@NonNull Context c) {
        removeChangesListener(c);
    }

}
