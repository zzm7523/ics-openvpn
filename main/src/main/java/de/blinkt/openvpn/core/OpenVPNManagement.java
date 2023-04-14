/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

public interface OpenVPNManagement {

    interface PausedStateCallback {
        boolean shouldBeRunning();
    }

    enum PauseReason {
        noNetwork,
        userPause,
        screenOff,
    }

    int BYTECOUNT_INTERVAL = 2;

    void pauseVPN(PauseReason reason);

    void resumeVPN();

    /**
     * @param replaceConnection True if the VPN is connected by a new connection.
     * @return true if there was a process that has been send a stop signal
     */
    boolean stopVPN(boolean replaceConnection);

    /*
     * Rebind the interface
     */
    void networkChange(boolean sameNetwork);

    void setPauseCallback(PausedStateCallback callback);

    /**
     * Send the response to a challenge response
     * @param response  Base64 encoded response
     */
    void sendCRResponse(String response);

}
