/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.NonNull;

import de.blinkt.xp.openvpn.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.activities.DisconnectVPN;

public class OpenVPNNotificationHelper {

    public static final String NOTIFICATION_CHANNEL_BG_ID = "openvpn_bg";
    public static final String NOTIFICATION_CHANNEL_NEWSTATUS_ID = "openvpn_newstat";
    public static final String NOTIFICATION_CHANNEL_USERREQ_ID = "openvpn_userreq";

    public static final String EXTRA_CHALLENGE_TXT = "de.blinkt.openvpn.core.CR_TEXT_CHALLENGE";

    private static final int PRIORITY_DEFAULT = 0;
    private static final int PRIORITY_MIN = -2;
    private static final int PRIORITY_MAX = 2;

    private static final String PAUSE_VPN = "de.blinkt.openvpn.PAUSE_VPN";
    private static final String RESUME_VPN = "de.blinkt.openvpn.RESUME_VPN";

    private final OpenVPNService service;
    private final Handler guiHandler;
    private Toast mlastToast;
    private String lastChannel;


    public OpenVPNNotificationHelper(@NonNull OpenVPNService service) {
        this.service = service;
        this.guiHandler = new Handler(service.getMainLooper());
    }

    public void showNotification(@NonNull VpnProfile profile, final String msg, String tickerText, @NonNull String channel,
                                 long when, int status, Intent intent) {
        int priority;
        if (channel.equals(NOTIFICATION_CHANNEL_BG_ID))
            priority = PRIORITY_MIN;
        else if (channel.equals(NOTIFICATION_CHANNEL_USERREQ_ID))
            priority = PRIORITY_MAX;
        else
            priority = PRIORITY_DEFAULT;

        Notification.Builder builder = new Notification.Builder(service);
        if (profile != null)
            builder.setContentTitle(service.getString(R.string.notifcation_title, profile.getName()));
        else
            builder.setContentTitle(service.getString(R.string.notifcation_title_notconnect));

        builder.setContentText(msg);
        builder.setOnlyAlertOnce(true);
        builder.setOngoing(true);

        int icon = getIconByConnectionStatus(status);
        builder.setSmallIcon(icon);
        if (status == ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT) {
            PendingIntent pIntent = PendingIntent.getActivity(service, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(pIntent);
        } else {
            builder.setContentIntent(service.getGraphPendingIntent());
        }

        if (when != 0)
            builder.setWhen(when);

        setNotificationExtras(priority, Notification.CATEGORY_SERVICE, builder);
        addVpnActionsToNotification(builder);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //noinspection NewApi
            builder.setChannelId(channel);
            if (profile != null)
                //noinspection NewApi
                builder.setShortcutId(profile.getUUIDString());
        }

        if (tickerText != null && !tickerText.equals(""))
            builder.setTicker(tickerText);

        @SuppressWarnings("deprecation")
        Notification notification = builder.getNotification();
        int notificationId = channel.hashCode();

        NotificationManager notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification);

        service.startForeground(notificationId, notification);

        if (lastChannel != null && !channel.equals(lastChannel)) {
            // Cancel old notification
            notificationManager.cancel(lastChannel.hashCode());
        }

        // Check if running on a TV
        if (runningOnAndroidTV() && !(priority < 0)) {
            guiHandler.post(() -> {
                if (mlastToast != null)
                    mlastToast.cancel();
                String toastText = String.format(Locale.getDefault(), "%s - %s", profile.getName(), msg);
                mlastToast = Toast.makeText(service.getBaseContext(), toastText, Toast.LENGTH_SHORT);
                mlastToast.show();
            });
        }
    }

    private int getIconByConnectionStatus(int level) {
        switch (level) {
            case ConnectionStatus.LEVEL_CONNECTED:
                return R.drawable.ic_stat_vpn;
            case ConnectionStatus.LEVEL_AUTH_FAILED:
            case ConnectionStatus.LEVEL_NONETWORK:
            case ConnectionStatus.LEVEL_NOTCONNECTED:
                return R.drawable.ic_stat_vpn_offline;
            case ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
            case ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT:
                return R.drawable.ic_stat_vpn_outline;
            case ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED:
                return R.drawable.ic_stat_vpn_empty_halo;
            case ConnectionStatus.LEVEL_VPNPAUSED:
                return android.R.drawable.ic_media_pause;
            case ConnectionStatus.LEVEL_UNKNOWN:
            default:
                return R.drawable.ic_stat_vpn;
        }
    }

    private boolean runningOnAndroidTV() {
        UiModeManager uiModeManager = (UiModeManager) service.getSystemService(Service.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private void setNotificationExtras(int priority, String category, Notification.Builder builder) {
        try {
            if (priority != 0) {
                Method setPriority = builder.getClass().getMethod("setPriority", int.class);
                setPriority.invoke(builder, priority);

                Method setUsesChronometer = builder.getClass().getMethod("setUsesChronometer", boolean.class);
                setUsesChronometer.invoke(builder, true);
            }

            builder.setCategory(category);
            builder.setLocalOnly(true);

        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
            //ignore exception
            VpnStatus.logThrowable(e);
        }
    }

    private void addVpnActionsToNotification(Notification.Builder builder) {
        Intent disconnectVPN = new Intent(service, DisconnectVPN.class);
        disconnectVPN.setAction(OpenVPNService.DISCONNECT_VPN);
        PendingIntent disconnectPendingIntent = PendingIntent.getActivity(service, 0, disconnectVPN, PendingIntent.FLAG_IMMUTABLE);

        builder.addAction(R.drawable.ic_menu_close_clear_cancel, service.getString(R.string.cancel_connection), disconnectPendingIntent);

        DeviceStateReceiver stateReceiver = service.getDeviceStateReceiver();
        Intent pauseVPN = new Intent(service, OpenVPNService.class);
        if (stateReceiver == null || !stateReceiver.isUserPaused()) {
            pauseVPN.setAction(PAUSE_VPN);
            PendingIntent pauseVPNPending = PendingIntent.getService(service, 0, pauseVPN, PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_menu_pause, service.getString(R.string.pauseVPN), pauseVPNPending);
        } else {
            pauseVPN.setAction(RESUME_VPN);
            PendingIntent resumeVPNPending = PendingIntent.getService(service, 0, pauseVPN, PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_menu_play, service.getString(R.string.resumevpn), resumeVPNPending);
        }
    }

    public void trigger_sso(@NonNull String info) {
        String channel = NOTIFICATION_CHANNEL_USERREQ_ID;
        String method = info.split(":", 2)[0];

        Notification.Builder builder = new Notification.Builder(service);
        builder.setAutoCancel(true);
        int icon = android.R.drawable.ic_dialog_info;
        builder.setSmallIcon(icon);

        Intent intent;
        int reason;

        if (method.equals("OPEN_URL")) {
            String url = info.split(":", 2)[1];
            reason = R.string.openurl_requested;
            builder.setContentTitle(service.getString(reason));
            builder.setContentText(url);

            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        } else if (method.equals("CR_TEXT")) {
            String challenge = info.split(":", 2)[1];
            reason = R.string.crtext_requested;
            builder.setContentTitle(service.getString(reason));
            builder.setContentText(challenge);

            intent = new Intent();
            intent.setComponent(new ComponentName(service, "de.blinkt.openvpn.activities.CredentialsPopup"));
            intent.putExtra(EXTRA_CHALLENGE_TXT, challenge);

        } else {
            VpnStatus.logError("Unknown SSO method found: " + method);
            return;
        }

        // updateStateString trigger the notification of the VPN to be refreshed, save this intent
        // to have that notification also this intent to be set
        PendingIntent pIntent = PendingIntent.getActivity(service, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        VpnStatus.updateStatus("USER_INPUT", "waiting for user input", reason, intent);
        builder.setContentIntent(pIntent);

        setNotificationExtras(PRIORITY_MAX, Notification.CATEGORY_STATUS, builder);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //noinspection NewApi
            builder.setChannelId(channel);
        }

        @SuppressWarnings("deprecation")
        Notification notification = builder.getNotification();
        int notificationId = channel.hashCode();

        NotificationManager notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification);
    }

}
