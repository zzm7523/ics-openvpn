/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.blinkt.xp.openvpn.BuildConfig;
import de.blinkt.xp.openvpn.R;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.utils.IOUtils;
import de.blinkt.openvpn.utils.NativeUtils;

public class OpenVPNLaunchHelper {

    public static final String OVPNCONFIGFILE = "android.conf";

    // !! 仅libXXXXX.so形式的文件会部署在Context.getApplicationInfo().nativeLibraryDir目录
    public static final String LIBOVPNEXEC = "libovpnexec.so";
    public static final String MINIPIEVPN = "pie_openvpn";

    private static final String TAG = "OpenVPNLaunchHelper";

    public static String writeMiniOpenVPN(@NonNull Context context) throws IOException {
        /* Q does not allow executing binaries written in temp directory anymore */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return new File(context.getApplicationInfo().nativeLibraryDir, LIBOVPNEXEC).getCanonicalPath();
        }

        String nativeAPI = NativeUtils.getNativeAPI();
        String[] abis = Build.SUPPORTED_ABIS;
        if (!nativeAPI.equals(abis[0])) {
            VpnStatus.logWarning(R.string.abi_mismatch, Arrays.toString(abis), nativeAPI);
            abis = new String[]{nativeAPI};
        }

        for (String abi : abis) {
            File minivpnFile = new File(context.getCacheDir(), MINIPIEVPN + "." + abi);
            if ((minivpnFile.exists() && minivpnFile.canExecute()) || writeBinary(context, MINIPIEVPN, abi)) {
                return minivpnFile.getPath();
            }
        }

        throw new RuntimeException("Cannot find any execute for this device's ABIs " + TextUtils.join(",", abis));
    }

    // !! 仅libXXXXX.so形式的文件会部署在Context.getApplicationInfo().nativeLibraryDir目录
    public static String writeFatOpenVPN(@NonNull Context context) throws IOException {
        /* Q does not allow executing binaries written in temp directory anymore */
        return new File(context.getApplicationInfo().nativeLibraryDir, "libfatopenvpn.so").getCanonicalPath();
    }

    public static String writeGdbServer(@NonNull Context context) throws IOException {
        /* Q does not allow executing binaries written in temp directory anymore */
        return new File(context.getApplicationInfo().nativeLibraryDir, "libgdbserver.so").getCanonicalPath();
    }

    private static boolean writeBinary(@NonNull Context context, @NonNull String binName, @NonNull String abi) {
        File outBinFile = new File(context.getCacheDir(), binName + "." + abi);

        try (OutputStream output = new FileOutputStream(outBinFile)) {
            try (InputStream input = context.getAssets().open(binName + "." + abi)) {
                IOUtils.copy(input, output);
                if (!outBinFile.setExecutable(true)) {
                    VpnStatus.logError("Failed to make " + binName + "." + abi + " executable");
                    return false;
                }
                return true;

            } catch (IOException ex) {
                VpnStatus.logInfo("Failed getting assets for archicture " + binName + "." + abi);
                return false;
            }

        } catch (IOException ex) {
            VpnStatus.logThrowable(ex);
            return false;
        }
    }

    public static void startOpenVpn(@NonNull Context context, @NonNull VpnProfile profile) {
        Intent intent = new Intent(context, OpenVPNService.class);
        intent.putExtra(VpnProfile.EXTRA_PROFILE_UUID, profile.getUUIDString());
        intent.putExtra(VpnProfile.EXTRA_PROFILE_VERSION, profile.getVersion());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // noinspection NewApi
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static File getConfigFile(@NonNull Context context) {
        return new File(context.getCacheDir(), OVPNCONFIGFILE);
    }

    public static String[] buildOpenVPNArgv(@NonNull Context context, @NonNull VpnProfile profile, @NonNull Intent intent) {
        List<String> args = new ArrayList<>();

        try {
            if (BuildConfig.DEBUG && false) {
                String gdbserverName = writeGdbServer(context);
                args.add(gdbserverName);
                args.add(":9999");
                String openvpnName = writeFatOpenVPN(context);
                args.add(openvpnName);

            } else {
                String openvpnName = writeMiniOpenVPN(context);
                args.add(openvpnName);
            }

        } catch (Exception ex) {
            Log.d(TAG, "Error writing minivpn binary, " + ex.getMessage());
            VpnStatus.logError("Error writing minivpn binary, " + ex.getMessage());
            return null;
        }

        try {
            args.add("--config");
            args.add(getConfigFile(context).getCanonicalPath());
            return args.toArray(new String[args.size()]);

        } catch (Exception ex) {
            Log.d(TAG, "generate openvpn command line fail, " + ex.getMessage());
            VpnStatus.logError("generate openvpn command line fail, " + ex.getMessage());
            return null;
        }
    }

}
