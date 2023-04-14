/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.security.KeyChainException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import de.blinkt.xp.openvpn.BuildConfig;
import de.blinkt.xp.openvpn.R;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.fragments.VPNProfileList;

public class OpenVPNUtils {

    // keytool -printcert -jarfile de.blinkt.openvpn_85.apk
    // keytool -list -v -keystore ics-openvpn.jks

    private static final byte[] OFFICIAL_SIGNER_FINGERPRINT = {
        (byte) 0x50, (byte) 0x48, (byte) 0x1F, (byte) 0x5A, (byte) 0xCB, (byte) 0x17, (byte) 0x14, (byte) 0xC3, (byte) 0xC1, (byte) 0xB8,
        (byte) 0x1A, (byte) 0xC0, (byte) 0xA1, (byte) 0x07, (byte) 0x90, (byte) 0x63, (byte) 0xF2, (byte) 0x57, (byte) 0x18, (byte) 0x39
    };

    private static String BUILD_FOR;
    private static String UNIQUE_PSUEDO_ID;

    public static String buildFor(@NonNull Context c) {
        if (BUILD_FOR == null) {
            synchronized (OpenVPNUtils.class) {
                if (BUILD_FOR == null)
                    BUILD_FOR = generateBuildFor(c);
            }
        }
        return BUILD_FOR;
    }

    public static String getUniquePsuedoID(@NonNull Context c) {
        if (UNIQUE_PSUEDO_ID == null) {
            synchronized (OpenVPNUtils.class) {
                if (UNIQUE_PSUEDO_ID == null)
                    UNIQUE_PSUEDO_ID = generateUniquePsuedoID(c);
            }
        }
        return UNIQUE_PSUEDO_ID;
    }

    private static String generateBuildFor(@NonNull Context c) {
        try {
            PackageInfo packageinfo = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES);

            if (packageinfo.signatures != null && packageinfo.signatures.length > 0) {
                @SuppressLint("PackageManagerGetSignatures")
                Signature raw = packageinfo.signatures[0];
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Certificate cert = cf.generateCertificate(new ByteArrayInputStream(raw.toByteArray()));
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(cert.getEncoded());
                byte[] digest = md.digest();

                if (Arrays.equals(digest, OpenVPNUtils.OFFICIAL_SIGNER_FINGERPRINT)) {
                    return c.getString(R.string.official_build);
                } else {
                    String friendlyName = X509Utils.getCertificateFriendlyName((X509Certificate) cert);
                    return c.getString(R.string.built_by, friendlyName);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return "error getting package signature";
    }

    private static String generateUniquePsuedoID(@NonNull Context c) {
        // If all else fails, if the user does have lower than API 9 (lower than Gingerbread),
        // has reset their device or 'Secure.ANDROID_ID' returns 'null', then simply the ID returned will be
        // solely based off their Android device information. This is where the collisions can happen.
        // Thanks http://www.pocketmagic.net/?p=1662!
        // Try not to use DISPLAY, HOST or ID - these items could change.
        // If there are collisions, there will be overlapping data
        String szDevIDShort = "35" + (Build.BOARD.length() % 10) + (Build.BRAND.length() % 10) + (Build.DEVICE.length() % 10)
            + (Build.MANUFACTURER.length() % 10) + (Build.MODEL.length() % 10) + (Build.PRODUCT.length() % 10);

        // Thanks to @Roman SL!
        // https://stackoverflow.com/a/4789483/950427
        // Only devices with API >= 9 have android.os.Build.SERIAL
        // http://developer.android.com/reference/android/os/Build.html#SERIAL
        // If a user upgrades software or roots their device, there will be a duplicate entry
        String serial = null;
        try {
            serial = android.os.Build.class.getField("SERIAL").get(null).toString();
            // Go ahead and return the serial for api => 9
            return new UUID(szDevIDShort.hashCode(), serial.hashCode()).toString();

        } catch (Exception ex) {
            // String needs to be initialized
            serial = "serial"; // some value
        }

        // Thanks @Joe!
        // https://stackoverflow.com/a/2853253/950427
        // Finally, combine the values we have found by using the UUID class to create a unique identifier
        return new UUID(szDevIDShort.hashCode(), serial.hashCode()).toString();
    }

    // 25 7.1.1 armeabi-v7a 360 sdm660 1801-A01
    public static String getPlatformInfoString(boolean escape) {
        String info = String.format(Locale.US, "%d %s %s %s %s %s", Build.VERSION.SDK_INT, Build.VERSION.RELEASE,
            NativeUtils.getNativeAPI(), Build.BRAND, Build.BOARD, Build.MODEL);
        return escape ? StringUtils.escape(info) : info;
    }

    // de.blinkt.openvpn 0.7.13
    public static String getProductInfoString(@NonNull Context c, boolean escape) {
        String version = "unknown";
        try {
            PackageInfo packageinfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = packageinfo.versionName;
        } catch (PackageManager.NameNotFoundException ex) {
            VpnStatus.logThrowable(ex);
        }

        String info = String.format(Locale.US, "%s %s", c.getPackageName(), version);
        return escape ? StringUtils.escape(info) : info;
    }

    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (Throwable ex) {
            // Ignore
        }
    }

    public static void sleepForDebug(int millis) {
        if (BuildConfig.DEBUG) {
            try {
                Thread.sleep(millis);
            } catch (Throwable ex) {
                // Ignore
            }
        }
    }

    public static void ensureNotOnMainThread(@NonNull Context context) {
        Looper looper = Looper.myLooper();
        if (looper != null && looper == context.getMainLooper()) {
            throw new IllegalStateException("calling this from your main thread can lead to deadlock");
        }
    }

    public static void loadCACerts(@NonNull Context context, @NonNull StringBuilder buffer) throws IOException {
        String[] cacerts = context.getAssets().list("ca_path");
        if (cacerts == null || cacerts.length == 0)
            return;

        for (String filename : cacerts) {
            if (filename.endsWith(".crt") || filename.endsWith(".cer") || filename.endsWith(".pem")) {
                if (!filename.startsWith("ca_path/"))
                    filename = "ca_path/" + filename;
                buffer.append(IOUtils.readAssertAsString(context, filename)).append(StringUtils.endWithNewLine(buffer) ? "" : "\n");
            }
        }
    }

    public static String generateConfig(@NonNull Context context, @NonNull VpnProfile profile)
            throws ConfigParser.ParseError, IllegalStateException {
        try {
            return profile.generateConfig(context);

        } catch (KeyChainException | InterruptedException | CertificateException | IOException ex) {
            throw new IllegalStateException("generate config fail" + ex.getMessage());
        }
    }

    public static void askProfileRemoval(@NonNull Activity activity, @NonNull VpnProfile profile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.confirm_deletion));
        builder.setMessage(activity.getString(R.string.remove_vpn_query, profile.getName()));
        builder.setPositiveButton(android.R.string.yes, (dialog, which) -> {
            ProfileManager.getInstance(activity).removeProfile(activity, profile);
            activity.setResult(VPNProfileList.RESULT_VPN_DELETED);
            activity.finish();
        });
        builder.setNegativeButton(android.R.string.no, null);

        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    public static void shareVpnProfile(@NonNull Activity activity, @NonNull VpnProfile profile) {
        String authority = activity.getPackageName() + ".VpnProfileProvider";
        File tempFile = exportVpnProfile(activity, profile, "mask_share.vpbf");
        Uri tempUri = FileProvider.getUriForFile(activity, authority, tempFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, tempUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.export_config_title));
        shareIntent.setType("*/*");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.export_config_chooser_title)));
    }

    public static File exportVpnProfile(@NonNull Context c, @NonNull VpnProfile profile, @NonNull String fileName) {
        File shareDir = new File(c.getCacheDir(), "share");
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }
        File exportFile = new File(shareDir, fileName);

        VpnProfile maskProfile = profile.copy("export_profile");
        maskVpnProfile(maskProfile);

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(exportFile))) {
            out.writeObject(maskProfile);
            out.flush();
            return exportFile;

        } catch (IOException ex) {
            VpnStatus.logThrowable("export VPN profile", ex);
            throw new RuntimeException(ex);
        }
    }

    public static boolean importVpnProfile(@NonNull Context c, @NonNull VpnProfile profile, boolean mask) {
        if (profile.getName() != null && profile.getUuid() != null) {
            if (!ProfileManager.getInstance(c).existProfile(profile)) {
                if (mask)
                    maskVpnProfile(profile);
                ProfileManager.getInstance(c).addProfile(profile);
                ProfileManager.getInstance(c).saveProfile(c, profile);
                ProfileManager.getInstance(c).saveProfileList(c);
                return true;
            }
        }
        return false;
    }

    private static void maskVpnProfile(@NonNull VpnProfile profile) {
        profile.setUsername(null);
        profile.setPassword(null);
        profile.setPkcs12Filename(null);
        profile.setAlias(null);
        profile.setProtectPassword(null);
    }

}
