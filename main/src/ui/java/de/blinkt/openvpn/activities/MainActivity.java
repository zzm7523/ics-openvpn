/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Rationale;
import com.yanzhenjie.permission.RequestExecutor;
import com.yanzhenjie.permission.runtime.Permission;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.fragments.AboutFragment;
import de.blinkt.openvpn.fragments.GeneralSettings;
import de.blinkt.openvpn.fragments.GraphFragment;
import de.blinkt.openvpn.fragments.LogFragment;
import de.blinkt.openvpn.fragments.AccessibleResourceFragment;
import de.blinkt.openvpn.fragments.VPNProfileList;
import de.blinkt.openvpn.views.ScreenSlidePagerAdapter;

public class MainActivity extends BaseActivity {

    private ViewPager mPager;
    private ScreenSlidePagerAdapter mPagerAdapter;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        /* Toolbar and slider should have the same elevation */
        setToolbar(R.id.toolbar);

        // Instantiate a ViewPager and a PagerAdapter.
        mPagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager(), this);
        mPagerAdapter.addTab(R.string.vpn_list_title, VPNProfileList.class);
        mPagerAdapter.addTab(R.string.accessible_resource, AccessibleResourceFragment.class);
        mPagerAdapter.addTab(R.string.graph, GraphFragment.class);
        mPagerAdapter.addTab(R.string.generalsettings, GeneralSettings.class);
        mPagerAdapter.addTab(R.string.about, AboutFragment.class);

        mPager = findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkMustBePermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.show_log) {
            Intent intent = new Intent(this, GeneralActivity.class);
            intent.putExtra(GeneralActivity.FRAGMENT_CLASS_NAME, LogFragment.class.getName());
            intent.putExtra(GeneralActivity.TOOLBAR_TITLE, getString(R.string.log));
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void checkMustBePermission() {
        AndPermission.with(this)
            .runtime()
            .permission(Permission.READ_EXTERNAL_STORAGE, Permission.WRITE_EXTERNAL_STORAGE, Permission.READ_PHONE_STATE)
            .rationale(new PermissionRationale<>("应用没有授予读取外部存储权限"))
            .onGranted(permissions -> {})
            .onDenied(permissions -> {
                Uri packageURI = Uri.parse("package:" + getPackageName());
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(MainActivity.this, "没有权限无法导入应用配置", Toast.LENGTH_LONG).show();
            }).start();

        // TODO ?? 这个权限是否必须
/*
        AndPermission.with(this)
            .notification()
            .permission()
            .rationale(new PermissionRationale<>("应用没有授予通知显示权限"))
            .onGranted(permissions -> {})
            .onDenied(permissions -> {
                Uri packageURI = Uri.parse("package:" + getPackageName());
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(MainActivity.this, "没有权限可能导致服务不稳定", Toast.LENGTH_LONG).show();
            })
            .start();
*/
    }

    private static class PermissionRationale<T> implements Rationale<T> {
        private String message;

        public PermissionRationale(@NonNull String message) {
            this.message = message;
        }

        @Override
        public void showRationale(Context context, T permissions, RequestExecutor executor) {
            new android.app.AlertDialog.Builder(context).setCancelable(false)
                .setTitle("提示")
                .setMessage(message)
                .setPositiveButton("设置", (dialog, which) -> executor.execute())
                .setNegativeButton(R.string.cancel, (dialog, which) -> executor.cancel())
                .show();
        }
    }

}
