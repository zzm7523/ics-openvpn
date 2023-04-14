/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static class CrashHandlerHolder {
        public static CrashHandler instance = new CrashHandler();
    }

    public static CrashHandler getInstance() {
        return CrashHandlerHolder.instance;
    }

    private Thread.UncaughtExceptionHandler defaultHandler = null;
    private File logPath = null;

    public void init(@NonNull Context ctx) {
        logPath = new File(ctx.getCacheDir(), "crash");
        if (!logPath.exists())
            logPath.mkdirs();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /**
     * 核心方法，当程序crash 会回调此方法， Throwable中存放这错误日志
     */
    @Override
    public void uncaughtException(@NonNull Thread arg0, @NonNull Throwable arg1) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss", Locale.US);
        String fileName = df.format(new Date()) + ".log";

        try (OutputStream out = new FileOutputStream(new File(logPath, fileName), true)) {
            arg1.printStackTrace(new PrintStream(out));

        } catch (IOException e) {
            Log.e("CrashHandler", "load file failed...", e.getCause());
        }

        // !! 调用defaultHandler.uncaughtException(...)
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(arg0, arg1);
        }

        // !! 不调用defaultHandler.uncaughtException(...), ?? 直接调用下面代码; 震荡, 反复重启:openvpn服务进程
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);

    }

}
