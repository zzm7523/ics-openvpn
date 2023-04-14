/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.utils.OpenVPNUtils;

public class ProfileManager {

    private static final String TEMPORARY_PROFILE_FILENAME = "temporary_vpn_profile";
    private static final String LAST_CONNECTED_PROFILE = "last_connected_profile";

    private static ProfileManager instance = null;

    private int mVersion = -1;
    private VpnProfile mLastConnectedVpn = null;
    private VpnProfile mTmpProfile = null;
    private Map<String, VpnProfile> mProfiles = new HashMap<>();

    private ProfileManager() {
    }

    private static void checkInstance(@NonNull Context context) {
        SharedPreferences prefs = Preferences.getProfileSharedPreferences(context);
        int version = prefs.getInt("version", 0);

        if (instance == null) {
            instance = new ProfileManager();
        }

        // 初次加载
        // 版本变化, ExternalOpenVPNService后台更改了数据; 重新加载
        if (instance.mVersion == -1 || instance.mVersion != version) {
            instance.mVersion = version;
            instance.loadVPNList(context);
        }
    }

    synchronized public static ProfileManager getInstance(@NonNull Context context) {
        checkInstance(context);
        return instance;
    }

    public Collection<VpnProfile> getProfiles() {
        return mProfiles.values();
    }

    public VpnProfile getProfileByName(@NonNull String name) {
        for (VpnProfile profile : mProfiles.values()) {
            if (profile.getName().equals(name))
                return profile;
        }
        return null;
    }

    public void saveProfileList(@NonNull Context context) {
        SharedPreferences prefs = Preferences.getProfileSharedPreferences(context);
        mVersion = prefs.getInt("version", 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("version", mVersion);
        editor.putStringSet("vpnlist", mProfiles.keySet());
        editor.apply();
    }

    public boolean existProfile(@NonNull VpnProfile profile) {
        return mProfiles.containsKey(profile.getUUIDString());
    }

    public void addProfile(@NonNull VpnProfile profile) {
        mProfiles.put(profile.getUUIDString(), profile);
    }

    public void saveProfile(@NonNull Context context, @NonNull VpnProfile profile) {
        SharedPreferences prefs = Preferences.getProfileSharedPreferences(context);
        mVersion = prefs.getInt("version", 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("version", mVersion).apply();

        saveProfile(context, profile, true, profile.isTemporaryProfile());
    }

    public void removeProfile(@NonNull Context context, @NonNull VpnProfile profile) {
        mProfiles.remove(profile.getUUIDString());
        if (mLastConnectedVpn == profile) {
            mLastConnectedVpn = null;
        }
        if (mTmpProfile == profile) {
            mTmpProfile = null;
        }
        saveProfileList(context);
        context.deleteFile(profile.getUUIDString() + ".vpbf");
    }

    private void loadVPNList(@NonNull Context context) {
        SharedPreferences prefs = Preferences.getProfileSharedPreferences(context);
        Set<String> vlist = prefs.getStringSet("vpnlist", null);
        if (vlist == null) {
            vlist = new HashSet<>();
        }

        // Always try to load the temporary profile
        vlist.add(TEMPORARY_PROFILE_FILENAME);
        mProfiles = new HashMap<>();

        for (String vpnentry : vlist) {
            try (ObjectInputStream in = new ObjectInputStream(context.openFileInput(vpnentry + ".vpbf"))) {
                VpnProfile profile = ((VpnProfile) in.readObject());

                // Sanity check
                if (profile == null || profile.getName() == null || profile.getUuid() == null)
                    continue;

                if (vpnentry.equals(TEMPORARY_PROFILE_FILENAME)) {
                    mTmpProfile = profile;
                } else {
                    mProfiles.put(profile.getUUIDString(), profile);
                }

            } catch (ClassNotFoundException | IOException ex) {
                if (!vpnentry.equals(TEMPORARY_PROFILE_FILENAME))
                    VpnStatus.logThrowable("Loading VPN List", ex);
            }
        }
    }

    /**
     * Sets the profile that is connected (to connect if the service restarts)
     */
    public void setConnectedVpnProfile(@NonNull Context context, @Nullable VpnProfile connectedProfile) {
        SharedPreferences prefs = Preferences.getVariableSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(LAST_CONNECTED_PROFILE, connectedProfile == null ? null : connectedProfile.getUUIDString());
        editor.apply();
        mLastConnectedVpn = connectedProfile;
    }

    /**
     * Returns the profile that was last connected (to connect if the service restarts)
     */
    public VpnProfile getConnectedVpnProfile(@NonNull Context context) {
        SharedPreferences prefs = Preferences.getVariableSharedPreferences(context);
        String lastConnectedProfile = prefs.getString(LAST_CONNECTED_PROFILE, null);
        return lastConnectedProfile == null ? null : get(context, lastConnectedProfile);
    }

    public VpnProfile getTemporaryProfile() {
        return mTmpProfile;
    }

    public void setTemporaryProfile(@NonNull Context context, @NonNull VpnProfile profile) {
        SharedPreferences prefs = Preferences.getProfileSharedPreferences(context);
        int version = prefs.getInt("version", 0) + 1;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("version", version).apply();

        profile.setTemporaryProfile(true);
        mTmpProfile = profile;
        saveProfile(context, profile, true, true);
    }

    private void saveProfile(@NonNull Context context, @NonNull VpnProfile profile, boolean updateVersion, boolean isTemporary) {
        if (updateVersion)
            profile.setVersion(profile.getVersion() + 1);

        String filename = profile.getUUIDString() + ".vpbf";
        if (isTemporary)
            filename = TEMPORARY_PROFILE_FILENAME + ".vpbf";

        try (ObjectOutputStream out = new ObjectOutputStream(context.openFileOutput(filename, AppCompatActivity.MODE_PRIVATE))) {
            out.writeObject(profile);
            out.flush();

        } catch (IOException ex) {
            VpnStatus.logThrowable("saving VPN profile", ex);
            throw new RuntimeException(ex);
        }
    }

    private VpnProfile get(@NonNull String key) {
        if (mTmpProfile != null && mTmpProfile.getUUIDString().equals(key))
            return mTmpProfile;
        return mProfiles.get(key);
    }

    public VpnProfile get(@NonNull Context context, @NonNull String profileUUID) {
        return get(context, profileUUID, 0, 10);
    }

    public VpnProfile get(@NonNull Context context, @NonNull String profileUUID, int version, int tries) {
        if (profileUUID == null) {
            return null;
        }

        VpnProfile profile = get(profileUUID);
        int tried = 0;

        while ((profile == null || profile.getVersion() < version) && (tried++ < tries)) {
            if (tried > 1)
                OpenVPNUtils.sleep(100);
            loadVPNList(context);
            profile = get(profileUUID);
        }

        if (tried > 5) {
            int ver = profile == null ? -1 : profile.getVersion();
            VpnStatus.logError(String.format(Locale.US, "Used x %d tries to get current version (%d/%d) of the profile", tried, ver, version));
        }
        return profile;
    }

    public VpnProfile getConnectedVpnProfile() {
        return mLastConnectedVpn;
    }

    public VpnProfile getAlwaysOnVPN(@NonNull Context context) {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);
        String uuid = prefs.getString("always_on_vpn", null);
        return get(uuid);
    }

    public void updateLRU(@NonNull Context context, @NonNull VpnProfile profile) {
        profile.setLastUsed(System.currentTimeMillis());
        // LRU does not change the profile, no need for the service to refresh
        if (profile != mTmpProfile) {
            saveProfile(context, profile, false, false);
        }
    }

}
