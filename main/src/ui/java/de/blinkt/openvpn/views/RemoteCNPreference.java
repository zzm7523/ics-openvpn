/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.preference.DialogPreference;

import de.blinkt.xp.openvpn.R;

public class RemoteCNPreference extends DialogPreference {

    private int mDNType;
    private String mDn;

    public RemoteCNPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public RemoteCNPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RemoteCNPreference(Context context, AttributeSet attrs) {
        this(context, attrs, RemoteCNPreference.getAttr(context, R.attr.dialogPreferenceStyle,
            android.R.attr.dialogPreferenceStyle));
    }

    public RemoteCNPreference(Context context) {
        this(context, null);
    }

    private static int getAttr(@NonNull Context context, int attr, int fallbackAttr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        if (value.resourceId != 0) {
            return attr;
        }
        return fallbackAttr;
    }

    public void setDN(String dn) {
        mDn = dn;
    }

    public void setAuthType(int x509authtype) {
        mDNType = x509authtype;
    }

    public String getCNText() {
        return mDn;
    }

    public int getAuthtype() {
        return mDNType;
    }

    @Override
    public int getDialogLayoutResource() {
        return R.layout.tlsremote;
    }

}
