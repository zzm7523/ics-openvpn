/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.core.VpnStatus;

/**
 * Created by arne on 13.10.13.
 */
public class GeneralActivity extends BaseActivity {

    public static final String BACK_PRESSED_RESULT_OK = "back_pressed_result_ok";
    public static final String FRAGMENT_CLASS_NAME = "fragment_class_name";

    private static final String TAG = "GeneralActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.general_activity);
        setToolbar(R.id.toolbar);
        if (mToolbar != null) {
            mToolbar.setNavigationOnClickListener((v) -> finish());
        }

        Intent intent = getIntent();
        String activityName = intent.getStringExtra(FRAGMENT_CLASS_NAME);
        try {
            Class fragmentClass = Class.forName(activityName);
            Fragment fragmentObject = (Fragment) fragmentClass.newInstance();
            fragmentObject.setArguments(intent.getExtras());

            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, fragmentObject)
                    .commit();
            }

        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            VpnStatus.logThrowable("Instance Fragement " + activityName + " fail", ex);
            Log.d(TAG, "Instance Fragement " + activityName + " fail", ex);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (getIntent().getBooleanExtra(BACK_PRESSED_RESULT_OK, false)) {
            setResult(RESULT_OK, getIntent());
        }
        super.onBackPressed();
    }

}
