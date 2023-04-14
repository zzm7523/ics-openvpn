/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by arne on 23.01.16.
 */
class LogFileHandler extends Handler {

    public static final String CACHE_LOGFILE_NAME = "logcache.dat";

    public static final int TRIM_LOG_FILE = 100;
    public static final int FLUSH_TO_DISK = 101;
    public static final int LOG_INIT = 102;
    public static final int LOG_MESSAGE = 103;

    // protected 方便子类化测试
    protected DateFormat mDateFormat;
    protected Context mContext;
    protected OutputStream mLogOutBin;
    protected ObjectOutputStream mLogOutObj;

    // for debug 发送日志时使用
    protected OutputStream mLogOpenvpnUI;
    protected OutputStream mLogOpenvpnConsole;
    protected OutputStream mLogOpenvpnManagement;

    public LogFileHandler(@NonNull Looper looper) {
        super(looper);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        try {
            if (msg.what == LOG_INIT) {
                if (mLogOutBin != null)
                    VpnStatus.logError("mLogFile not null, already initialized");
                else
                    initLogFile((Context) msg.obj);

            } else if (msg.what == LOG_MESSAGE && msg.obj instanceof LogItem) {
                writeLogItemObject((LogItem) msg.obj);
                writeLogItemText((LogItem) msg.obj);

            } else if (msg.what == TRIM_LOG_FILE) {
                trimCacheLogFile();

                LinkedList<LogItem> logBuffer;
                synchronized (VpnStatus.LOG_LOCK) {
                    logBuffer = new LinkedList<>(VpnStatus.getLogBufferAll());
                }
                for (LogItem li : logBuffer) {
                    writeLogItemObject(li);
                }

            } else if (msg.what == FLUSH_TO_DISK) {
                flushCacheToDisk();

            } else {
                VpnStatus.logError("Unknown log message: " + msg);
            }

        } catch (IOException | BufferOverflowException ex) {
            VpnStatus.logError("Error during log cache: " + msg);
            VpnStatus.logThrowable(ex);
        }
    }

    protected void initLogFile(@NonNull Context context) throws IOException {
        File cacheDir = context.getCacheDir();
        mContext = context;

        mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        mLogOpenvpnUI = new FileOutputStream(new File(cacheDir, "OpenVPN_front.log"));
        mLogOpenvpnConsole = new FileOutputStream(new File(cacheDir, "OpenVPN_console.log"));
        mLogOpenvpnManagement = new FileOutputStream(new File(cacheDir, "OpenVPN_management.log"));

        readLogItemCache(cacheDir);
        openCacheLogFile(cacheDir);
    }

    protected FileOutputStream openAndArchiveLogFile(@NonNull File logFile) throws IOException {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(logFile, true);
            FileChannel fileChannel = fileOutputStream.getChannel();
            if (fileChannel.size() > 2 * 1024 * 1024) {
                fileOutputStream.close();
                zipLogFile(logFile, new File(mContext.getCacheDir(), logFile.getName() + ".zip"));
                fileOutputStream = new FileOutputStream(logFile);
            }

            return fileOutputStream;

        } catch (Exception ex) {
            ex.printStackTrace();
            return new FileOutputStream(logFile);
        }
    }

    protected void zipLogFile(@NonNull File logFile, @NonNull File zipFile) {
        try (
            FileInputStream fileInputStream = new FileInputStream(logFile);
            FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)
        ) {

            ZipEntry zipEntry = new ZipEntry(logFile.getName());
            zipOutputStream.putNextEntry(zipEntry);

            byte[] buf = new byte[2048];
            int bytesRead;

            while ((bytesRead = fileInputStream.read(buf)) > 0) {
                zipOutputStream.write(buf, 0, bytesRead);
            }

            zipOutputStream.closeEntry();
            zipOutputStream.flush();
            fileOutputStream.flush();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    protected void flushCacheToDisk() throws IOException {
        mLogOutObj.flush();
        mLogOutBin.flush();
    }

    protected void trimCacheLogFile() throws IOException {
        try {
            mLogOutObj.flush();
            mLogOutBin.flush();

            ((FileOutputStream) mLogOutBin).getChannel().truncate(0);
            mLogOutBin.close();

        } catch (Exception ex) {
            ex.printStackTrace();
            // Ignore
        }

        openCacheLogFile(mContext.getCacheDir());
    }

    protected void openCacheLogFile(@NonNull File cacheDir) throws IOException {
        File logfile = new File(cacheDir, CACHE_LOGFILE_NAME);
        mLogOutBin = new FileOutputStream(logfile);
        mLogOutObj = new ObjectOutputStream(mLogOutBin);
    }

    protected void closeCacheLogFile() throws IOException {
        mLogOutObj.flush();
        mLogOutBin.flush();
        mLogOutObj.close();
        mLogOutBin.close();
    }

    protected void readLogItemCache(@NonNull File cacheDir) {
        try {
            File logfile = new File(cacheDir, CACHE_LOGFILE_NAME);
            if (!logfile.exists() || !logfile.canRead())
                return;

            try (ObjectInputStream objIn = new ObjectInputStream(new FileInputStream(logfile))) {
                LogItem li = (LogItem) objIn.readObject();
                // 舍弃从cache文件读入的tag
                li.setTag(null);
                VpnStatus.newLogItem(li, true);

            } catch (ObjectStreamException | ClassNotFoundException ex) {
                VpnStatus.logThrowable("read log item", ex);
            } catch (EOFException ex) {
                // Ignore
            }

        } catch (Exception ex) {
            VpnStatus.logError("Reading cached logfile failed");
            VpnStatus.logThrowable(ex);
        }
    }

    protected void writeLogItemObject(@NonNull LogItem li) throws IOException {
        // tag不要写入cache文件
        String tag = li.getTag();
        li.setTag(null);

        mLogOutObj.writeObject(li);
        li.setTag(tag);
    }

    protected void writeLogItemText(@NonNull LogItem li) throws IOException {
        // tag不要写入cache文件
        String tag = li.getTag();
        li.setTag(null);

        String logtime = mDateFormat.format(new Date(li.getLogtime()));
        String text = logtime + " " + li.getString(mContext) + "\n";

        if (LogSource.OPENVPN_FRONT.equals(li.getLogSource())) {
            mLogOpenvpnUI.write(text.getBytes(StandardCharsets.UTF_8));
            mLogOpenvpnUI.flush();
        } else if (LogSource.OPENVPN_CONSOLE.equals(li.getLogSource())) {
            mLogOpenvpnConsole.write(text.getBytes(StandardCharsets.UTF_8));
            mLogOpenvpnConsole.flush();
        } else if (LogSource.OPENVPN_MANAGEMENT.equals(li.getLogSource())) {
            mLogOpenvpnManagement.write(text.getBytes(StandardCharsets.UTF_8));
            mLogOpenvpnManagement.flush();
        } else {
            throw new IllegalStateException("Invalid LogSource " + li.getLogSource().toString());
        }

        li.setTag(tag);
    }

}
