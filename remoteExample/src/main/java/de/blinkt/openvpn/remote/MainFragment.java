package de.blinkt.openvpn.remote;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import de.blinkt.openvpn.api.APIVpnProfile;
import de.blinkt.openvpn.api.IOpenVPNAPIService;
import de.blinkt.openvpn.api.IOpenVPNStatusCallback;

import de.blinkt.xp.openvpn.remote.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;

public class MainFragment extends Fragment implements View.OnClickListener, Handler.Callback {

    private static final int MSG_UPDATE_STATE = 0;
    private static final int MSG_UPDATE_BIND_STATE = 1;

    private static final int START_PROFILE_EMBEDDED = 2;
    private static final int START_PROFILE_BYUUID = 3;
    private static final int ICS_OPENVPN_PERMISSION = 4;

    private IOpenVPNAPIService mService = null;
    private Handler mHandler = null;
    private String mStartUUID = null;
    private String mStartName = "192.168.31.96";
    private TextView mBindStatus;
    private TextView mStatus;
    private TextView mVpnList;
    private Button mStartVpn_aidl;
    private Button mStartVpn_intent;

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (msg.what == MSG_UPDATE_STATE) {
            mStatus.setText((CharSequence) msg.obj);
        } else if (msg.what == MSG_UPDATE_BIND_STATE) {
            mBindStatus.setText((CharSequence) msg.obj);
        }
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_main, container, false);
        mStartVpn_aidl = v.findViewById(R.id.startVPN_aidl);
        mStartVpn_aidl.setOnClickListener(this);
        mStartVpn_intent = v.findViewById(R.id.startVPN_intent);
        mStartVpn_intent.setOnClickListener(this);
        v.findViewById(R.id.disconnect).setOnClickListener(this);
        v.findViewById(R.id.startembedded_aidl).setOnClickListener(this);
        v.findViewById(R.id.startembedded_intent).setOnClickListener(this);

        mVpnList = v.findViewById(R.id.vpnList);
        mStatus = v.findViewById(R.id.status);
        mBindStatus = v.findViewById(R.id.bindStatus);
        mHandler = new Handler(this);
        return v;
    }

    private String readVPNConfig(@NonNull String filename) {
        try (
            InputStream in = Objects.requireNonNull(getActivity()).getAssets().open(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in))
        ) {
            StringBuilder buffer = new StringBuilder();
            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;
                buffer.append(line).append('\n');
            }

            return buffer.toString();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService();
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService();
    }

    private IOpenVPNStatusCallback mCallback = new IOpenVPNStatusCallback.Stub() {
        /**
         * This is called by the remote service regularly to tell us about
         * new values.  Note that IPC calls are dispatched through a thread
         * pool running in each process, so the code executing here will
         * NOT be running in our main thread like most other things -- so,
         * to update the UI, we need to use a Handler to hop over there.
         */

        @Override
        public void newStatus(String uuid, String status, String message) {
            Message msg = Message.obtain(mHandler, MSG_UPDATE_STATE, status + "|" + message);
            msg.sendToTarget();
        }

    };

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = IOpenVPNAPIService.Stub.asInterface(service);

            Message msg = Message.obtain(mHandler, MSG_UPDATE_BIND_STATE, "服务绑定成功");
            msg.sendToTarget();

            try {
                // Request permission to use the API
                Intent intent = mService.prepare(getActivity().getPackageName());
                if (intent != null) {
                    startActivityForResult(intent, ICS_OPENVPN_PERMISSION);
                } else {
                    onActivityResult(ICS_OPENVPN_PERMISSION, Activity.RESULT_OK,null);
                }

            } catch (RemoteException ex) {
                // TODO Auto-generated catch block
                ex.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            Message msg = Message.obtain(mHandler, MSG_UPDATE_BIND_STATE, "服务绑定已断开");
            msg.sendToTarget();
        }

    };

    private boolean bindService() {
        Intent intent = new Intent(IOpenVPNAPIService.class.getName());
        intent.setPackage("de.blinkt.xp.openvpn");

        boolean ok = getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        if (!ok) {
            System.out.println("bindService IVPNAPIService fail");
            System.out.println(intent);
            mBindStatus.setText("服务绑定失败!\n" + "请确认OpenVPN for Android APP已安装并且赋予了自启动权限");
        }
        return ok;
    }

    private void unbindService() {
        if (mService != null) {
            getActivity().unbindService(mConnection);
        }
    }

    protected void listVPNs() {
        try {
            List<APIVpnProfile> list = mService.getProfiles();
            String all = "List:\n";
            for (APIVpnProfile profile : list.subList(0, Math.min(5, list.size()))) {
                all = all + profile.name + "\n";
            }

            if (list.size() > 5)
                all += "\n And some profiles....";

            if (list.size() > 0) {
                mStartVpn_aidl.setVisibility(View.VISIBLE);
                mStartVpn_intent.setVisibility(View.VISIBLE);
                mStartUUID = list.get(0).uuid;
                mStartName = list.get(0).name;
            }

            mVpnList.setText(all);

        } catch (Exception ex) {
            mVpnList.setText(ex.getMessage());
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startVPN_aidl:
                if (mService != null) {
                    try {
                        prepareStartProfile(START_PROFILE_BYUUID);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        mStatus.setText(ex.getMessage());
                    }
                }
                break;

            case R.id.startembedded_aidl:
                if (mService != null) {
                    try {
                        prepareStartProfile(START_PROFILE_EMBEDDED);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        mStatus.setText(ex.getMessage());
                    }
                }
                break;

            case R.id.startVPN_intent:
                Intent shortcutIntent0 = new Intent(Intent.ACTION_MAIN);
                shortcutIntent0.setClassName("de.blinkt.xp.openvpn", "de.blinkt.openvpn.api.ConnectVPN");
                shortcutIntent0.putExtra("de.blinkt.openvpn.api.profileName", mStartName);
                // 必须用startActivityForResult, 否则RemoteAction中getCallingPackage()返回null
//                startActivity(shortcutIntent);
                startActivityForResult(shortcutIntent0, 0);
                if (mService == null) {
                    for (int i = 0; i < 5; ++i) {
                        try {
                            Thread.sleep(1000);
                        } catch (Exception ex) {
                            // Ignore
                        } finally {
                            if (bindService())
                                break;
                        }
                    }
                }
                break;

            case R.id.startembedded_intent:
                String inlineConfig = readVPNConfig("test.conf");
                Intent shortcutIntent1 = new Intent(Intent.ACTION_MAIN);
                shortcutIntent1.setClassName("de.blinkt.xp.openvpn", "de.blinkt.openvpn.api.ConnectVPN");
                shortcutIntent1.putExtra("de.blinkt.openvpn.api.inlineConfig", inlineConfig);
                // 必须用startActivityForResult, 否则RemoteAction中getCallingPackage()返回null
//                startActivity(shortcutIntent);
                startActivityForResult(shortcutIntent1, 0);
                break;

            case R.id.disconnect:
                if (mService != null) {
                    try {
                        mService.disconnect();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        mStatus.setText(ex.getMessage());
                    }
                }
                break;

            default:
                break;
        }
    }

    private void prepareStartProfile(int requestCode) throws RemoteException {
        Intent requestpermission = mService.prepareVPNService();
        if(requestpermission == null) {
            onActivityResult(requestCode, Activity.RESULT_OK, null);
        } else {
            // Have to call an external Activity since services cannot used onActivityResult
            startActivityForResult(requestpermission, requestCode);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == START_PROFILE_EMBEDDED) {
                String inlineConfig = readVPNConfig("test.conf");
                try {
                    mService.startVPN(inlineConfig);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    mStatus.setText(ex.getMessage());
                }
            } else if (requestCode == START_PROFILE_BYUUID) {
                try {
                    if (mService != null)
                        mService.startProfile(mStartUUID);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                    mStatus.setText(ex.getMessage());
                }
            } else if (requestCode == ICS_OPENVPN_PERMISSION) {
                try {
                    if (mService != null) {
                        listVPNs();
                        mService.registerStatusCallback(mCallback);
                    }
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                    mStatus.setText(ex.getMessage());
                }
            }
        }
    };

}
