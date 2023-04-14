/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.security.KeyChain
import android.security.KeyChainException
import android.security.keystore.KeyInfo
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.utils.X509Utils
import de.blinkt.openvpn.views.FileSelectLayout
import de.blinkt.xp.openvpn.R
import java.security.KeyFactory
import java.security.cert.X509Certificate

internal abstract class KeyChainSettingsFragment : Settings_Fragment(), AdapterView.OnItemSelectedListener, View.OnClickListener,
        FileSelectLayout.FileSelectCallback{

    companion object {
        private val TAG: String = "KEY_CHAIN"
        private val UPDATE_ALIAS = 210
        private val CHOOSE_FILE_OFFSET = 1000
    }

    private lateinit var mLayout: View
    private lateinit var mAuthType: Spinner

    private lateinit var mPkcs12: FileSelectLayout
    private lateinit var mPkcs12Password: EditText

    private var mCaCert: FileSelectLayout? = null
    private var mCrlFile: FileSelectLayout? = null

    private lateinit var mAliasName: TextView
    private lateinit var mAliasCertificate: TextView

    private val fileselects = SparseArray<FileSelectLayout>()

    private val mHandler: Handler = Handler { _ ->
            if (mProfile.authenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE ||
                mProfile.authenticationType == VpnProfile.TYPE_KEYSTORE) {
                setKeyStoreAlias()
            }
            true
    }

    private val isInHardwareKeystore: Boolean
        @Throws(KeyChainException::class, InterruptedException::class)
        get() {
            val key = KeyChain.getPrivateKey(requireActivity().applicationContext, mProfile.alias) ?: return false

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                val keyFactory = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                val keyInfo = keyFactory.getKeySpec(key, KeyInfo::class.java)
                return keyInfo.isInsideSecureHardware

            } else {
                return KeyChain.isBoundKeyAlgorithm(key.algorithm)
            }
        }

    private fun setKeyStoreAlias() {
        if (mProfile.alias == null) {
            mAliasName.setText(R.string.no_certificate)
            mAliasName.tag = null
            mAliasCertificate.text = ""
            return
        }

        mAliasName.text = mProfile.alias
        mAliasName.tag = mProfile.alias
        mAliasCertificate.text = "Loading certificate from Keystore..."

        Thread {
            var certstr = ""

            try {
                var cert: X509Certificate? = null
                val certChain = KeyChain.getCertificateChain(requireActivity().applicationContext, mProfile.alias)
                if (certChain != null) {
                    cert = certChain[0]
                    try {
                        if (isInHardwareKeystore)
                            certstr += getString(R.string.hwkeychain)
                    } catch (e: Exception) {
                        Log.d(TAG, "check hardware keystore fail, " + e.localizedMessage)
                    }
                }

                if (cert == null) {
                    certstr = "Loading certificate from Keystore..."
                } else {
                    certstr = getCertificateBasicInfo(cert, resources)
                }

            } catch (ex: Exception) {
                certstr = "Could not get certificate from Keystore: " + ex.localizedMessage
            }

            val finalCertstr = certstr

            activity?.runOnUiThread {
                mAliasCertificate.text = Html.fromHtml(finalCertstr)
            }

        }.start()
    }

    private fun getCertificateBasicInfo(cert: X509Certificate, res: Resources): String {
        try {
            val validtyString = X509Utils.getCertificateValidityString(cert, res)
            val friendlyName = X509Utils.getCertificateFriendlyName(cert)
            return "$validtyString; $friendlyName"

        } catch (ex: Exception) {
            return "Failed to extract the contents of the certificate"
        }
    }

    protected fun initKeychainViews(v: View) {
        mLayout = v

        mAuthType = v.findViewById(R.id.type)
        mAuthType.onItemSelectedListener = this

        mPkcs12 = v.findViewById(R.id.pkcs12select)
        addFileSelectLayout(mPkcs12, FilePickerUtils.FileType.PKCS12)
        mPkcs12Password = v.findViewById(R.id.pkcs12password)

        mCaCert = v.findViewById(R.id.caselect)
        if (mCaCert != null) {
            addFileSelectLayout(mCaCert!!, FilePickerUtils.FileType.CERTIFICATE)
            mCaCert!!.setShowClear()
        }

        mCrlFile = v.findViewById(R.id.crlfile)
        if (mCrlFile != null) {
            addFileSelectLayout(mCrlFile!!, FilePickerUtils.FileType.CRL_FILE)
            mCrlFile!!.setShowClear()
        }

        v.findViewById<View>(R.id.select_keystore_button).setOnClickListener(this)
        v.findViewById<View>(R.id.install_keystore_button).setOnClickListener {
            startActivity(KeyChain.createInstallIntent())
        }

        mAliasName = v.findViewById(R.id.aliasname)
        mAliasCertificate = v.findViewById(R.id.alias_certificate)
    }

    private fun addFileSelectLayout(fsl: FileSelectLayout, type: FilePickerUtils.FileType) {
        val i: Int = fileselects.size() + CHOOSE_FILE_OFFSET
        fileselects.put(i, fsl)
        fsl.setCaller(this, i, type)
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        if (parent === mAuthType) {
            changeType(position)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    private fun changeType(type: Int) {
        mLayout.findViewById<View>(R.id.userpassword)?.visibility = View.GONE
        mLayout.findViewById<View>(R.id.pkcs12)?.visibility = View.GONE
        mLayout.findViewById<View>(R.id.keystore)?.visibility = View.GONE

        mLayout.findViewById<View>(R.id.cacert)?.visibility = View.VISIBLE
        (mLayout.findViewById<View>(R.id.caselect) as FileSelectLayout).setClearable(true)

        when (type) {
            VpnProfile.TYPE_USERPASS_PKCS12 -> {
                mLayout.findViewById<View>(R.id.userpassword)?.visibility = View.VISIBLE
                mLayout.findViewById<View>(R.id.pkcs12)?.visibility = View.VISIBLE
            }
            VpnProfile.TYPE_USERPASS_KEYSTORE -> {
                mLayout.findViewById<View>(R.id.userpassword)?.visibility = View.VISIBLE
                mLayout.findViewById<View>(R.id.keystore)?.visibility = View.VISIBLE
            }
            VpnProfile.TYPE_PKCS12 -> {
                mLayout.findViewById<View>(R.id.pkcs12)?.visibility = View.VISIBLE
            }
            VpnProfile.TYPE_KEYSTORE -> {
                mLayout.findViewById<View>(R.id.keystore)?.visibility = View.VISIBLE
            }
            VpnProfile.TYPE_USERPASS -> {
                mLayout.findViewById<View>(R.id.userpassword)?.visibility = View.VISIBLE
            }
        }
    }

    override fun onClick(v: View) {
        if (v === v.findViewById<View>(R.id.select_keystore_button)) {
            try {
                KeyChain.choosePrivateKeyAlias(requireActivity(), { alias ->
                    // Credential alias selected.  Remember the alias selection for future use.
                    mProfile.alias = alias
                    mHandler.sendEmptyMessage(UPDATE_ALIAS)
                }, arrayOf("RSA", "EC"), null, null, -1, mProfile.alias)

            } catch (ex: ActivityNotFoundException) {
                val builder = AlertDialog.Builder(requireActivity())
                builder.setTitle(R.string.broken_image_cert_title)
                builder.setMessage(R.string.broken_image_cert)
                builder.setPositiveButton(android.R.string.ok, null)
                builder.show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        loadPreferences()
    }

    override fun savePreferences() {
        mProfile.authenticationType = mAuthType.selectedItemPosition
        if (mProfile.authenticationType == VpnProfile.TYPE_KEYSTORE || mProfile.authenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE) {
            if (mAliasName.tag == null)
                mProfile.alias = null
            else
                mProfile.alias = mAliasName.tag as String
//          mProfile.protectPassword = null

        } else {
//          mProfile.alias = null
            mProfile.protectPassword = mPkcs12Password.text.toString()
        }

        mProfile.pkcs12Filename = mPkcs12.data
        mProfile.caFilename = mCaCert?.data
        mProfile.crlFilename = mCrlFile?.data
    }

    override fun loadPreferences() {
        mAuthType.setSelection(mProfile.authenticationType)

        mPkcs12.setData(mProfile.pkcs12Filename, activity)
        mPkcs12Password.setText(mProfile.protectPassword)

        mCaCert?.setData(mProfile.caFilename, activity)
        mCrlFile?.setData(mProfile.crlFilename, activity)

        if (mProfile.authenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE ||
            mProfile.authenticationType == VpnProfile.TYPE_KEYSTORE) {
            setKeyStoreAlias()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode >= CHOOSE_FILE_OFFSET && resultCode == AppCompatActivity.RESULT_OK) {
            val fsl = fileselects[requestCode]
            fsl.parseResponse(data, activity)
            savePreferences()
        }
    }

}
