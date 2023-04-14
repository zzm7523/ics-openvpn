/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.ListFragment;

import de.blinkt.openvpn.core.AccessibleResource;
import de.blinkt.xp.openvpn.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import de.blinkt.openvpn.LaunchOpenVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.activities.DisconnectVPN;
import de.blinkt.openvpn.activities.MainActivity;
import de.blinkt.openvpn.activities.VPNPreferences;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.LogItem;
import de.blinkt.openvpn.core.LogLevel;
import de.blinkt.openvpn.core.LogSource;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.VpnTunnel;
import de.blinkt.openvpn.utils.ActivityUtils;

public class LogFragment extends ListFragment implements VpnStatus.StatusListener, AdapterView.OnItemSelectedListener,
        SeekBar.OnSeekBarChangeListener, RadioGroup.OnCheckedChangeListener {

    private static final int TIME_FORMAT_NONE = 0;
    private static final int TIME_FORMAT_SHORT = 1;
    private static final int TIME_FORMAT_ISO = 2;
    private static final int START_VPN_CONFIG = 0;

    private static final String LOG_LEVEL = "log_level";
    private static final String LOG_TIMEFORMAT = "log_timeformat";

    private Spinner mLogSourceSpinner;
    private SeekBar mLogLevelSlider;
    private LinearLayout mOptionsLayout;
    private RadioGroup mTimeRadioGroup;
    private boolean mShowOptionsLayout;
    private CheckBox mClearLogCheckBox;
    private TextView mConnectStatus;

    private LogListAdapter ladapter;

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mLogSourceSpinner)
            ladapter.setLogSource(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        ladapter.setLogLevel(progressToLogLevel(progress));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.radioISO:
                ladapter.setTimeFormat(LogFragment.TIME_FORMAT_ISO);
                break;
            case R.id.radioNone:
                ladapter.setTimeFormat(LogFragment.TIME_FORMAT_NONE);
                break;
            case R.id.radioShort:
                ladapter.setTimeFormat(LogFragment.TIME_FORMAT_SHORT);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.clearlog) {
            ladapter.clearLog();
            return true;

        } else if (item.getItemId() == R.id.cancel) {
            Intent intent = new Intent(getActivity(), DisconnectVPN.class);
            startActivity(intent);
            return true;

        } else if (item.getItemId() == R.id.send) {
            ladapter.shareLog();
            return true;

        } else if (item.getItemId() == R.id.edit_vpn) {
            VpnProfile lastConnectedProfile = ProfileManager.getInstance(getActivity()).get(getActivity(), VpnStatus.getConnectedVpnProfile());
            if (lastConnectedProfile != null) {
                Intent intent = new Intent(getActivity(), VPNPreferences.class);
                intent.putExtra(VpnProfile.EXTRA_PROFILE_UUID, lastConnectedProfile.getUUIDString());
                startActivityForResult(intent, START_VPN_CONFIG);
            } else {
                Toast.makeText(getActivity(), R.string.log_no_last_vpn, Toast.LENGTH_LONG).show();
            }
            return true;

        } else if (item.getItemId() == R.id.toggle_time) {
            showHideOptionsPanel();
            return true;

        } else if (item.getItemId() == android.R.id.home) {
            // This is called when the Home (Up) button is pressed in the Action Bar.
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            getActivity().finish();
            return true;

        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void showHideOptionsPanel() {
        boolean optionsVisible = (mOptionsLayout.getVisibility() != View.GONE);
        ObjectAnimator anim;

        if (optionsVisible) {
            anim = ObjectAnimator.ofFloat(mOptionsLayout, "alpha", 1.0f, 0f);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    mOptionsLayout.setVisibility(View.GONE);
                }
            });

        } else {
            mOptionsLayout.setVisibility(View.VISIBLE);
            anim = ObjectAnimator.ofFloat(mOptionsLayout, "alpha", 0f, 1.0f);
        }

        anim.start();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.log_menu, menu);
        if (getResources().getBoolean(R.bool.logSildersAlwaysVisible))
            menu.removeItem(R.id.toggle_time);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        VpnStatus.addStatusListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        VpnStatus.removeStatusListener(this);
        Preferences.getVariableSharedPreferences(getActivity()).edit()
            .putInt(LOG_LEVEL, ladapter.mLogLevel.getInt())
            .putInt(LOG_TIMEFORMAT, ladapter.mTimeFormat).apply();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ListView lv = getListView();

        lv.setOnItemLongClickListener((parent, view, position, id) -> {
            ClipboardManager clipboard = (ClipboardManager)
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Log Entry", ((TextView) view).getText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getActivity(), R.string.copied_entry, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.log_fragment, container, false);
        setHasOptionsMenu(true);

        SharedPreferences prefs = Preferences.getVariableSharedPreferences(getActivity());
        LogLevel logLevel = LogLevel.getEnumByValue(prefs.getInt(LOG_LEVEL, LogLevel.INFO.getInt()));
        int timeFormat = prefs.getInt(LOG_TIMEFORMAT, TIME_FORMAT_SHORT);

        // 初始总是显示LogSource.OPENVPN_FRONT日志
        ladapter = new LogListAdapter(LogSource.OPENVPN_FRONT, logLevel, timeFormat);
        setListAdapter(ladapter);
        VpnStatus.addLogListener(ladapter);

        mLogSourceSpinner = v.findViewById(R.id.log_source);
        mLogSourceSpinner.setSelection(LogSource.OPENVPN_FRONT.getInt());
        mLogSourceSpinner.setOnItemSelectedListener(this);

        int maxLogProgress = logLevelToProgress(LogLevel.VERBOSE);
        int curLogProgress = logLevelToProgress(logLevel);

        mLogLevelSlider = v.findViewById(R.id.LogLevelSlider);
        mLogLevelSlider.setMax(maxLogProgress);
        mLogLevelSlider.setProgress(curLogProgress);
        mLogLevelSlider.setOnSeekBarChangeListener(this);

        mTimeRadioGroup = v.findViewById(R.id.timeFormatRadioGroup);
        mTimeRadioGroup.setOnCheckedChangeListener(this);
        if (ladapter.mTimeFormat == LogFragment.TIME_FORMAT_ISO) {
            mTimeRadioGroup.check(R.id.radioISO);
        } else if (ladapter.mTimeFormat == LogFragment.TIME_FORMAT_NONE) {
            mTimeRadioGroup.check(R.id.radioNone);
        } else if (ladapter.mTimeFormat == LogFragment.TIME_FORMAT_SHORT) {
            mTimeRadioGroup.check(R.id.radioShort);
        }

        mClearLogCheckBox = v.findViewById(R.id.clearlogconnect);
        mClearLogCheckBox.setChecked(prefs.getBoolean(LaunchOpenVPN.CLEAR_LOG, true));
        mClearLogCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(LaunchOpenVPN.CLEAR_LOG, isChecked).apply();
        });

        mConnectStatus = v.findViewById(R.id.status);
        mOptionsLayout = v.findViewById(R.id.logOptionsLayout);
        if (mShowOptionsLayout || getResources().getBoolean(R.bool.logSildersAlwaysVisible)) {
            mOptionsLayout.setVisibility(View.VISIBLE);
        }

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Scroll to the end of the list end
//      getListView().setSelection(getListView().getAdapter().getCount() - 1);
    }

    @Override
    public void onAttach(@NonNull Context activity) {
        super.onAttach(activity);
        if (mOptionsLayout != null && getResources().getBoolean(R.bool.logSildersAlwaysVisible)) {
            mShowOptionsLayout = true;
            mOptionsLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == START_VPN_CONFIG && resultCode == AppCompatActivity.RESULT_OK) {
            String configuredVPN = data.getStringExtra(VpnProfile.EXTRA_PROFILE_UUID);
            VpnProfile profile = ProfileManager.getInstance(getActivity()).get(getActivity(), configuredVPN);
            ProfileManager.getInstance(getActivity()).saveProfile(getActivity(), profile);

            // Name could be modified, reset List adapter
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.configuration_changed);
            builder.setMessage(R.string.restart_vpn_after_change);
            builder.setPositiveButton(R.string.restart, (dialog, which) -> {
                Intent intent = new Intent(getActivity(), LaunchOpenVPN.class);
                intent.putExtra(LaunchOpenVPN.EXTRA_KEY, profile.getUUIDString());
                intent.setAction(Intent.ACTION_MAIN);
                startActivity(intent);
            });
            builder.setNegativeButton(R.string.ignore, null);
            Dialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
    }

    @Override
    public void onDestroy() {
        VpnStatus.removeLogListener(ladapter);
        super.onDestroy();
    }

    @Override
    public void updateStatus(@NonNull ConnectionStatus status, Bundle extra) {
        if (isAdded()) {
            String statusString = status.getString(getActivity());
            ActivityUtils.runOnUiThread(getActivity(), () -> {
                if (isAdded()) {
                    if (mConnectStatus != null)
                        mConnectStatus.setText(statusString);
                }
            });
        }
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

    private LogLevel progressToLogLevel(int progress) {
        switch (progress) {
            case 0:
                return LogLevel.ERROR;
            case 1:
                return LogLevel.WARNING;
            case 2:
                return LogLevel.INFO;
            case 3:
                return LogLevel.DEBUG;
            default:
                return LogLevel.VERBOSE;
        }
    }

    private int logLevelToProgress(@NonNull LogLevel logLevel) {
        switch (logLevel) {
            case ERROR:
                return 0;
            case WARNING:
                return 1;
            case INFO:
                return 2;
            case DEBUG:
                return 3;
            default:
                return 4;
        }
    }

    class LogListAdapter implements ListAdapter, Callback, VpnStatus.LogListener {

        private static final int MESSAGE_NEWLOG = 0;
        private static final int MESSAGE_CLEARLOG = 1;
        private static final int MESSAGE_NEWTS = 2;
        private static final int MESSAGE_NEWLOGSOURCE = 3;
        private static final int MESSAGE_NEWLOGLEVEL = 4;

        private static final int MAX_LOG_ENTRIES = 1000;
        private final LinkedList<LogItem> allLogBuffers = new LinkedList<>();
        private final LinkedList<LogItem> currLogBuffers = new LinkedList<>();

        private final Handler mHandler;
        private final List<DataSetObserver> observers = new ArrayList<>();

        private LogSource mLogSource;
        private LogLevel mLogLevel;
        private int mTimeFormat;

        LogListAdapter(@NonNull LogSource logSource, @NonNull LogLevel logLevel, int timeFormat) {
            this.mLogSource = logSource;
            this.mLogLevel = logLevel;
            this.mTimeFormat = timeFormat;
            this.mHandler = new Handler(this);
            initLogBuffer();
        }

        private void initLogBuffer() {
            allLogBuffers.clear();
            synchronized (VpnStatus.LOG_LOCK) {
                allLogBuffers.addAll(VpnStatus.getLogBufferAll());
            }
            initCurrentLogBuffers();
        }

        private void shareLog() {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ics_openvpn_log_file));

            StringBuilder buffer = new StringBuilder(8192);

            if (mLogSource == LogSource.OPENVPN_FRONT)
                buffer.append("<OPENVPN_FRONT>\n");
            else if (mLogSource == LogSource.OPENVPN_CONSOLE)
                buffer.append("<OPENVPN_CONSOLE>\n");
            else if (mLogSource == LogSource.OPENVPN_MANAGEMENT)
                buffer.append("<OPENVPN_MANAGEMENT>\n");

            for (LogItem item : currLogBuffers) {
                buffer.append(getTime(item, TIME_FORMAT_ISO)).append(item.getString(getActivity())).append('\n');
            }

            intent.putExtra(Intent.EXTRA_TEXT, buffer.toString());
            startActivity(Intent.createChooser(intent, "Send Logfile"));
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            observers.add(observer);
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            observers.remove(observer);
        }

        @Override
        public int getCount() {
            return currLogBuffers.size();
        }

        @Override
        public Object getItem(int position) {
            return currLogBuffers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return currLogBuffers.get(position).hashCode();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView v = (convertView == null) ? new TextView(getActivity()) : (TextView) convertView;
            LogItem item = currLogBuffers.get(position);
            String msg = item.getString(getActivity());
            String time = getTime(item, mTimeFormat);
            v.setText(new SpannableString(time + msg));
            return v;
        }

        private String getTime(LogItem item, int time) {
            if (time != TIME_FORMAT_NONE) {
                java.text.DateFormat timeformat;
                if (time == TIME_FORMAT_ISO)
                    timeformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                else
                    timeformat = android.text.format.DateFormat.getTimeFormat(getActivity());
                return timeformat.format(new Date(item.getLogtime())) + " ";
            } else {
                return "";
            }
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return currLogBuffers.isEmpty();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public void newLog(LogItem logItem) {
            Message msg = Message.obtain();
            assert (msg != null);
            msg.what = MESSAGE_NEWLOG;
            Bundle bundle = new Bundle();
            bundle.putParcelable("log_message", logItem);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }

        @Override
        public boolean handleMessage(Message msg) {
            // We have been called
            if (msg.what == MESSAGE_NEWLOG) {
                LogItem logItem = msg.getData().getParcelable("log_message");
                if (addLogItem(logItem)) {
                    for (DataSetObserver observer : observers) {
                        observer.onChanged();
                    }
                }

            } else if (msg.what == MESSAGE_CLEARLOG) {
                for (DataSetObserver observer : observers) {
                    observer.onInvalidated();
                }
                initLogBuffer();

            } else if (msg.what == MESSAGE_NEWTS) {
                for (DataSetObserver observer : observers) {
                    observer.onInvalidated();
                }

            } else if (msg.what == MESSAGE_NEWLOGSOURCE) {
                initCurrentLogBuffers();
                for (DataSetObserver observer : observers) {
                    observer.onChanged();
                }

            } else if (msg.what == MESSAGE_NEWLOGLEVEL) {
                initCurrentLogBuffers();
                for (DataSetObserver observer : observers) {
                    observer.onChanged();
                }
            }

            return true;
        }

        private void initCurrentLogBuffers() {
            currLogBuffers.clear();
            for (LogItem li : allLogBuffers) {
                if (li.getLogSource() == mLogSource && li.getLogLevel().getInt() >= mLogLevel.getInt()) {
                    currLogBuffers.add(li);
                }
            }
        }

        /**
         * @return True if the current entries have changed
         */
        private boolean addLogItem(LogItem logItem) {
            allLogBuffers.add(logItem);

            if (allLogBuffers.size() > MAX_LOG_ENTRIES) {
                while (allLogBuffers.size() < (MAX_LOG_ENTRIES - 50)) {
                    allLogBuffers.remove(0);
                }
                initCurrentLogBuffers();
                return true;

            } else {
                if (logItem.getLogSource() == mLogSource && (logItem.getLogLevel().getInt() >= mLogLevel.getInt())) {
                    currLogBuffers.add(logItem);
                    return true;
                } else {
                    return false;
                }
            }
        }

        void clearLog() {
            VpnStatus.clearLog();
            VpnStatus.logInfo(R.string.logCleared);
            mHandler.sendEmptyMessage(MESSAGE_CLEARLOG);
        }

        void setLogSource(int value) {
            switch (value) {
                case 0:
                    mLogSource = LogSource.OPENVPN_FRONT;
                    break;
                case 1:
                    mLogSource = LogSource.OPENVPN_CONSOLE;
                    break;
                case 2:
                    mLogSource = LogSource.OPENVPN_MANAGEMENT;
                    break;
                default:
                    mLogSource = LogSource.OPENVPN_FRONT;
                    break;
            }
            mHandler.sendEmptyMessage(MESSAGE_NEWLOGSOURCE);
        }

        void setLogLevel(@NonNull LogLevel logLevel) {
            mLogLevel = logLevel;
            mHandler.sendEmptyMessage(MESSAGE_NEWLOGLEVEL);
        }

        void setTimeFormat(int newTimeFormat) {
            mTimeFormat = newTimeFormat;
            mHandler.sendEmptyMessage(MESSAGE_NEWTS);
        }

    }

}
