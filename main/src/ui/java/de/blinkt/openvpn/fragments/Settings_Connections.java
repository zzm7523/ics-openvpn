/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.blinkt.xp.openvpn.R;

public class Settings_Connections extends Settings_Fragment implements View.OnClickListener {

    private ConnectionsAdapter mConnectionsAdapter;
    private TextView mWarning;
    private Checkable mUseRandomRemote;
    private RecyclerView mRecyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.connections, container, false);

        mConnectionsAdapter = new ConnectionsAdapter(getActivity(), this, mProfile);

        mWarning = v.findViewById(R.id.noserver_active_warning);
        mRecyclerView = v.findViewById(R.id.connection_recycler_view);

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mRecyclerView.setAdapter(mConnectionsAdapter);

        ImageButton addBtn = v.findViewById(R.id.add_new_remote);
        addBtn.setOnClickListener(this);

        mUseRandomRemote = v.findViewById(R.id.remote_random);
        mUseRandomRemote.setChecked(mProfile.isRemoteRandom());

        mConnectionsAdapter.displayWarningIfNoneEnabled();
        return v;
    }

    @Override
    public void onClick(@NonNull View v) {
        if (v.getId() == R.id.add_new_remote) {
            mConnectionsAdapter.addRemote();
        }
    }

    @Override
    protected void loadPreferences() {
    }

    @Override
    protected void savePreferences() {
        mConnectionsAdapter.saveProfile();
        mProfile.setRemoteRandom(mUseRandomRemote.isChecked());
    }

    public void setWarningVisible(int showWarning) {
        mWarning.setVisibility(showWarning);
    }

}
