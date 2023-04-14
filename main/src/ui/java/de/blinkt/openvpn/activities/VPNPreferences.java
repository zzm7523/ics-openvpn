/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.fragments.Settings_Allowed_Apps;
import de.blinkt.openvpn.fragments.Settings_Authentication;
import de.blinkt.openvpn.fragments.Settings_Basic;
import de.blinkt.openvpn.fragments.Settings_Connections;
import de.blinkt.openvpn.fragments.Settings_IP;
import de.blinkt.openvpn.fragments.Settings_Obscure;
import de.blinkt.openvpn.fragments.Settings_Routing;
import de.blinkt.openvpn.fragments.Settings_UserEditable;
import de.blinkt.openvpn.fragments.ShowConfigFragment;
import de.blinkt.openvpn.fragments.VPNProfileList;
import de.blinkt.openvpn.utils.OpenVPNUtils;
import de.blinkt.openvpn.views.ScreenSlidePagerAdapter;


public class VPNPreferences extends BaseActivity {

    private String mProfileUUID;
    private VpnProfile mProfile;
    private ViewPager mPager;
    private ScreenSlidePagerAdapter mPagerAdapter;

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(getIntent().getStringExtra(VpnProfile.EXTRA_PROFILE_UUID), mProfileUUID);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadVpnProfile(null);
        // When a profile is deleted from a category fragment in hadset mod we need to finish
        // this activity as well when returning
        if (mProfile == null) {
            setResult(VPNProfileList.RESULT_VPN_DELETED);
            finish();
        } else {
            if (mProfile.isTemporaryProfile()) {
                Toast.makeText(this, getString(R.string.tmp_profile_no_edit), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onCreate(@NonNull Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        setToolbar(R.id.toolbar);

        loadVpnProfile(savedInstanceState);
        if (mToolbar != null) {
            if (mProfile != null)
                mToolbar.setTitle(getString(R.string.edit_profile_title, mProfile.getName()));
            mToolbar.setNavigationOnClickListener((v) -> {
                setResult(RESULT_OK, getIntent());
                finish();
            });
        }

        // Instantiate a ViewPager and a PagerAdapter.
        mPagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager(), this);
        Bundle fragmentArguments = new Bundle();
        fragmentArguments.putString(VpnProfile.EXTRA_PROFILE_UUID, mProfileUUID);
        mPagerAdapter.setFragmentArgs(fragmentArguments);

        if (mProfile.isUserEditable()) {
            mPagerAdapter.addTab(R.string.basic, Settings_Basic.class);
            mPagerAdapter.addTab(R.string.server_list, Settings_Connections.class);
            mPagerAdapter.addTab(R.string.ipdns, Settings_IP.class);
            mPagerAdapter.addTab(R.string.routing, Settings_Routing.class);
            mPagerAdapter.addTab(R.string.settings_auth, Settings_Authentication.class);
            mPagerAdapter.addTab(R.string.advanced, Settings_Obscure.class);
        } else {
            mPagerAdapter.addTab(R.string.basic, Settings_UserEditable.class);
        }

        mPagerAdapter.addTab(R.string.vpn_allowed_apps, Settings_Allowed_Apps.class);
        mPagerAdapter.addTab(R.string.generated_config, ShowConfigFragment.class);

        mPager = findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK, getIntent());
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.remove_vpn) {
            OpenVPNUtils.askProfileRemoval(this, mProfile);
            return true;

        } else if (item.getItemId() == R.id.duplicate_vpn) {
            Intent intent = new Intent();
            intent.putExtra(VpnProfile.EXTRA_PROFILE_UUID, mProfileUUID);
            setResult(VPNProfileList.RESULT_VPN_DUPLICATE, intent);
            finish();
            return true;

        } else if (item.getItemId() == R.id.share_vpn) {
            try {
                OpenVPNUtils.shareVpnProfile(this, mProfile);
            } catch (Exception ex) {
                Toast.makeText(this, R.string.export_config_error, Toast.LENGTH_LONG).show();
                ex.printStackTrace();
            }
            return true;

        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vpnpreferences_menu, menu);
        MenuItem item = menu.findItem(R.id.remove_vpn);
        if (item != null) {
            boolean connected = TextUtils.equals(VpnStatus.getConnectedVpnProfile(), mProfile.getUUIDString());
            item.setVisible(!connected);
        }
        item = menu.findItem(R.id.duplicate_vpn);
        if (menu != null) {
            item.setVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void loadVpnProfile(Bundle savedInstanceState) {
        Intent intent = getIntent();
        String profileUUID = intent.getStringExtra(VpnProfile.EXTRA_PROFILE_UUID);
        if (savedInstanceState != null) {
            String savedUUID = savedInstanceState.getString(VpnProfile.EXTRA_PROFILE_UUID);
            if (savedUUID != null)
                profileUUID = savedUUID;
        }
        if (profileUUID != null) {
            mProfileUUID = profileUUID;
            mProfile = ProfileManager.getInstance(this).get(this, mProfileUUID);
        }
    }

}
