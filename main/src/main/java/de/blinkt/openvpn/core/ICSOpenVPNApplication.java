/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.blinkt.xp.openvpn.R;

import java.security.Provider;
import java.security.Security;

import de.blinkt.openvpn.activities.BaseActivity;
import de.blinkt.openvpn.api.AppRestrictions;
import de.blinkt.openvpn.utils.CrashHandler;

public class ICSOpenVPNApplication extends Application {

    private static ActivityLifecycleCallbacks gLifecycleCallbacks;
    private static BaseActivity gCurrentBaseActivity;
    private static Activity gCurrentActivity;
    // 不要使用WeakReference, 防止gCurrentBaseActivity, gCurrentActivity被回收, 变成空指针
//  private static WeakReference<BaseActivity> gCurrentBaseActivity;
//  private static WeakReference<Activity> gCurrentActivity;

    private StatusListener mStatus;

    public static BaseActivity getCurrentBaseActivity() {
        synchronized (ActivityLifecycleCallbacks.class) {
            return gCurrentBaseActivity;
        }
    }

    public static Activity getCurrentActivity() {
        synchronized (ActivityLifecycleCallbacks.class) {
            return gCurrentActivity;
        }
    }

    private static void setCurrentActivity(@NonNull Activity activity) {
        synchronized (ActivityLifecycleCallbacks.class) {
            gCurrentActivity = activity;
            if (activity instanceof BaseActivity)
                gCurrentBaseActivity = (BaseActivity) activity;
        }
    }

    @Override
    public void onCreate() {
        if ("robolectric".equals(Build.FINGERPRINT))
            return;

        super.onCreate();

/*
        if (BuildConfig.DEBUG) {
            enableStrictModes();
        }
*/

        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(getApplicationContext());

        try {
            if (Security.getProvider(org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME) == null)
                Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());

            Provider provider = new org.conscrypt.OpenSSLProvider();
            if (Security.getProvider(provider.getName()) == null)
                Security.addProvider(provider);

//          PRNGFixes.apply();

        } catch (Exception ex) {
            // 忽略所有错误
            ex.printStackTrace();
        }

        if (gLifecycleCallbacks == null) {
            gLifecycleCallbacks = new ActivityLifecycleCallbacks();
            registerActivityLifecycleCallbacks(gLifecycleCallbacks);
        }

        mStatus = new StatusListener();

        new Thread(() -> {
            try {
                // !!注释掉, 不推荐使用ca_path参数, 启动连接时生成内联<ca>...</ca>
                // 安装或升级后第一次运行时, 导入CA证书
//              OpenVPNUtils.importCACerts(this);

                // 导致启动android:process=":openvpn"进程
                mStatus.init(this);

            } catch (Exception ex) {
                // 忽略所有错误
                ex.printStackTrace();
            }

        }).start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannels();

        AppRestrictions.getInstance(this).checkRestrictions(this);
    }

    /**
     * android.os.strictmode.InstanceCountViolation: class de.blinkt.openvpn.activities.GeneralActivity; instances=2; limit=1
     * 在Activity中创建了一个Thread匿名内部类，而匿名内部类隐式持有外部类的引用。而每次旋转屏幕是，Android会新创建一个Activity，
     * 而原来的Activity实例又被我们启动的匿名内部类线程持有，所以不会释放。
     */
    private void enableStrictModes() {
        StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder()
//          .detectActivityLeaks()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .detectLeakedRegistrationObjects()
            .detectFileUriExposure()
            .penaltyLog()
            .penaltyDeath()
            .build();
        StrictMode.setVmPolicy(vmPolicy);

        StrictMode.ThreadPolicy threadPolicy = new StrictMode.ThreadPolicy.Builder()
            .detectNetwork()
            .permitDiskReads()
            .permitDiskWrites()
            .build();
        StrictMode.setThreadPolicy(threadPolicy);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void createNotificationChannels() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Background message
        String name = getString(R.string.channel_name_background);
        NotificationChannel channel = new NotificationChannel(OpenVPNNotificationHelper.NOTIFICATION_CHANNEL_BG_ID,
            name, NotificationManager.IMPORTANCE_MIN);

        channel.setDescription(getString(R.string.channel_description_background));
        channel.enableLights(false);

        channel.setLightColor(Color.DKGRAY);
        manager.createNotificationChannel(channel);

        // Connection status change messages
        name = getString(R.string.channel_name_status);
        channel = new NotificationChannel(OpenVPNNotificationHelper.NOTIFICATION_CHANNEL_NEWSTATUS_ID,
            name, NotificationManager.IMPORTANCE_LOW);

        channel.setDescription(getString(R.string.channel_description_status));
        channel.enableLights(true);

        channel.setLightColor(Color.BLUE);
        manager.createNotificationChannel(channel);

        // Urgent requests, e.g. two factor auth
        name = getString(R.string.channel_name_userreq);
        channel = new NotificationChannel(OpenVPNNotificationHelper.NOTIFICATION_CHANNEL_USERREQ_ID, name,
            NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(getString(R.string.channel_description_userreq));
        channel.enableVibration(true);
        channel.setLightColor(Color.CYAN);
        manager.createNotificationChannel(channel);
    }

    private static class ActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            setCurrentActivity(activity);
        }

        public void onActivityStarted(@NonNull Activity activity) {
        }

        public void onActivityResumed(@NonNull Activity activity) {
            setCurrentActivity(activity);
        }

        public void onActivityPaused(@NonNull Activity activity) {
        }

        public void onActivityStopped(@NonNull Activity activity) {
        }

        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        public void onActivityDestroyed(@NonNull Activity activity) {
        }

    }

}
