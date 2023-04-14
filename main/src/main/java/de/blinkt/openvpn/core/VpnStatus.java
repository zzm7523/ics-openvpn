/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import de.blinkt.xp.openvpn.R;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.blinkt.openvpn.utils.NativeUtils;

/**
 * 追踪并且记录和转发OpenVPN日志、状态、流量
 */

public class VpnStatus {

    public interface StatusListener {
        void updateStatus(@NonNull ConnectionStatus status, Bundle extra);
        void setConnectedVPN(String uuid);
    }

    public interface LogListener {
        void newLog(@NonNull LogItem logItem);
    }

    public interface ByteCountListener {
        void updateByteCount(long in, long out, long diffIn, long diffOut);
    }


    public static final int MAX_LOGE_NTRIES = 1024;

    // mLastStatus 为全局共享, 使用需持有STATUS_LOCK锁
    public static final Object STATUS_LOCK = new Object();
    public static final ConnectionStatus LAST_CONNECTION_STATUS = new ConnectionStatus("NOPROCESS", R.string.state_noprocess);
    public static final VpnTunnel LAST_VPN_TUNNEL = new VpnTunnel();
    public static final ArrayList<AccessibleResource> LAST_ACCESSIBLE_RESOURCES = new ArrayList<>();
    private static final List<StatusListener> gStatusListeners = new ArrayList<>();
    private static Intent gLastIntent = null;
    private static String gLastConnectedVPNUUID;

    // logBufferAll, logBufferMap 为全局共享, 使用需持有LOG_LOCK锁
    public static final Object LOG_LOCK = new Object();
    private static final List<LogListener> gLogListeners = new ArrayList<>();
    private static final LinkedList<LogItem> gLogBufferAll = new LinkedList<>();
    private static final Map<LogSource, LinkedList<LogItem>> gLogBufferMap = new HashMap<>();

    // trafficHistory 为全局共享, 使用需持有TRAFFIC_LOCK锁
    public static final Object TRAFFIC_LOCK = new Object();
    public static final TrafficHistory TRAFFIC_HISTORY = new TrafficHistory();
    private static final List<ByteCountListener> gByteCountListeners = new ArrayList<>();


    public static void addByteCountListener(@NonNull ByteCountListener bcl) {
        synchronized (TRAFFIC_LOCK) {
            TrafficHistory.LastDiff diff = TRAFFIC_HISTORY.getLastDiff(null);
            bcl.updateByteCount(diff.getIn(), diff.getOut(), diff.getDiffIn(), diff.getDiffOut());
            gByteCountListeners.add(bcl);
        }
    }

    public static void removeByteCountListener(@NonNull ByteCountListener bcl) {
        synchronized (TRAFFIC_LOCK) {
            gByteCountListeners.remove(bcl);
        }
    }

    public static void addLogListener(@NonNull LogListener ll) {
        synchronized (LOG_LOCK) {
            gLogListeners.add(ll);
        }
    }

    public static void removeLogListener(@NonNull LogListener ll) {
        synchronized (LOG_LOCK) {
            gLogListeners.remove(ll);
        }
    }

    public static void addStatusListener(@NonNull StatusListener sl) {
        synchronized (STATUS_LOCK) {
            if (!gStatusListeners.contains(sl)) {
                gStatusListeners.add(sl);

                Bundle extra = new Bundle();
                extra.putParcelable("LAST_INTENT", gLastIntent);
                if (LAST_CONNECTION_STATUS.getLevel() == ConnectionStatus.LEVEL_CONNECTED) {
                    extra.putParcelable("LAST_VPN_TUNNEL", LAST_VPN_TUNNEL);
                    extra.putSerializable("LAST_ACCESSIBLE_RESOURCES", LAST_ACCESSIBLE_RESOURCES);
                }
                sl.updateStatus(LAST_CONNECTION_STATUS, extra);
            }
        }
    }

    public static void removeStatusListener(@NonNull StatusListener sl) {
        synchronized (STATUS_LOCK) {
            gStatusListeners.remove(sl);
        }
    }

    public static boolean isVPNActive() {
        synchronized (STATUS_LOCK) {
            return LAST_CONNECTION_STATUS.getLevel() != ConnectionStatus.LEVEL_GENERAL_ERROR &&
                LAST_CONNECTION_STATUS.getLevel() != ConnectionStatus.LEVEL_AUTH_FAILED &&
                LAST_CONNECTION_STATUS.getLevel() != ConnectionStatus.LEVEL_NOTCONNECTED;
        }
    }

    public static boolean isVPNConnected(String uuid) {
        synchronized (STATUS_LOCK) {
            return LAST_CONNECTION_STATUS.getLevel() == ConnectionStatus.LEVEL_CONNECTED &&
                (TextUtils.isEmpty(uuid) || TextUtils.equals(gLastConnectedVPNUUID, uuid));
        }
    }

    public static void setConnectedVpnProfile(String uuid) {
        synchronized (STATUS_LOCK) {
            gLastConnectedVPNUUID = uuid;
            for (StatusListener sl : gStatusListeners) {
                sl.setConnectedVPN(uuid);
            }
        }
    }

    public static String getConnectedVpnProfile() {
        synchronized (STATUS_LOCK) {
            return gLastConnectedVPNUUID;
        }
    }

    // 调用需持有LOG_LOCK锁
    public static LinkedList<LogItem> getLogBufferAll() {
        return gLogBufferAll;
    }

    // 调用需持有LOG_LOCK锁
    public static LinkedList<LogItem> getLogBuffer(@NonNull LogSource source) {
        LinkedList<LogItem> logBuffer = gLogBufferMap.get(source);
        if (logBuffer == null) {
            logBuffer = new LinkedList<>();
            gLogBufferMap.put(source, logBuffer);
        }
        return logBuffer;
    }

    public static void updateStatusPause(@NonNull OpenVPNManagement.PauseReason pauseReason) {
        switch (pauseReason) {
            case noNetwork:
                VpnStatus.updateStatus("NONETWORK", R.string.state_nonetwork);
                break;
            case screenOff:
                VpnStatus.updateStatus("SCREENOFF", R.string.state_screenoff);
                break;
            case userPause:
                VpnStatus.updateStatus("USERPAUSE", R.string.state_userpause);
                break;
        }
    }

    public static void updateStatus(@NonNull String status, String msg, int resid) {
        updateStatus(status, msg, resid, null);
    }

    public static void updateStatus(@NonNull String status, int resid) {
        updateStatus(status, "", resid, null);
    }

    public static void updateStatus(@NonNull String status, String msg, int resid, Intent intent) {
        int lastLevel;
        synchronized (STATUS_LOCK) {
            lastLevel = ConnectionStatus.getLevel(LAST_CONNECTION_STATUS);
        }

        // We want to skip announcing that we are trying to get the configuration since
        // this is just polling until the user input has finished.be
        if (lastLevel == ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT && status.equals("GET_CONFIG"))
            return;

        // Workound for OpenVPN doing AUTH and wait and being connected Simply ignore these state
        if (lastLevel == ConnectionStatus.LEVEL_CONNECTED && (status.equals("WAIT") || status.equals("AUTH"))) {
            newLogItem(new LogItem(LogSource.OPENVPN_FRONT, LogLevel.DEBUG,
                String.format("Ignoring OpenVPN Status in CONNECTED state (%s->%s): %s",
                    status, ConnectionStatus.getLevelString(ConnectionStatus.getLevel(status)), msg)));
            return;
        }

        synchronized (STATUS_LOCK) {
            LAST_CONNECTION_STATUS.setStatus(status);
            LAST_CONNECTION_STATUS.setMessage(msg);
            LAST_CONNECTION_STATUS.setResid(resid);
            gLastIntent = intent;

            Bundle extra = new Bundle();
            extra.putParcelable("LAST_INTENT", gLastIntent);
            if (LAST_CONNECTION_STATUS.getLevel() == ConnectionStatus.LEVEL_CONNECTED) {
                extra.putParcelable("LAST_VPN_TUNNEL", LAST_VPN_TUNNEL);
                extra.putSerializable("LAST_ACCESSIBLE_RESOURCES", LAST_ACCESSIBLE_RESOURCES);
            }

            for (StatusListener sl : gStatusListeners) {
                sl.updateStatus(LAST_CONNECTION_STATUS, extra);
            }
        }
    }

    public static void updateStatus(@NonNull ConnectionStatus status, Bundle extra) {
        synchronized (STATUS_LOCK) {
            if (LAST_CONNECTION_STATUS != status) {
                LAST_CONNECTION_STATUS.setStatus(status.getStatus());
                LAST_CONNECTION_STATUS.setMessage(status.getMessage());
                LAST_CONNECTION_STATUS.setResid(status.getResid());

                if (extra != null) {
                    extra.setClassLoader(VpnTunnel.class.getClassLoader());
                    gLastIntent = extra.getParcelable("LAST_INTENT");

                    VpnTunnel tunnel = extra.getParcelable("LAST_VPN_TUNNEL");
                    if (tunnel != null) {
                        LAST_VPN_TUNNEL.copyFrom(tunnel);
                    }
                    ArrayList<AccessibleResource> resources = (ArrayList<AccessibleResource>) extra.get("LAST_ACCESSIBLE_RESOURCES");
                    if (resources != null) {
                        LAST_ACCESSIBLE_RESOURCES.clear();
                        LAST_ACCESSIBLE_RESOURCES.addAll(resources);
                    }
                }
            }

            for (StatusListener sl : gStatusListeners) {
                sl.updateStatus(status, extra);
            }
        }
    }

    public static void updateByteCount(long in, long out) {
        synchronized (TRAFFIC_LOCK) {
            TrafficHistory.LastDiff diff = TRAFFIC_HISTORY.add(in, out);
            for (ByteCountListener bcl : gByteCountListeners) {
                bcl.updateByteCount(in, out, diff.getDiffIn(), diff.getDiffOut());
            }
        }
    }

    public static void logThrowable(Throwable tr) {
        logThrowable(null, tr);
    }

    public static void logThrowable(String context, Throwable tr) {
        StringWriter writer = new StringWriter();
        tr.printStackTrace(new PrintWriter(writer));
        LogItem li;
        if (context != null) {
            li = new LogItem(LogSource.OPENVPN_FRONT, LogLevel.VERBOSE, R.string.unhandled_exception_context, tr.getMessage(), writer.toString(), context);
        } else {
            li = new LogItem(LogSource.OPENVPN_FRONT, LogLevel.VERBOSE, R.string.unhandled_exception, tr.getMessage(), writer.toString());
        }
        newLogItem(li);
    }

    public static void logInfo(String message) {
        newLogItem(new LogItem(LogSource.OPENVPN_FRONT, LogLevel.INFO, message));
    }

    public static void logInfo(int resourceId, Object... args) {
        newLogItem(new LogItem(LogSource.OPENVPN_FRONT, LogLevel.INFO, resourceId, args));
    }

    public static void logDebug(String message) {
        newLogItem(new LogItem(LogSource.OPENVPN_FRONT, LogLevel.DEBUG, message));
    }

    public static void logDebug(int resourceId, Object... args) {
        newLogItem(new LogItem(LogSource.OPENVPN_FRONT, LogLevel.DEBUG, resourceId, args));
    }

    public static void logError(String message) {
        newLogItem(new LogItem(LogSource.OPENVPN_FRONT, LogLevel.ERROR, message));
    }

    public static void logError(int resourceId, Object... args) {
        newLogItem(new LogItem(LogSource.OPENVPN_FRONT, LogLevel.ERROR, resourceId, args));
    }

    public static void logWarning(String message) {
        newLogItem(new LogItem(LogSource.OPENVPN_FRONT, LogLevel.WARNING, message));
    }

    public static void logWarning(int resourceId, Object... args) {
        newLogItem(new LogItem(LogSource.OPENVPN_FRONT, LogLevel.WARNING, resourceId, args));
    }

    public static void logOpenVPNConsole(LogLevel level, String message) {
        newLogItem(new LogItem(LogSource.OPENVPN_CONSOLE, level, message));
    }

    public static void logOpenVPNManagement(LogLevel level, String message) {
        newLogItem(new LogItem(LogSource.OPENVPN_MANAGEMENT, level, message));
    }

    public static void newLogItem(@NonNull LogItem logItem) {
        newLogItem(logItem, false);
    }


    private static HandlerThread mHandlerThread;
    private static LogFileHandler mLogFileHandler;

    static void newLogItem(LogItem logItem, boolean cachedLine) {
        synchronized (LOG_LOCK) {
            if (cachedLine) {
                gLogBufferAll.addFirst(logItem);
            } else {
                gLogBufferAll.addLast(logItem);
            }

            LinkedList<LogItem> logBuffer = gLogBufferMap.get(logItem.getLogSource());
            if (logBuffer == null) {
                logBuffer = new LinkedList<>();
                gLogBufferMap.put(logItem.getLogSource(), logBuffer);
            }

            if (cachedLine) {
                // 从文件缓存读入的日志不要再写入文件
                logBuffer.addFirst(logItem);
            } else {
                logBuffer.addLast(logItem);
                if (mLogFileHandler != null) {
                    Message msg = mLogFileHandler.obtainMessage(LogFileHandler.LOG_MESSAGE, logItem);
                    mLogFileHandler.sendMessage(msg);
                }
            }

            if (logBuffer.size() > MAX_LOGE_NTRIES + MAX_LOGE_NTRIES) {
                while (logBuffer.size() > MAX_LOGE_NTRIES) {
                    logItem = logBuffer.removeFirst();
                    gLogBufferAll.remove(logItem);
                }

                // 截断并重建文件缓存
                if (mLogFileHandler != null) {
                    mLogFileHandler.sendMessage(mLogFileHandler.obtainMessage(LogFileHandler.TRIM_LOG_FILE));
                }
            }

            for (LogListener ll : gLogListeners) {
                ll.newLog(logItem);
            }
        }
    }

    public static void initLogCache(@NonNull Context context) {
        synchronized (LOG_LOCK) {
            mHandlerThread = new HandlerThread("LogFileWriter", Thread.MIN_PRIORITY);
            mHandlerThread.start();
            mLogFileHandler = new LogFileHandler(mHandlerThread.getLooper());

            Message msg = mLogFileHandler.obtainMessage(LogFileHandler.LOG_INIT, context);
            mLogFileHandler.sendMessage(msg);
        }

        logPlatformInfo();
    }

    public static void flushLog() {
        synchronized (LOG_LOCK) {
            if (mLogFileHandler != null) {
                mLogFileHandler.sendEmptyMessage(LogFileHandler.FLUSH_TO_DISK);
            }
        }
    }

    public static void clearLog() {
        synchronized (LOG_LOCK) {
            gLogBufferAll.clear();
            gLogBufferMap.clear();
        }

        logPlatformInfo();

        synchronized (LOG_LOCK) {
            if (mLogFileHandler != null) {
                mLogFileHandler.sendEmptyMessage(LogFileHandler.TRIM_LOG_FILE);
            }
        }
    }

    private static void logPlatformInfo() {
        String nativeAPI;
        try {
            nativeAPI = NativeUtils.getNativeAPI();
        } catch (UnsatisfiedLinkError ignore) {
            nativeAPI = "error";
        }

        logInfo(R.string.mobile_info, Build.MODEL, Build.BOARD, Build.BRAND,
            Build.VERSION.SDK_INT, nativeAPI, Build.VERSION.RELEASE, Build.ID, Build.FINGERPRINT, "", "");
    }

}
