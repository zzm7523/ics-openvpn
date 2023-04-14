/*
 * Copyright (c) 2012-2019 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core

import androidx.appcompat.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import de.blinkt.xp.openvpn.R
import de.blinkt.openvpn.core.OpenVPNNotificationHelper.EXTRA_CHALLENGE_TXT

class PasswordDialogFragment : DialogFragment() {

    private var mService: IOpenVPNServiceInternal? = null

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mService = IOpenVPNServiceInternal.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(activity, OpenVPNService::class.java)
        intent.action = OpenVPNService.START_SERVICE
        requireActivity().bindService(intent, mConnection, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        requireActivity().unbindService(mConnection)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AppCompatDialog {
        val title = requireArguments().getString("title")
        val echo = requireArguments().getBoolean("echo")
        val finish = requireArguments().getBoolean("finish")
        val input = EditText(activity)
        if (!echo)
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        return AlertDialog.Builder(requireActivity())
            .setTitle("Challenge/Response Authentification")
            .setMessage(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                try {
                    mService?.challengeResponse(input.text.toString())
                    if (finish) requireActivity().finish()
                } catch (ex: RemoteException) {
                    VpnStatus.logThrowable(ex)
                    ex.printStackTrace()
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> if (finish) requireActivity().finish() }
            .create()
    }

    companion object {
        fun newInstance(intent: Intent?, finish: Boolean): PasswordDialogFragment? {
            val extras = intent?.extras ?: return null
            val challenge = extras.getString(EXTRA_CHALLENGE_TXT, "R,E:(empty challenge text)")
            val message = challenge.split(":", limit = 2)[1]
            val flagsStr = challenge.split(":", limit = 2)[0]
            val flags = flagsStr.split(",")
            var echo = false
            var response = false

            for (flag in flags) {
                if (flag == "R")
                    response = true
                else if (flag == "E")
                    echo = true
            }
            if (!response) {
                VpnStatus.logError("Error unrecognised challenge from Server: $challenge")
                return null
            } else {
                val frag = PasswordDialogFragment()
                val args = Bundle()
                args.putString("title", message)
                args.putBoolean("echo", echo)
                args.putBoolean("finish", finish)
                frag.arguments = args
                return frag
            }
        }
    }

}
