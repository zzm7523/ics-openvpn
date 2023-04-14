/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import de.blinkt.xp.openvpn.R;

import java.io.ByteArrayInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.security.KeyStore;

import de.blinkt.openvpn.activities.GeneralActivity;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.IServiceStatus;
import de.blinkt.openvpn.core.OpenVPNLaunchHelper;
import de.blinkt.openvpn.core.OpenVPNStatusService;
import de.blinkt.openvpn.core.PasswordCache;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.fragments.AlertDialogFragment;
import de.blinkt.openvpn.fragments.LogFragment;
import de.blinkt.openvpn.utils.NetworkUtils;
import de.blinkt.openvpn.utils.OpenVPNUtils;

/**
 * This Activity actually handles two stages of a launcher shortcut's life cycle.
 * <p/>
 * 1. Your application offers to provide shortcuts to the launcher.  When
 * the user installs a shortcut, an activity within your application
 * generates the actual shortcut and returns it to the launcher, where it
 * is shown to the user as an icon.
 * <p/>
 * 2. Any time the user clicks on an installed shortcut, an intent is sent.
 * Typically this would then be handled as necessary by an activity within
 * your application.
 * <p/>
 * We handle stage 1 (creating a shortcut) by simply sending back the information (in the form
 * of an {@link android.content.Intent} that the launcher will use to create the shortcut.
 * <p/>
 * You can also implement this in an interactive way, by having your activity actually present
 * UI for the user to select the specific nature of the shortcut, such as a contact, picture, URL,
 * media item, or action.
 * <p/>
 * We handle stage 2 (responding to a shortcut) in this sample by simply displaying the contents
 * of the incoming {@link android.content.Intent}.
 * <p/>
 * In a real application, you would probably use the shortcut intent to display specific content
 * or start a particular operation.
 */
public class LaunchOpenVPN extends AppCompatActivity {

    public static final String EXTRA_KEY = "de.blinkt.openvpn.shortcutProfileUUID";
    public static final String EXTRA_HIDELOG = "de.blinkt.openvpn.showNoLogWindow";
    public static final String CLEAR_LOG = "clear_log_connect";

    private static final String TAG = "LaunchOpenVPN";
    private static final int PREPARE_VPN_SERVICE = 69;
    private static final int START_VPN_PROFILE = 70;

    private VpnProfile mProfile;
    private boolean mHideLog = false;

    // 这几个变量, 多线程读, 单线程写
    private volatile String mTransientAuthPW;
    private volatile String mTransientProtectPW;
    private volatile boolean mUserCancled = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            IServiceStatus service = IServiceStatus.Stub.asInterface(binder);
            try {
                if (mTransientAuthPW != null)
                    service.setCachedPassword(mProfile.getUUIDString(), PasswordCache.AUTH_PASSWORD, mTransientAuthPW);
                if (mTransientProtectPW != null)
                    service.setCachedPassword(mProfile.getUUIDString(), PasswordCache.PROTECT_PASSWORD, mTransientProtectPW);

                launchVPNSetp2();

            } catch (RemoteException ex) {
                ex.printStackTrace();
            }

            unbindService(this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.launch_vpn);
        setFinishOnTouchOutside(false);
        startVpnFromIntent();
    }

    @Override
    public void onBackPressed() {
        VpnStatus.updateStatus(new ConnectionStatus("NOPROCESS", R.string.state_noprocess), null);
        mUserCancled = true;
        setResult(RESULT_CANCELED, getIntent());
        super.onBackPressed();
    }

    private void startVpnFromIntent() {
        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())) {
            return;
        }

        synchronized (VpnStatus.STATUS_LOCK) {
            VpnStatus.LAST_VPN_TUNNEL.clearDefaults();
        }

        // Check if we need to clear the log
        if (Preferences.getVariableSharedPreferences(this).getBoolean(CLEAR_LOG, true)) {
            VpnStatus.clearLog();
        }

        ProfileManager profileManager = ProfileManager.getInstance(this);

        // we got called to be the starting point, most likely a shortcut
        String shortcutUUID = intent.getStringExtra(EXTRA_KEY);
        if (shortcutUUID != null)
            mProfile = profileManager.get(this, shortcutUUID);
        if (mProfile == null) {
            VpnStatus.logError(R.string.shortcut_profile_notfound);
            // show Log activity to display error
            showLogActivity();
            finish();
            return;
        }

        int result = mProfile.checkProfile(this);
        if (result != R.string.no_error_found) {
            showAlertDialog(getString(R.string.error), getString(result));
            return;
        }

        profileManager.setConnectedVpnProfile(this, mProfile);
        VpnStatus.setConnectedVpnProfile(mProfile.getUUIDString());
        mHideLog = intent.getBooleanExtra(EXTRA_HIDELOG, false);

        new Thread(() -> {
            if (mProfile.getAuthenticationType() == VpnProfile.TYPE_KEYSTORE ||
                    mProfile.getAuthenticationType() == VpnProfile.TYPE_USERPASS_KEYSTORE) {

                String preselect = mProfile.getAlias();
                boolean askForAccess = true;

                if (!TextUtils.isEmpty(preselect)) {
                    try {
                        KeyChain.getCertificateChain(this, preselect);
                        KeyChain.getPrivateKey(this, preselect);
                        askForAccess = false;
                        // 已授权, 启动VPN
                        runOnUiThread(() -> launchVPNSetp1());

                    } catch (KeyChainException | InterruptedException ex) {
                        // Ignore, 请求授权
                    }
                }

                // 未授权, 请求授权
                if (askForAccess) {
                    runOnUiThread(() -> choosePrivateKeyAlias(this, preselect));
                }

            } else {
                runOnUiThread(() -> launchVPNSetp1());
            }

        }).start();
    }

    @MainThread
    @TargetApi(Build.VERSION_CODES.M)
    public void choosePrivateKeyAlias(@NonNull Activity activity, String preselect) {
        KeyChain.choosePrivateKeyAlias(activity, (alias -> {
                mProfile.setAlias(alias);
                launchVPNSetp1();
            }),
            new String[]{"RSA", "EC"}, null, null, preselect);
    }

    @MainThread
    private void askForPW(int type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.pw_request_dialog_title, getString(type)));
        builder.setMessage(getString(R.string.pw_request_dialog_prompt, mProfile.getName()));

        @SuppressLint("InflateParams")
        View vLayout = getLayoutInflater().inflate(R.layout.userpass, null, false);
        EditText vUsername = vLayout.findViewById(R.id.username);
        EditText vPassword = vLayout.findViewById(R.id.password);

        if (type == R.string.password) {
            vUsername.setText(mProfile.getUsername());
            vPassword.setText(mProfile.getPassword());
//          ((CheckBox) vLayout.findViewById(R.id.save_password)).setChecked(!TextUtils.isEmpty(mProfile.getPassword()));
        } else {
            vUsername.setVisibility(View.GONE);
            vPassword.setText(mProfile.getProtectPassword());
//          ((CheckBox) vLayout.findViewById(R.id.save_password)).setChecked(!TextUtils.isEmpty(mProfile.getProtectPassword()));
        }
        ((CheckBox) vLayout.findViewById(R.id.save_password)).setChecked(true);

        builder.setView(vLayout);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            if (type == R.string.password) {
                mProfile.setUsername(vUsername.getText().toString());
                mProfile.setPassword(vPassword.getText().toString());
                if (!((CheckBox) vLayout.findViewById(R.id.save_password)).isChecked()) {
                    mTransientAuthPW = mProfile.getPassword();
                    mProfile.setPassword(null);
                }

            } else {
                mProfile.setProtectPassword(vPassword.getText().toString());
                if (!((CheckBox) vLayout.findViewById(R.id.save_password)).isChecked()) {
                    mTransientProtectPW = mProfile.getProtectPassword();
                    mProfile.setProtectPassword(null);
                }
            }

            Intent intent = new Intent(LaunchOpenVPN.this, OpenVPNStatusService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            VpnStatus.updateStatus("USER_VPN_PASSWORD_CANCELLED", R.string.state_user_vpn_password_cancelled);
            mUserCancled = true;
            finish();
        });

        builder.setOnCancelListener(d -> {
            VpnStatus.updateStatus("USER_VPN_PASSWORD_CANCELLED", R.string.state_user_vpn_password_cancelled);
            mUserCancled = true;
            finish();
        });

        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    @MainThread
    private void showLogActivity() {
        Intent intent = new Intent(this, GeneralActivity.class);
        intent.putExtra(GeneralActivity.FRAGMENT_CLASS_NAME, LogFragment.class.getName());
        intent.putExtra(GeneralActivity.TOOLBAR_TITLE, getString(R.string.log));
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    @MainThread
    private void showAlertDialog(@NonNull String title, @NonNull String message) {
        AlertDialogFragment dialogFragment = AlertDialogFragment.newInstance(title, message, true);
//      dialogFragment.show(getSupportFragmentManager(), "dialog");
        // 解决 IllegalStateException: Can not perform this action after onSaveInstanceState
        getSupportFragmentManager().beginTransaction().add(dialogFragment, "tag").commitAllowingStateLoss();
    }

    private boolean generateConfigFile(@NonNull VpnProfile profile) {
        try (Writer writer = new FileWriter(OpenVPNLaunchHelper.getConfigFile(this))) {
            String config = OpenVPNUtils.generateConfig(this, profile);
            writer.write(config);
            writer.flush();
            return true;
        } catch (ConfigParser.ParseError | IllegalStateException | IOException ex) {
            VpnStatus.logThrowable(getString(R.string.generate_vpn_config_error), ex);
            VpnStatus.updateStatus(new ConnectionStatus("NOPROCESS", R.string.state_noprocess), null);
            runOnUiThread(() -> showAlertDialog(getString(R.string.error), getString(R.string.generate_vpn_config_error)));
            return false;
        }
    }

    @MainThread
    private void launchVPNSetp1() {
        new Thread(() -> {
            OpenVPNUtils.sleepForDebug(2000);

            if (generateConfigFile(mProfile)) {
                // 检查是否放弃连接
                if (mUserCancled)
                    return;
                runOnUiThread(() -> launchVPNSetp2());
            }
        }).start();
    }

    @MainThread
    private void launchVPNSetp2() {
        // !! 逻辑上必须先选择证书/私钥, 再检查保护密码(应该在choosePrivateKeyAlias(...)调用后)
        // 检查证书保护密码
        int needProtectPassword = mProfile.needProtectPasswordInput(mTransientProtectPW);
        if (needProtectPassword != 0) {
            VpnStatus.updateStatus("USER_VPN_PASSWORD", R.string.state_user_vpn_password);
            askForPW(needProtectPassword);
            return;
        }

        // 检查用户名和密码
        int needUserPassword = mProfile.needUserPasswordInput(mTransientAuthPW);
        if (needUserPassword != 0) {
            VpnStatus.updateStatus("USER_VPN_PASSWORD", R.string.state_user_vpn_password);
            askForPW(needUserPassword);
            return;
        }

        // 校验证书保护密码
        new Thread(() -> {
            String protectPW = mTransientProtectPW == null ? mProfile.getProtectPassword() : mTransientProtectPW;
            if (verifyProtectPassword(mProfile.getAuthenticationType(), protectPW)) {
                runOnUiThread(() -> {
                    Intent intent = VpnService.prepare(this);
                    if (intent != null) {
                        VpnStatus.updateStatus("USER_VPN_PERMISSION", R.string.state_user_vpn_permission);
                        try {
                            startActivityForResult(intent, PREPARE_VPN_SERVICE);
                        } catch (ActivityNotFoundException ex) {
                            // At least one user reported that an official Sony Xperia Arc S image triggers this exception
                            VpnStatus.logError(R.string.no_vpn_support_image);
                            showLogActivity();
                        }
                    } else {
                        onActivityResult(START_VPN_PROFILE, AppCompatActivity.RESULT_OK, null);
                    }
                });
            } else {
                mProfile.setProtectPassword(null);
                VpnStatus.updateStatus(new ConnectionStatus("NOPROCESS", R.string.state_noprocess), null);
                runOnUiThread(() -> showAlertDialog(getString(R.string.error), getString(R.string.state_password_error)));
            }
        }).start();
    }

    private boolean verifyProtectPassword(int authType, String protectPW) {
        if (VpnProfile.TYPE_USERPASS_PKCS12 == authType || VpnProfile.TYPE_PKCS12 == authType) {
            try {
                KeyStore ks = KeyStore.getInstance("PKCS12", org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME);
                String content = VpnProfile.getEmbeddedContent(mProfile.getPkcs12Filename());
                InputStream in = new ByteArrayInputStream(Base64.decode(content, Base64.DEFAULT));
                ks.load(in, protectPW == null ? "".toCharArray() : protectPW.toCharArray());
                return true;

            } catch (Throwable ex) {
                VpnStatus.logThrowable("Verify pkcs12 password fail", ex);
                return false;
            }

        } else if (VpnProfile.TYPE_USERPASS_KEYSTORE == authType || VpnProfile.TYPE_KEYSTORE == authType) {
            // 不需要处理, 直接返回true
            return true;

        } else {
            // 不需要保护密码(未使用私钥), 直接返回true
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PREPARE_VPN_SERVICE) {
            if (resultCode == AppCompatActivity.RESULT_CANCELED) {
                // User does not want us to start, so we just vanish
                VpnStatus.updateStatus("USER_VPN_PERMISSION_CANCELLED", R.string.state_user_vpn_permission_cancelled);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    VpnStatus.logError(R.string.nought_alwayson_warning);
                finish();

            } else if (resultCode == AppCompatActivity.RESULT_OK) {
                launchVPNSetp2();
            }

        } else if (requestCode == START_VPN_PROFILE) {
            SharedPreferences prefs = Preferences.getDefaultSharedPreferences(this);
            boolean showLogActivity = prefs.getBoolean("show_logactivity", false);
            if (!mHideLog && showLogActivity)
                showLogActivity();

            ProfileManager.getInstance(this).updateLRU(this, mProfile);
            OpenVPNLaunchHelper.startOpenVpn(getBaseContext(), mProfile);
            finish();
        }
    }

}
