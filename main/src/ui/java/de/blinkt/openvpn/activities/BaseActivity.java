/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities;

import android.app.UiModeManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import de.blinkt.xp.openvpn.R;

import de.blinkt.openvpn.core.PasswordDialogFragment;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.fragments.AlertDialogFragment;

public abstract class BaseActivity extends AppCompatActivity {

    public static final String TOOLBAR_TITLE = "toolbar_title";

    protected Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isAndroidTV()) {
            requestWindowFeature(Window.FEATURE_OPTIONS_PANEL);
        }
        super.onCreate(savedInstanceState);
    }

    protected boolean isAndroidTV() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (uiModeManager == null)
            return false;
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    protected void setToolbar(@IdRes int id) {
        mToolbar = findViewById(id);
        if (mToolbar != null) {
            Intent intent = getIntent();
            String title = null;
            if (intent != null)
                title = intent.getStringExtra(TOOLBAR_TITLE);
            if (TextUtils.isEmpty(title))
                title = getString(R.string.app);

            mToolbar.setElevation(0);
            mToolbar.setTitle(title);
            // !! setSupportActionBar(...) 必须放在setTitleXXX(...)调用后
            setSupportActionBar(mToolbar);
        }
    }

    public void showPasswordDialog(Intent intent, boolean finish) {
        PasswordDialogFragment dialogFragment = PasswordDialogFragment.Companion.newInstance(intent, finish);
        if (dialogFragment != null) {
//          dialogFragment.show(getSupportFragmentManager(), "dialog");
            getSupportFragmentManager().beginTransaction().add(dialogFragment, "tag").commitAllowingStateLoss();
        }
    }

    public void showAlertDialog(@NonNull String title, @NonNull String message, boolean finish) {
        AlertDialogFragment dialogFragment = AlertDialogFragment.newInstance(title, message, finish);
//      dialogFragment.show(getSupportFragmentManager(), "dialog");
        getSupportFragmentManager().beginTransaction().add(dialogFragment, "tag").commitAllowingStateLoss();
    }

}
