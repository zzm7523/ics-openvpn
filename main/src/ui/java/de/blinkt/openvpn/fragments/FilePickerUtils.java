/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.utils.IOUtils;

public class FilePickerUtils {

    public static Intent getFilePickerIntent(@NonNull Context c, @NonNull FileType fileType) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        Set<String> supportedMimeTypes = new TreeSet<>();
        List<String> extensions = new ArrayList<>();

        switch (fileType) {
            case PKCS12:
                intent.setType("application/x-pkcs12");
                supportedMimeTypes.add("application/x-pkcs12");
                extensions.add("p12");
                extensions.add("pfx");
                break;

            case CERTIFICATE:
                intent.setType("application/x-pem-file");
                supportedMimeTypes.add("application/x-x509-ca-cert");
                supportedMimeTypes.add("application/x-x509-user-cert");
                supportedMimeTypes.add("application/x-pem-file");
                supportedMimeTypes.add("application/pkix-cert");
                supportedMimeTypes.add("text/plain");
                extensions.add("pem");
                extensions.add("crt");
                extensions.add("cer");
                break;

            case TLS_AUTH_FILE:
                intent.setType("text/plain");
                // Backup ....
                supportedMimeTypes.add("application/pkcs8");
                // Google Drive is kind of crazy .....
                supportedMimeTypes.add("application/x-iwork-keynote-sffkey");
                extensions.add("txt");
                extensions.add("key");
                break;

            case OVPN_CONFIG:
                intent.setType("application/x-openvpn-profile");
                supportedMimeTypes.add("application/x-openvpn-profile");
                supportedMimeTypes.add("application/openvpn-profile");
                supportedMimeTypes.add("application/ovpn");
                supportedMimeTypes.add("text/plain");
                extensions.add("vpbf");
                extensions.add("ovpn");
                extensions.add("conf");
                break;

            case CRL_FILE:
                intent.setType("application/x-pem-file");
                supportedMimeTypes.add("application/x-pkcs7-crl");
                supportedMimeTypes.add("application/pkix-crl");
                extensions.add("crl");
                break;

            case USERPW_FILE:
                intent.setType("text/plain");
                supportedMimeTypes.add("text/plain");
                break;
        }

        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();

        for (String ext : extensions) {
            String mimeType = mimeTypeMap.getMimeTypeFromExtension(ext);
            if (mimeType != null)
                supportedMimeTypes.add(mimeType);
        }

        // Always add this as fallback
        supportedMimeTypes.add("application/octet-stream");

        intent.putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes.toArray(new String[supportedMimeTypes.size()]));

        // People don't know that this is actually a system setting. Override it ...
        // DocumentsContract.EXTRA_SHOW_ADVANCED is hidden
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        /* Samsung has decided to do something strange, on stock Android GET_CONTENT opens the document UI */
        /* fist try with documentsui */
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N)
            intent.setPackage("com.android.documentsui");

        //noinspection ConstantConditions
        if (!isIntentAvailable(c, intent)) {
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.setPackage(null);

            // Check for really broken devices ... :(
            if (!isIntentAvailable(c, intent)) {
                return null;
            }
        }

        return intent;
    }

    private static boolean isIntentAvailable(@NonNull Context context, @NonNull Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        // Ignore the Android TV framework app in the list
        int size = list.size();
        for (ResolveInfo ri : list) {
            // Ignore stub apps
            if ("com.google.android.tv.frameworkpackagestubs".equals(ri.activityInfo.packageName)) {
                size--;
            }
        }

        return size > 0;
    }

    public static String getFilePickerResult(@NonNull FileType fileType, @NonNull Intent result, @NonNull Context c)
            throws IOException, SecurityException {
        Uri uri = result.getData();
        if (uri == null)
            return null;

        byte[] fileData = IOUtils.readStream(c.getContentResolver().openInputStream(uri), VpnProfile.MAX_EMBED_FILE_SIZE);
        String newData = null;

        Cursor cursor = c.getContentResolver().query(uri, null, null, null, null);

        String prefix = "";
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int cidx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (cidx != -1) {
                    String displayName = cursor.getString(cidx);
                    if (!displayName.contains(VpnProfile.INLINE_TAG) && !displayName.contains(VpnProfile.DISPLAYNAME_TAG))
                        prefix = VpnProfile.DISPLAYNAME_TAG + displayName;
                }
            }

        } finally {
            if (cursor != null)
                cursor.close();
        }

        switch (fileType) {
            case PKCS12:
                newData = Base64.encodeToString(fileData, Base64.DEFAULT);
                break;
            default:
                newData = new String(fileData, StandardCharsets.UTF_8);
                break;
        }

        return prefix + VpnProfile.INLINE_TAG + newData;
    }


    public enum FileType {
        PKCS12(0),
        CERTIFICATE(1),
        OVPN_CONFIG(2),
        TLS_AUTH_FILE(3),
        USERPW_FILE(4),
        CRL_FILE(5);

        private int value;

        FileType(int i) {
            value = i;
        }

        public int getValue() {
            return value;
        }

        public static FileType getFileTypeByValue(int value) {
            switch (value) {
                case 0:
                    return PKCS12;
                case 1:
                    return CERTIFICATE;
                case 2:
                    return OVPN_CONFIG;
                case 3:
                    return TLS_AUTH_FILE;
                case 4:
                    return USERPW_FILE;
                case 5:
                    return CRL_FILE;
                default:
                    return null;
            }
        }

    }

}
