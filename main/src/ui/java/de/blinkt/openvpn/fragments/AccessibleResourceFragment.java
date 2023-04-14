package de.blinkt.openvpn.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;

import de.blinkt.xp.openvpn.R;

import java.util.ArrayList;
import java.util.List;

import de.blinkt.openvpn.core.AccessibleResource;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.VpnTunnel;
import de.blinkt.openvpn.utils.ActivityUtils;

public class AccessibleResourceFragment extends Fragment implements VpnStatus.StatusListener, AdapterView.OnItemClickListener {

    private AccessibleResourceAdapter mAccessibleResourceAdapter;
    private TextView mAccessibleResourceSummary;
    private ListView mAccessibleResource;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.accessible_resource_list, container, false);

        mAccessibleResource = v.findViewById(R.id.accessible_resource);
        mAccessibleResourceAdapter = new AccessibleResourceAdapter(getContext(), new ArrayList<>());
        mAccessibleResource.setAdapter(mAccessibleResourceAdapter);
        mAccessibleResourceSummary = v.findViewById(R.id.accessible_resource_summary);

        // 打开资源
        mAccessibleResource.setOnItemClickListener(this);
        return v;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        AccessibleResource resource = (AccessibleResource) mAccessibleResourceAdapter.getItem(position);
        String resourceUri = resource.getUri();
        try {
            Uri uri = Uri.parse(resourceUri);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(getContext(), R.string.invalid_accessible_resource, Toast.LENGTH_LONG).show();
            ex.printStackTrace();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        VpnStatus.addStatusListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        VpnStatus.removeStatusListener(this);
    }

    private void updatePortalView(List<AccessibleResource> accessibleResources) {
        if (accessibleResources == null || accessibleResources.size() == 0) {
            mAccessibleResourceSummary.setVisibility(View.VISIBLE);
            mAccessibleResourceSummary.setText(getString(R.string.no_accessible_resource));
            mAccessibleResourceAdapter.setAccessibleResources(new ArrayList<>());
        } else {
            mAccessibleResourceSummary.setVisibility(View.GONE);
            mAccessibleResourceAdapter.setAccessibleResources(accessibleResources);
        }
    }

    @WorkerThread
    @Override
    public void updateStatus(@NonNull ConnectionStatus status, Bundle extra) {
        List<AccessibleResource> resources = null;
        if (extra != null) {
            extra.setClassLoader(AccessibleResource.class.getClassLoader());
            resources = (ArrayList<AccessibleResource>) extra.get("LAST_ACCESSIBLE_RESOURCES");
        }

        if (status.getLevel() == ConnectionStatus.LEVEL_CONNECTED) {
            final List<AccessibleResource> finalResources = resources;
            ActivityUtils.runOnUiThread(getActivity(), () -> updatePortalView(finalResources));
        } else {
            ActivityUtils.runOnUiThread(getActivity(), () -> updatePortalView(null));
        }
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

}
