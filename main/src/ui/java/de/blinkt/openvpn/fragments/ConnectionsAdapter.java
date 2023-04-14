/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.Connection;

public class ConnectionsAdapter extends RecyclerView.Adapter<ConnectionsAdapter.ConnectionsHolder> {

    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_FOOTER = TYPE_NORMAL + 1;

    private final Context mContext;
    private final Settings_Connections mConnectionFragment;
    private final VpnProfile mProfile;
    private Connection[] mConnections;

    ConnectionsAdapter(@NonNull Context c, @NonNull Settings_Connections s, @NonNull VpnProfile profile) {
        mContext = c;
        mConnectionFragment = s;
        mProfile = profile;
        mConnections = profile.getConnections();
    }

    @Override
    public ConnectionsAdapter.ConnectionsHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        LayoutInflater li = LayoutInflater.from(mContext);
        View card;
        if (viewType == TYPE_NORMAL) {
            card = li.inflate(R.layout.server_card, viewGroup, false);
        } else { // TYPE_FOOTER
            card = li.inflate(R.layout.server_footer, viewGroup, false);
        }
        return new ConnectionsHolder(card, this, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ConnectionsAdapter.ConnectionsHolder holder, int position) {
        if (position == mConnections.length) {
            // Footer
            return;
        }

        final Connection connection = mConnections[position];
        holder.mConnection = null;

        holder.mServerNameView.setText(connection.getServerName());
        holder.mPortNumberView.setText(connection.getServerPort());
        holder.mRemoteSwitch.setChecked(connection.isEnabled());

        holder.mProxyNameView.setText(connection.getProxyName());
        holder.mProxyPortNumberView.setText(connection.getProxyPort());

        holder.mConnectText.setText(String.valueOf(connection.getConnectTimeout()));
        holder.mConnectSlider.setProgress(connection.getConnectTimeout());

        holder.mProtoGroup.check(connection.isUseUdp() ? R.id.udp_proto : R.id.tcp_proto);
        switch (connection.getProxyType()) {
            case NONE:
                holder.mProxyGroup.check(R.id.proxy_none);
                break;
            case HTTP:
                holder.mProxyGroup.check(R.id.proxy_http);
                break;
            case SOCKS5:
                holder.mProxyGroup.check(R.id.proxy_socks);
                break;
        }

        holder.mProxyAuthCb.setChecked(connection.isUseProxyAuth());
        holder.mProxyAuthUsername.setText(connection.getProxyAuthUsername());
        holder.mProxyAuthPassword.setText(connection.getProxyAuthPassword());

        holder.mCustomOptionsLayout.setVisibility(connection.isUseCustomConfig() ? View.VISIBLE : View.GONE);
        holder.mCustomOptionText.setText(connection.getCustomConfiguration());
        holder.mCustomOptionCB.setChecked(connection.isUseCustomConfig());

        holder.mConnection = connection;
        setVisibilityProxyServer(holder, connection);
    }

    private void setVisibilityProxyServer(ConnectionsHolder holder, Connection connection) {
        int proxyVisible = connection.getProxyType() == Connection.ProxyType.NONE ? View.GONE : View.VISIBLE;
        int authVisible = connection.getProxyType() == Connection.ProxyType.HTTP ? View.VISIBLE : View.GONE;

        holder.mProxyNameView.setVisibility(proxyVisible);
        holder.mProxyPortNumberView.setVisibility(proxyVisible);
        holder.mProxyNameLabel.setVisibility(proxyVisible);
        holder.mProxyPortLabel.setVisibility(proxyVisible);
        holder.mProxyAuthLayout.setVisibility(authVisible);
    }

    @Override
    public int getItemCount() {
        return mConnections.length + 1; //for footer
    }

    @Override
    public int getItemViewType(int position) {
        return position == mConnections.length ? TYPE_FOOTER : TYPE_NORMAL;
    }

    private void removeRemote(int idx) {
        Connection[] mConnections2 = Arrays.copyOf(mConnections, mConnections.length - 1);
        for (int i = idx + 1; i < mConnections.length; i++) {
            mConnections2[i - 1] = mConnections[i];
        }
        mConnections = mConnections2;
        notifyItemRemoved(idx);
        displayWarningIfNoneEnabled();
    }

    void addRemote() {
        mConnections = Arrays.copyOf(mConnections, mConnections.length + 1);
        mConnections[mConnections.length - 1] = new Connection();
        // FIX BUG （360 7.1.1） notifyItemInserted(0) 不刷新UI
        if (mConnections.length == 1)
            notifyDataSetChanged();
        else
            notifyItemInserted(mConnections.length - 1);
        displayWarningIfNoneEnabled();
    }

    void displayWarningIfNoneEnabled() {
        int showWarning = View.VISIBLE;
        for (Connection conn : mConnections) {
            if (conn.isEnabled()) {
                showWarning = View.GONE;
                break;
            }
        }
        mConnectionFragment.setWarningVisible(showWarning);
    }

    void saveProfile() {
        mProfile.setConnections(mConnections);
    }

    abstract class OnTextChangedWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }
    }

    class ConnectionsHolder extends RecyclerView.ViewHolder {

        private final EditText mServerNameView;
        private final EditText mPortNumberView;
        private final Switch mRemoteSwitch;
        private final RadioGroup mProtoGroup;
        private final EditText mCustomOptionText;
        private final CheckBox mCustomOptionCB;
        private final View mCustomOptionsLayout;
        private final ImageButton mDeleteButton;
        private final EditText mConnectText;
        private final SeekBar mConnectSlider;
        private final ConnectionsAdapter mConnectionsAdapter;
        private final RadioGroup mProxyGroup;
        private final EditText mProxyNameView;
        private final EditText mProxyPortNumberView;
        private final View mProxyNameLabel;
        private final View mProxyPortLabel;
        private final View mProxyAuthLayout;
        private final EditText mProxyAuthUsername;
        private final EditText mProxyAuthPassword;
        private final CheckBox mProxyAuthCb;

        private Connection mConnection; // Set to null on update

        ConnectionsHolder(View card, ConnectionsAdapter connectionsAdapter, int viewType) {
            super(card);
            mServerNameView = card.findViewById(R.id.servername);
            mPortNumberView = card.findViewById(R.id.portnumber);
            mRemoteSwitch = card.findViewById(R.id.remoteSwitch);
            mCustomOptionCB = card.findViewById(R.id.use_customoptions);
            mCustomOptionText = card.findViewById(R.id.customoptions);
            mProtoGroup = card.findViewById(R.id.udptcpradiogroup);
            mCustomOptionsLayout = card.findViewById(R.id.custom_options_layout);
            mDeleteButton = card.findViewById(R.id.remove_connection);
            mConnectSlider = card.findViewById(R.id.connect_silder);
            mConnectText = card.findViewById(R.id.connect_timeout);

            mProxyGroup = card.findViewById(R.id.proxyradiogroup);
            mProxyNameView = card.findViewById(R.id.proxyname);
            mProxyPortNumberView = card.findViewById(R.id.proxyport);
            mProxyNameLabel = card.findViewById(R.id.proxyserver_label);
            mProxyPortLabel = card.findViewById(R.id.proxyport_label);

            mProxyAuthLayout = card.findViewById(R.id.proxyauthlayout);
            mProxyAuthCb = card.findViewById(R.id.enable_proxy_auth);
            mProxyAuthUsername = card.findViewById(R.id.proxyuser);
            mProxyAuthPassword = card.findViewById(R.id.proxypassword);

            mConnectionsAdapter = connectionsAdapter;
            if (viewType == TYPE_NORMAL) {
                addListeners();
            }
        }

        void addListeners() {
            mRemoteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (mConnection != null) {
                    mConnection.setEnabled(isChecked);
                    mConnectionsAdapter.displayWarningIfNoneEnabled();
                }
            });

            mProtoGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (mConnection != null) {
                    mConnection.setUseUdp(checkedId == R.id.udp_proto);
                }
            });

            mProxyGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (mConnection != null) {
                    switch (checkedId) {
                        case R.id.proxy_none:
                            mConnection.setProxyType(Connection.ProxyType.NONE);
                            break;
                        case R.id.proxy_http:
                            mConnection.setProxyType(Connection.ProxyType.HTTP);
                            break;
                        case R.id.proxy_socks:
                            mConnection.setProxyType(Connection.ProxyType.SOCKS5);
                            break;
                    }
                    setVisibilityProxyServer(this, mConnection);
                }
            });

            mProxyAuthCb.setOnCheckedChangeListener((group, isChecked) -> {
                if (mConnection != null) {
                    mConnection.setUseProxyAuth(isChecked);
                    setVisibilityProxyServer(this, mConnection);
                }
            });

            mCustomOptionText.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null)
                        mConnection.setCustomConfiguration(s.toString());
                }
            });

            mCustomOptionCB.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (mConnection != null) {
                    mConnection.setUseCustomConfig(isChecked);
                    mCustomOptionsLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
            });

            mServerNameView.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.setServerName(s.toString());
                    }
                }

            });

            mPortNumberView.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.setServerPort(s.toString());
                    }
                }
            });

            mProxyNameView.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.setProxyName(s.toString());
                    }
                }

            });

            mProxyPortNumberView.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.setProxyPort(s.toString());
                    }
                }
            });

            mProxyAuthPassword.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.setProxyAuthPassword(s.toString());
                    }
                }
            });

            mProxyAuthUsername.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        mConnection.setProxyAuthUsername(s.toString());
                    }
                }
            });

            mConnectSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mConnection != null) {
                        mConnectText.setText(String.valueOf(progress));
                        mConnection.setConnectTimeout(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            mConnectText.addTextChangedListener(new OnTextChangedWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (mConnection != null) {
                        try {
                            int t = Integer.valueOf(s.toString());
                            mConnectSlider.setProgress(t);
                            mConnection.setConnectTimeout(t);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });

            mDeleteButton.setOnClickListener(v -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle(R.string.query_delete_remote);
                    builder.setPositiveButton(R.string.keep, null);
                    builder.setNegativeButton(R.string.delete, (dialog, which) -> removeRemote(getAdapterPosition()));
                    builder.show();
                }
            );

        }
    }

}
