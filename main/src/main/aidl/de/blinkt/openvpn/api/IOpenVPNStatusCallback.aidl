package de.blinkt.openvpn.api;

interface IOpenVPNStatusCallback {

    /**
     * Called when the service has a new status for you.
     */
    oneway void newStatus(in String uuid, in String state, in String reason);

}
