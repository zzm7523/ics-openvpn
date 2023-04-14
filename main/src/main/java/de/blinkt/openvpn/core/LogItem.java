/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;

import de.blinkt.xp.openvpn.BuildConfig;
import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.utils.OpenVPNUtils;

/**
 * Created by arne on 24.04.16.
 */
public class LogItem implements Serializable, Parcelable {

    private static final long serialVersionUID = 5842449911692L;

    @NonNull
    private LogSource mSource;
    // Default log priority
    @NonNull
    private LogLevel mLevel = LogLevel.INFO;
    private long mLogtime = System.currentTimeMillis();

    private Object[] mArgs;
    private String mMessage;
    private int mResourceId;

    // tag 调试用
    private String mTag;

    private LogItem(@NonNull LogSource source, int resourceId, Object[] args) {
        mSource = source;
        mResourceId = resourceId;
        mArgs = args;
    }

    public LogItem(@NonNull LogSource source, @NonNull LogLevel level, String message) {
        mSource = source;
        mLevel = level;
        mMessage = message;
    }

    public LogItem(@NonNull LogSource source, @NonNull LogLevel loglevel, int resourceId, Object... args) {
        mSource = source;
        mLevel = loglevel;
        mResourceId = resourceId;
        mArgs = args;
    }

    public LogItem(@NonNull LogSource source, @NonNull LogLevel loglevel, int resourceId) {
        mSource = source;
        mLevel = loglevel;
        mResourceId = resourceId;
    }

    public LogItem(@NonNull Parcel in) {
        mSource = LogSource.getEnumByValue(in.readInt());
        mLevel = LogLevel.getEnumByValue(in.readInt());
        mLogtime = in.readLong();
        mArgs = in.readArray(Object.class.getClassLoader());
        mMessage = in.readString();
        mResourceId = in.readInt();
        mTag = in.readString();
    }

    public LogSource getLogSource() {
        return mSource;
    }

    public LogLevel getLogLevel() {
        return mLevel;
    }

    public long getLogtime() {
        return mLogtime;
    }

    public String getTag() {
        return mTag;
    }

    public void setTag(String tag) {
        this.mTag = tag;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LogItem))
            return obj.equals(this);

        LogItem other = (LogItem) obj;

        return mSource.equals(other.mSource) && other.mLevel.equals(mLevel) && mLogtime == other.mLogtime &&
            mResourceId == other.mResourceId && Arrays.equals(mArgs, other.mArgs) &&
            ((other.mMessage == null && mMessage == other.mMessage) || mMessage.equals(other.mMessage)) &&
            ((other.mTag == null && mTag == other.mTag) || mTag.equals(other.mTag));
    }

    public String getString(Context c) {
        String extString = getBasicString(c);
        if (BuildConfig.DEBUG) {
            if (!TextUtils.isEmpty(mTag))
                extString += ", tag=" + mTag;
        }
        return extString;
    }

    @SuppressLint("StringFormatMatches")
    public String getBasicString(Context c) {
        if (mMessage != null) {
            return mMessage;
        }

        if (c != null) {
            if (mResourceId == R.string.mobile_info) {
                String apksign = "error getting package signature";
                String version = "error getting version";

                try {
                    apksign = OpenVPNUtils.buildFor(c);
                    version = BuildConfig.VERSION_NAME;
                } catch (Exception ex) {
                    // Ignore
                }

                Object[] argsext = Arrays.copyOf(mArgs, mArgs.length);
                argsext[argsext.length - 1] = apksign;
                argsext[argsext.length - 2] = version;

                return c.getString(R.string.mobile_info, argsext);
            }

            if (mArgs == null)
                return c.getString(mResourceId);
            else
                return c.getString(mResourceId, mArgs);

        } else {
            String str = String.format(Locale.US, "Log (no context) resid %d", mResourceId);
            if (mArgs != null)
                str += join("|", mArgs);
            return str;
        }
    }

    @Override
    public String toString() {
        return getString(null);
    }

    // TextUtils.join will cause not macked exeception in tests ....
    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder builder = new StringBuilder();
        boolean firstTime = true;

        for (Object token : tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                builder.append(delimiter);
            }
            builder.append(token);
        }

        return builder.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mSource.getInt());
        dest.writeInt(mLevel.getInt());
        dest.writeLong(mLogtime);
        dest.writeArray(mArgs);
        dest.writeString(mMessage);
        dest.writeInt(mResourceId);
        dest.writeString(mTag);
    }

    public static final Creator<LogItem> CREATOR = new Creator<LogItem>() {

        public LogItem createFromParcel(@NonNull Parcel in) {
            return new LogItem(in);
        }

        public LogItem[] newArray(int size) {
            return new LogItem[size];
        }

    };

}
