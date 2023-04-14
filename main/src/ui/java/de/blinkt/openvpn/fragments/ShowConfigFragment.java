/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.utils.ActivityUtils;


public class ShowConfigFragment extends Fragment {

    private ImageButton mFabButton;
    private TextView mConfigView;
    private String mConfigText;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.view_config, container, false);
        mConfigView = v.findViewById(R.id.configview);

        mFabButton = v.findViewById(R.id.share_config);
        if (mFabButton != null) {
            mFabButton.setOnClickListener(v1 -> shareConfig());
            mFabButton.setVisibility(View.INVISIBLE);
        }

        return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // VPNPreferences.class 有共享VpnProfile菜单, 此次不要再添加共享VpnConfig菜单了; 有FabButton就行了
        setHasOptionsMenu(false);
    }

/*
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.config_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.sendConfig) {
            shareConfig();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
*/

    @Override
    public void onResume() {
        super.onResume();
        populateConfigText();
    }

    private void shareConfig() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, mConfigText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_config_title));
        shareIntent.setType("text/plain");
        startActivity(Intent.createChooser(shareIntent, getString(R.string.export_config_chooser_title)));
    }

    private void populateConfigText() {
        String profileUUID = requireArguments().getString(VpnProfile.EXTRA_PROFILE_UUID);
        VpnProfile profile = ProfileManager.getInstance(getActivity()).get(getActivity(), profileUUID);
        int check = profile.checkProfile(getActivity());

        if (R.string.no_error_found != check) {
            mConfigView.setText(check);
            mConfigText = getString(check);

        } else {
            // Run in own Thread since Keystore does not like to be queried from the main thread
            mConfigView.setText("Generating config...");
            new Thread(() -> {
                try {
                    /* Add a few newlines to make the textview scrollable past the FAB */
                    mConfigText = profile.generateConfig(requireContext()) + "\n\n\n";

                } catch (Exception ex) {
                    ex.printStackTrace();
                    mConfigText = "Error generating config file: " + ex.getLocalizedMessage();
                }

                ActivityUtils.runOnUiThread(getActivity(), () -> {
                    mConfigView.setText(mConfigText);
                    if (mFabButton != null)
                        mFabButton.setVisibility(View.VISIBLE);
                });

            }).start();
        }
    }

}
