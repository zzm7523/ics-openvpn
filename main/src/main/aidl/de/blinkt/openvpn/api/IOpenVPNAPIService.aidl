// IOpenVPNAPIService.aidl
package de.blinkt.openvpn.api;

import de.blinkt.openvpn.api.APIVpnProfile;
import de.blinkt.openvpn.api.IOpenVPNStatusCallback;

import android.content.Intent;
import android.os.ParcelFileDescriptor;

interface IOpenVPNAPIService {

    /* Registers to receive VPN Status Updates */
    void registerStatusCallback(in IOpenVPNStatusCallback cb);

    /* Remove a previously registered callback interface. */
    void unregisterStatusCallback(in IOpenVPNStatusCallback cb);

    List<APIVpnProfile> getProfiles();

    void startProfile(in String profileUUID);

    /** start a profile using a config as inline string. Make sure that all needed data is inlined,
     * e.g., using <ca>...</ca> or <auth-user-pass>...</auth-user-pass>
     * See the VPN manual page for more on inlining files */
    void startVPN(in String inlineConfig);

    /* Disconnect the VPN */
    void disconnect();

    /** Use a profile with all certificates etc. embedded */
    APIVpnProfile addProfile(in String name, boolean userEditable, in String config);

    /** Remove a profile by UUID */
    void removeProfile(in String profileUUID);

    /** This permission framework is used  to avoid confused deputy style attack to the VPN
     * calling this will give null if the app is allowed to use the external API and an Intent
     * that can be launched to request permissions otherwise */
    Intent prepare(in String packageName);

    /** Used to trigger to the Android VPN permission dialog (VPNService.prepare()) in advance,
     * if this return null VPN for Android already has the permissions otherwise you can start the returned Intent
     * to let VPN for Android request the permission */
    Intent prepareVPNService();

    /* Pause the VPN (same as using the pause feature in the notifcation bar) */
    void pause();

    /* Resume the VPN (same as using the pause feature in the notifcation bar) */
    void resume();

    /** Request a socket to be protected as a VPN socket would be. Useful for creating
     * a helper socket for an app controlling VPN
     * Before calling this function you should make sure VPN for Android may actually
     * this function by checking if prepareVPNService returns null; */
    boolean protectSocket(in ParcelFileDescriptor fd);

}
