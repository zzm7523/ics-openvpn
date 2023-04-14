/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities

import android.Manifest
import android.annotation.TargetApi
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.security.KeyChain
import android.text.TextUtils
import android.util.Base64
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.fragments.FilePickerUtils
import de.blinkt.openvpn.views.FileSelectLayout
import de.blinkt.openvpn.views.FileSelectLayout.FileSelectCallback
import de.blinkt.xp.openvpn.R

import java.io.*
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*

class ConfigConverter : BaseActivity(), FileSelectCallback, View.OnClickListener {

    private var mResult: VpnProfile? = null

    @Transient
    private var mPathsegments: List<String>? = null
    private var mAliasName: String? = null

    private val fileSelectMap = HashMap<FilePickerUtils.FileType, FileSelectLayout?>()
    private var mEmbeddedPwFile: String? = null
    private val mLogEntries = Vector<String>()
    private var mSourceUri: Uri? = null
    private lateinit var mProfilename: EditText
    private var mImportTask: AsyncTask<Void, Void, Int>? = null
    private lateinit var mLogLayout: LinearLayout
    private lateinit var mProfilenameLabel: TextView

    override fun onClick(v: View) {
        if (v.id == R.id.fab_save)
            userActionSaveProfile()
        if (v.id == R.id.permssion_hint && Build.VERSION.SDK_INT == Build.VERSION_CODES.M)
            doRequestSDCardPermission(PERMISSION_REQUEST_EMBED_FILES)
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun doRequestSDCardPermission(requestCode: Int) {
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Permission declined, do nothing
        if (grantResults.size == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED)
            return

        // Reset file select dialogs
        findViewById<View>(R.id.files_missing_hint).visibility = View.GONE
        findViewById<View>(R.id.permssion_hint).visibility = View.GONE
        val fileroot = findViewById<View>(R.id.config_convert_root) as LinearLayout
        var i = 0
        while (i < fileroot.childCount) {
            if (fileroot.getChildAt(i) is FileSelectLayout)
                fileroot.removeViewAt(i)
            else
                i++
        }

        if (requestCode == PERMISSION_REQUEST_EMBED_FILES)
            embedFiles(null)
        else if (requestCode == PERMISSION_REQUEST_READ_URL) {
            if (mSourceUri != null)
                doImportUri(mSourceUri!!)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.cancel) {
            setResult(AppCompatActivity.RESULT_CANCELED)
            finish()
        } else if (item.itemId == R.id.ok) {
            return userActionSaveProfile()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun userActionSaveProfile(): Boolean {
        if (mResult == null) {
            log(R.string.import_config_error)
            Toast.makeText(this, R.string.import_config_error, Toast.LENGTH_LONG).show()
            return true
        }

        if (ProfileManager.getInstance(this).existProfile(mResult!!)) {
            Toast.makeText(this, getString(R.string.config_already_exist, mResult!!.name), Toast.LENGTH_LONG).show()
            return true
        }

        mResult!!.name = mProfilename.text.toString()
        if (ProfileManager.getInstance(this).getProfileByName(mResult!!.name) != null) {
            mProfilename.error = getString(R.string.duplicate_profile_name)
            return true
        }

        val `in` = installPKCS12()

        if (`in` != null)
            startActivityForResult(`in`, RESULT_INSTALLPKCS12)
        else
            saveProfile()

        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mResult != null)
            outState.putSerializable(VPNPROFILE, mResult)
        outState.putString("mAliasName", mAliasName)

        val logentries = mLogEntries.toTypedArray()

        outState.putStringArray("logentries", logentries)

        val fileselects = IntArray(fileSelectMap.size)
        var k = 0
        for (key in fileSelectMap.keys) {
            fileselects[k] = key.value
            k++
        }
        outState.putIntArray("fileselects", fileselects)
        outState.putString("pwfile", mEmbeddedPwFile)
        outState.putParcelable("mSourceUri", mSourceUri)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        if (requestCode == RESULT_INSTALLPKCS12 && resultCode == AppCompatActivity.RESULT_OK) {
            showCertDialog()
        }

        if (resultCode == AppCompatActivity.RESULT_OK && requestCode >= CHOOSE_FILE_OFFSET) {
            val type = FilePickerUtils.FileType.getFileTypeByValue(requestCode - CHOOSE_FILE_OFFSET)

            val fs = fileSelectMap[type]
            fs!!.parseResponse(result, this)

            val data = fs.data

            when (type) {
                FilePickerUtils.FileType.USERPW_FILE -> mEmbeddedPwFile = data
                FilePickerUtils.FileType.PKCS12 -> mResult!!.pkcs12Filename = data
                FilePickerUtils.FileType.TLS_AUTH_FILE -> mResult!!.tlsAuthFilename = data
                FilePickerUtils.FileType.CERTIFICATE -> mResult!!.caFilename = data
                FilePickerUtils.FileType.CRL_FILE -> mResult!!.crlFilename = data
                else -> throw RuntimeException("Type is wrong somehow?")
            }
        }

        super.onActivityResult(requestCode, resultCode, result)
    }

    private fun saveProfile() {
        val result = Intent()
        val vpl = ProfileManager.getInstance(this)

        if (!TextUtils.isEmpty(mEmbeddedPwFile))
            ConfigParser.useEmbbedUserAuth(mResult!!, mEmbeddedPwFile!!)

        vpl.addProfile(mResult!!)
        vpl.saveProfile(this, mResult!!)
        vpl.saveProfileList(this)
        result.putExtra(VpnProfile.EXTRA_PROFILE_UUID, mResult!!.uuid.toString())
        setResult(AppCompatActivity.RESULT_OK, result)
        finish()
    }

    fun showCertDialog() {
        try {
            var serverName: String? = null
            var port: Int = -1

            KeyChain.choosePrivateKeyAlias(this, { alias ->
                    // Credential alias selected.  Remember the alias selection for future use.
                    mResult!!.alias = alias
                    saveProfile()
                },
                arrayOf("RSA", "EC"), null, serverName, port, mAliasName)

        } catch (anf: ActivityNotFoundException) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(R.string.broken_image_cert_title)
            builder.setMessage(R.string.broken_image_cert)
            builder.setPositiveButton(android.R.string.ok, null)
            builder.show()
        }
    }

    private fun installPKCS12(): Intent? {
        if (!VpnProfile.isEmbedded(mResult!!.pkcs12Filename))
            return null;

        if (!(findViewById<View>(R.id.importpkcs12) as CheckBox).isChecked) {
            if (mResult!!.authenticationType == VpnProfile.TYPE_KEYSTORE)
                mResult!!.authenticationType = VpnProfile.TYPE_PKCS12
            else if (mResult!!.authenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE)
                mResult!!.authenticationType = VpnProfile.TYPE_USERPASS_PKCS12

            return null

        } else {
            if (mResult!!.authenticationType == VpnProfile.TYPE_PKCS12)
                mResult!!.authenticationType = VpnProfile.TYPE_KEYSTORE
            else if (mResult!!.authenticationType == VpnProfile.TYPE_USERPASS_PKCS12)
                mResult!!.authenticationType = VpnProfile.TYPE_USERPASS_KEYSTORE

            var pkcs12datastr = VpnProfile.getEmbeddedContent(mResult!!.pkcs12Filename)
            val pkcs12data = Base64.decode(pkcs12datastr, Base64.DEFAULT)

            val inkeyIntent = KeyChain.createInstallIntent()
            inkeyIntent.putExtra(KeyChain.EXTRA_PKCS12, pkcs12data)

            if (mAliasName == "")
                mAliasName = null
            if (mAliasName != null)
                inkeyIntent.putExtra(KeyChain.EXTRA_NAME, mAliasName)

            return inkeyIntent
        }
    }

    private fun setUniqueProfileName(profile: VpnProfile) {
        var i = 0
        var newname = getString(R.string.converted_profile)

        // 如果mResult已经存在, 不允许导入; 提示已经存在
        if (ProfileManager.getInstance(this).existProfile(mResult!!))
            return

        while (newname == null || ProfileManager.getInstance(this).getProfileByName(newname) != null) {
            i++
            if (i == 1)
                newname = getString(R.string.converted_profile)
            else
                newname = getString(R.string.converted_profile_i, i)
        }

        profile.name = newname;
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.import_menu, menu)
        return true
    }

    private fun embedFile(filename: String?, type: FilePickerUtils.FileType, onlyFindFileAndNullonNotFound: Boolean): String? {
        if (filename == null)
            return null

        // Already embedded, nothing to do
        if (VpnProfile.isEmbedded(filename))
            return filename

        val possibleFile = findFile(filename, type)
        return if (possibleFile == null)
            if (onlyFindFileAndNullonNotFound)
                null
            else
                filename
        else if (onlyFindFileAndNullonNotFound)
            possibleFile.absolutePath
        else
            readFileContent(possibleFile, type == FilePickerUtils.FileType.PKCS12)
    }


    private fun getFileDialogInfo(type: FilePickerUtils.FileType): Pair<Int, String> {
        var titleRes = 0
        var value: String? = null

        when (type) {
            FilePickerUtils.FileType.CERTIFICATE -> {
                titleRes = R.string.ca_title
                if (mResult != null)
                    value = mResult!!.caFilename
            }
            FilePickerUtils.FileType.TLS_AUTH_FILE -> {
                titleRes = R.string.tls_auth_file
                if (mResult != null)
                    value = mResult!!.tlsAuthFilename
            }
            FilePickerUtils.FileType.PKCS12 -> {
                titleRes = R.string.client_pkcs12_title
                if (mResult != null)
                    value = mResult!!.pkcs12Filename
            }

            FilePickerUtils.FileType.USERPW_FILE -> {
                titleRes = R.string.userpw_file
                value = mEmbeddedPwFile
            }

            FilePickerUtils.FileType.CRL_FILE -> {
                titleRes = R.string.crl_file
                value = mResult!!.crlFilename
            }
            FilePickerUtils.FileType.OVPN_CONFIG -> TODO()
        }

        return Pair.create(titleRes, value)

    }

    private fun findFile(filename: String?, fileType: FilePickerUtils.FileType): File? {
        val foundfile = findFileRaw(filename)

        if (foundfile == null && filename != null && filename != "") {
            log(R.string.import_could_not_open, filename)
        }
        fileSelectMap[fileType] = null

        return foundfile
    }

    private fun addMissingFileDialogs() {
        for ((key, value) in fileSelectMap) {
            if (value == null)
                addFileSelectDialog(key)
        }
    }

    private fun addFileSelectDialog(type: FilePickerUtils.FileType?) {
        val fileDialogInfo = getFileDialogInfo(type!!)

        val isCert = (type == FilePickerUtils.FileType.CERTIFICATE)
        val fl = FileSelectLayout(this, getString(fileDialogInfo.first), isCert, false)
        fileSelectMap[type] = fl
        fl.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        (findViewById<View>(R.id.config_convert_root) as LinearLayout).addView(fl, 2)
        findViewById<View>(R.id.files_missing_hint).visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M)
            checkPermission()

        fl.setData(fileDialogInfo.second, this)
        val i = getFileLayoutOffset(type)
        fl.setCaller(this, i, type)
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun checkPermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            findViewById<View>(R.id.permssion_hint).visibility = View.VISIBLE
            findViewById<View>(R.id.permssion_hint).setOnClickListener(this)
        }
    }

    private fun getFileLayoutOffset(type: FilePickerUtils.FileType): Int {
        return CHOOSE_FILE_OFFSET + type.value
    }

    private fun findFileRaw(filename: String?): File? {
        if (filename == null || filename == "")
            return null

        // Try diffent path relative to /mnt/sdcard
        val sdcard = Environment.getExternalStorageDirectory()
        val root = File("/")

        val dirlist = HashSet<File>()

        for (i in mPathsegments!!.indices.reversed()) {
            var path = ""
            for (j in 0..i) {
                path += "/" + mPathsegments!![j]
            }
            // Do a little hackish dance for the Android File Importer
            // /document/primary:ovpn/openvpn-imt.conf

            if (path.indexOf(':') != -1 && path.lastIndexOf('/') > path.indexOf(':')) {
                var possibleDir = path.substring(path.indexOf(':') + 1, path.length)
                // Unquote chars in the  path
                try {
                    possibleDir = URLDecoder.decode(possibleDir, "UTF-8")
                } catch (ignored: UnsupportedEncodingException) {
                }

                possibleDir = possibleDir.substring(0, possibleDir.lastIndexOf('/'))

                dirlist.add(File(sdcard, possibleDir))
            }

            dirlist.add(File(path))
        }
        dirlist.add(sdcard)
        dirlist.add(root)

        val fileparts = filename.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (rootdir in dirlist) {
            var suffix = ""
            for (i in fileparts.indices.reversed()) {
                if (i == fileparts.size - 1)
                    suffix = fileparts[i]
                else
                    suffix = fileparts[i] + "/" + suffix

                val possibleFile = File(rootdir, suffix)
                if (possibleFile.canRead())
                    return possibleFile
            }
        }
        return null
    }

    internal fun readFileContent(possibleFile: File, base64encode: Boolean): String? {
        val filedata: ByteArray
        try {
            filedata = readBytesFromFile(possibleFile)
        } catch (e: IOException) {
            log(e.localizedMessage)
            return null
        }

        val data: String
        if (base64encode) {
            data = Base64.encodeToString(filedata, Base64.DEFAULT)
        } else {
            data = String(filedata)
        }

        return VpnProfile.DISPLAYNAME_TAG + possibleFile.name + VpnProfile.INLINE_TAG + data
    }


    @Throws(IOException::class)
    private fun readBytesFromFile(file: File): ByteArray {
        val input = FileInputStream(file)

        val len = file.length()
        if (len > VpnProfile.MAX_EMBED_FILE_SIZE)
            throw IOException("File size of file to import too large.")

        // Create the byte array to hold the data
        val bytes = ByteArray(len.toInt())

        // Read in the bytes
        var offset = 0
        var bytesRead: Int
        do {
            bytesRead = input.read(bytes, offset, bytes.size - offset)
            offset += bytesRead
        } while (offset < bytes.size && bytesRead >= 0)

        input.close()
        return bytes
    }

    internal fun embedFiles(cp: ConfigParser?) {
        // This where I would like to have a c++ style
        // void embedFile(std::string & option)
        if (mResult!!.pkcs12Filename != null) {
            val pkcs12file = findFileRaw(mResult!!.pkcs12Filename)
            if (pkcs12file != null) {
                mAliasName = pkcs12file.name.replace(".p12", "")
            } else {
                mAliasName = "Imported PKCS12"
            }
        }

        mResult!!.caFilename = embedFile(mResult!!.caFilename, FilePickerUtils.FileType.CERTIFICATE, false)
        mResult!!.tlsAuthFilename = embedFile(mResult!!.tlsAuthFilename, FilePickerUtils.FileType.TLS_AUTH_FILE, false)
        mResult!!.pkcs12Filename = embedFile(mResult!!.pkcs12Filename, FilePickerUtils.FileType.PKCS12, false)
        mResult!!.crlFilename = embedFile(mResult!!.crlFilename, FilePickerUtils.FileType.CRL_FILE, true)
        if (cp != null) {
            mEmbeddedPwFile = cp.authUserPassFile
            mEmbeddedPwFile = embedFile(cp.authUserPassFile, FilePickerUtils.FileType.USERPW_FILE, false)
        }
    }

    private fun updateFileSelectDialogs() {
        for ((key, value) in fileSelectMap) {
            value?.setData(getFileDialogInfo(key).second, this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.config_converter)
        setToolbar(R.id.toolbar)
        if (mToolbar != null) {
            mToolbar.setNavigationOnClickListener { v: View? -> finish() }
        }

        val fab_button = findViewById<ImageButton?>(R.id.fab_save)
        if (fab_button != null) {
            fab_button.setOnClickListener(this)
            findViewById<View>(R.id.fab_footerspace).visibility = View.VISIBLE
        }

        mLogLayout = findViewById<View>(R.id.config_convert_root) as LinearLayout
        mProfilename = findViewById<View>(R.id.profilename) as EditText
        mProfilenameLabel = findViewById<View>(R.id.profilename_label) as TextView

        if (savedInstanceState != null && savedInstanceState.containsKey(VPNPROFILE)) {
            mResult = savedInstanceState.getSerializable(VPNPROFILE) as VpnProfile?
            mAliasName = savedInstanceState.getString("mAliasName")
            mEmbeddedPwFile = savedInstanceState.getString("pwfile")
            mSourceUri = savedInstanceState.getParcelable("mSourceUri")
            mProfilename.setText(mResult!!.name)

            if (savedInstanceState.containsKey("logentries")) {
                for (logItem in savedInstanceState.getStringArray("logentries")!!)
                    log(logItem)
            }
            if (savedInstanceState.containsKey("fileselects")) {
                for (k in savedInstanceState.getIntArray("fileselects")!!) {
                    addFileSelectDialog(FilePickerUtils.FileType.getFileTypeByValue(k))
                }
            }
            return
        }

        if (intent != null) {
            doImportIntent(intent)
            // We parsed the intent, relay on saved instance for restoring
            intent = null
        }
    }

    private fun doImportIntent(intent: Intent) {
        if (intent.action.equals(Intent.ACTION_VIEW) || intent.action.equals(IMPORT_PROFILE)) {
            val data = intent.data
            if (data != null) {
                mSourceUri = data
                doImportUri(data)
            }
        } else if (intent.action.equals(IMPORT_PROFILE_DATA)) {
            val data = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (data != null) {
                startImportTask(Uri.fromParts("inline", "inlinetext", null),
                    "imported profiles from AS", false, data)
            }
        }
    }

    private fun doImportUri(data: Uri) {
        //log(R.string.import_experimental);
        log(R.string.importing_config, URLDecoder.decode(data.toString(), StandardCharsets.UTF_8.name()))
        var possibleName: String? = null
        var possibleBinary: Boolean = false

        if (TextUtils.equals (data.scheme, "file") || (data.lastPathSegment != null &&
                (data.lastPathSegment!!.endsWith(".vpbf") || data.lastPathSegment!!.endsWith(".ovpn") ||
                    data.lastPathSegment!!.endsWith(".conf")))) {
            possibleName = data.lastPathSegment
            possibleBinary = possibleName!!.endsWith(".vpbf")
            if (possibleName!!.lastIndexOf('/') != -1)
                possibleName = possibleName.substring(possibleName.lastIndexOf('/') + 1)
        }

        mPathsegments = data.pathSegments

        val cursor = contentResolver.query(data, null, null, null, null)

        try {
            if (cursor != null && cursor.moveToFirst()) {
                var columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex != -1) {
                    val displayName = cursor.getString(columnIndex)
                    if (displayName != null)
                        possibleName = displayName
                }
                columnIndex = cursor.getColumnIndex("mime_type")
                if (columnIndex != -1) {
                    log("Mime type: " + cursor.getString(columnIndex))
                }
            }

        } finally {
            cursor?.close()
        }

        if (possibleName != null) {
            possibleName = possibleName.replace(".vpbf", "")
            possibleName = possibleName.replace(".ovpn", "")
            possibleName = possibleName.replace(".conf", "")
        }
        startImportTask(data, possibleName, possibleBinary, "")
    }

    private fun startImportTask(data: Uri, possibleName: String?, possibleBinary: Boolean, inlineData: String) {
        mImportTask = object : AsyncTask<Void, Void, Int>() {
            private var mProgress: ProgressBar? = null

            override fun onPreExecute() {
                mProgress = ProgressBar(this@ConfigConverter)
                addViewToLog(mProgress)
            }

            override fun doInBackground(vararg params: Void): Int? {
                try {
                    val inputStream: InputStream?
                    if (data.scheme.equals("inline")) {
                        inputStream = inlineData.byteInputStream()
                    } else {
                        inputStream = contentResolver.openInputStream(data)
                    }

                    if (inputStream != null) {
                        if (possibleBinary)
                            doImportVpnProfile(inputStream)
                        else
                            doImportVpnConfig(inputStream)
                    }

                    if (mResult == null)
                        return -3

                } catch (se: IOException) {
                    log(R.string.import_content_resolve_error.toString() + ":" + se.localizedMessage)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        checkMarschmallowFileImportError(data)
                    return -2

                } catch (se: SecurityException) {
                    log(R.string.import_content_resolve_error.toString() + ":" + se.localizedMessage)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        checkMarschmallowFileImportError(data)
                    return -2
                }

                return 0
            }

            override fun onPostExecute(errorCode: Int?) {
                mLogLayout.removeView(mProgress)
                addMissingFileDialogs()
                updateFileSelectDialogs()

                if (errorCode == 0) {
                    log(R.string.import_done)
                    displayWarnings()
                    setUniqueProfileName(mResult!!)
                    mProfilename.setText(mResult!!.name)
                    mProfilename.visibility = View.VISIBLE
                    mProfilenameLabel.visibility = View.VISIBLE
                }
            }
        }.execute()
    }


    @TargetApi(Build.VERSION_CODES.M)
    private fun checkMarschmallowFileImportError(data: Uri?) {
        // Permission already granted, not the source of the error
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            return

        // We got a file:/// URL and have no permission to read it. Technically an error of the calling app since
        // it makes an assumption about other apps being able to read the url but well ...
        if (data != null && "file" == data.scheme)
            doRequestSDCardPermission(PERMISSION_REQUEST_READ_URL)
    }

    private fun log(logmessage: String?) {
        runOnUiThread {
            val tv = TextView(this@ConfigConverter)
            mLogEntries.add(logmessage)
            tv.text = logmessage
            addViewToLog(tv)
        }
    }

    private fun addViewToLog(view: View?) {
        mLogLayout.addView(view, mLogLayout.childCount - 1)
    }

    private fun doImportVpnProfile(inputStream: InputStream) {
        try {
            val objIn = ObjectInputStream(inputStream)
            mResult = objIn.readObject() as VpnProfile
            embedFiles(null)
            return;

        } catch (ex: IOException) {
            log(R.string.error_reading_config_file)
            log(ex.localizedMessage)
        } catch (ex: ClassNotFoundException) {
            log(R.string.error_reading_config_file)
            log(ex.localizedMessage)
        } finally {
            inputStream.close()
        }

        mResult = null
    }

    private fun doImportVpnConfig(inputStream: InputStream) {
        try {
            val isr = InputStreamReader(inputStream)
            val parser = ConfigParser()
            parser.parseConfig(isr)
            mResult = parser.convertProfile()
            embedFiles(parser)
            return

        } catch (ex: ConfigParser.ParseError) {
            log(R.string.error_reading_config_file)
            log(ex.localizedMessage)
        } catch (ex: IOException) {
            log(R.string.error_reading_config_file)
            log(ex.localizedMessage)
        } finally {
            inputStream.close()
        }

        mResult = null
    }

    private fun displayWarnings() {
        if (mResult!!.isUseCustomConfig) {
            log(R.string.import_warning_custom_options)
            var copt = mResult!!.customConfigOptions
            if (copt.startsWith("#")) {
                val until = copt.indexOf('\n')
                copt = copt.substring(until + 1)
            }

            log(copt)
        }

        if (VpnProfile.isEmbedded(mResult!!.pkcs12Filename)) {
            findViewById<View>(R.id.importpkcs12).visibility = View.VISIBLE
        }
    }

    private fun log(ressourceId: Int, vararg formatArgs: Any) {
        log(getString(ressourceId, *formatArgs))
    }

    companion object {
        @kotlin.jvm.JvmField
        val IMPORT_PROFILE = "de.blinkt.openvpn.IMPORT_PROFILE"
        val IMPORT_PROFILE_DATA = "de.blinkt.openvpn.IMPORT_PROFILE_DATA"
        private val RESULT_INSTALLPKCS12 = 7
        private val CHOOSE_FILE_OFFSET = 1000
        val VPNPROFILE = "vpnProfile"
        private val PERMISSION_REQUEST_EMBED_FILES = 37231
        private val PERMISSION_REQUEST_READ_URL = PERMISSION_REQUEST_EMBED_FILES + 1
    }

}
