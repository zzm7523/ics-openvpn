/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.api;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

import de.blinkt.openvpn.core.Preferences;

/**
 * Activity, Service 都使用了ExternalAppDatabase这个类, 目前Activity, Service配置在不同进程中运行
 * ExternalAppDatabase不适合有多个实例(实例一般都缓存有状态)，
 */

public final class ExternalAppDatabase {

    private static final String ALLOWED_APPS = "allowed_apps";

    private static Set<String> loadAllowedApps(@NonNull Context ctx) {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(ctx);
        // !!返回Set<String>副本
        return new HashSet<>(prefs.getStringSet(ALLOWED_APPS, new HashSet<>()));
    }

    private static void saveAllowedApps(@NonNull Context ctx, Set<String> allowedApps) {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putStringSet(ALLOWED_APPS, allowedApps == null ? new HashSet<>() : allowedApps);
        // Workaround for bug
        int counter = prefs.getInt("version", 0);
        editor.putInt("version", counter + 1);
        editor.apply();
    }

    public static Set<String> getAllowedApps(@NonNull Context ctx) {
        Set<String> allowedApps = loadAllowedApps(ctx);
        return allowedApps;
    }

    public static boolean isAllowedApp(@NonNull Context ctx, @NonNull String packageName) {
        Set<String> allowedApps = loadAllowedApps(ctx);
        return allowedApps.contains(packageName);
    }

    public static void addAllowedApp(@NonNull Context ctx, @NonNull String packageName) {
        Set<String> allowedApps = loadAllowedApps(ctx);
        allowedApps.add(packageName);
        saveAllowedApps(ctx, allowedApps);
    }

    public static void removeAllowedApp(@NonNull Context ctx, @NonNull String packageName) {
        Set<String> allowedApps = loadAllowedApps(ctx);
        allowedApps.remove(packageName);
        saveAllowedApps(ctx, allowedApps);
    }

    public static void clearAllowedApps(@NonNull Context ctx) {
        saveAllowedApps(ctx, new HashSet<>());
    }

    // 通过IOpenVPNAPIService接口(ExternalOpenVPNService实现)调用OpenVPN时, 检查调用者是否被允许
    public static String checkOpenVPNPermission(@NonNull Context ctx) throws SecurityException {
        PackageManager pm = ctx.getPackageManager();
        boolean allowAnyApp = false;
        Set<String> allowedApps;

        if (allowAnyApp) {
            allowedApps = new HashSet<>();
            if (pm != null) {
                for (ApplicationInfo appInfo : pm.getInstalledApplications(PackageManager.GET_META_DATA))
                    allowedApps.add(appInfo.packageName);
            }
        } else {
            allowedApps = loadAllowedApps(ctx);
        }

        if (pm != null) {
            for (String appPackage : allowedApps) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(appPackage, 0);
                    if (Binder.getCallingUid() == appInfo.uid)
                        return appPackage;

                } catch (PackageManager.NameNotFoundException ex) {
                    // App不存在, 不要从列表删除, 用户可能随后安装App
//                  removeAllowedApp(ctx, appPackage);
                }
            }
        }

        // SecurityException 会自动传递到远端
        throw new SecurityException("Unauthorized VPN API Caller");
    }

    // 通过RemoteAction(Activity)调用OpenVPN时, 检查调用者是否被授权
    public static boolean checkRemoteActionPermission(@NonNull Context ctx, String callingPackage) {
        if (callingPackage == null) {
            //callingPackage = ConfirmDialog.ANONYMOUS_PACKAGE;
            return false;
        }

        if (isAllowedApp(ctx, callingPackage)) {
            return true;
        }

        Intent confirmDialog = new Intent(ctx, ConfirmDialog.class);
        confirmDialog.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        confirmDialog.putExtra(ConfirmDialog.EXTRA_PACKAGE_NAME, callingPackage);
        ctx.startActivity(confirmDialog);
        return false;
    }

}
