/*
 * Copyright (c) 2012-2019 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.ListFragment;

import de.blinkt.xp.openvpn.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import de.blinkt.openvpn.LaunchOpenVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.activities.ConfigConverter;
import de.blinkt.openvpn.activities.DisconnectVPN;
import de.blinkt.openvpn.activities.VPNPreferences;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.utils.ActivityUtils;


public class VPNProfileList extends ListFragment implements OnClickListener, VpnStatus.StatusListener {

    public final static int RESULT_VPN_DELETED = AppCompatActivity.RESULT_FIRST_USER;
    public final static int RESULT_VPN_DUPLICATE = AppCompatActivity.RESULT_FIRST_USER + 1;

    // Shortcut version is increased to refresh all shortcuts
    private static final int SHORTCUT_VERSION = 1;
    private static final int START_VPN_CONFIG = 92;
    private static final int IMPORT_PROFILE = 43;
    private static final int FILE_PICKER_RESULT = 392;

    private static final int MENU_ADD_PROFILE = Menu.FIRST;
    private static final int MENU_IMPORT_PROFILE = Menu.FIRST + 1;
    private static final int MENU_CHANGE_SORTING = Menu.FIRST + 2;
    private static final String PREF_SORT_BY_LRU = "sort_profiles_by_lru";

    private VpnProfile mEditProfile;
    private Intent mLastIntent;
    private ConnectionStatus mLastConnectionStatus;
    private ArrayAdapter<VpnProfile> mArrayadapter;

    @Override
    public void updateStatus(@NonNull ConnectionStatus status, Bundle extra) {
        ActivityUtils.runOnUiThread(getActivity(), () -> {
            mLastConnectionStatus = new ConnectionStatus(status);
            if (extra != null)
                mLastIntent = extra.getParcelable("LAST_INTENT");
            mArrayadapter.notifyDataSetChanged();
        });
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

    private void startOrStopVPN(@NonNull VpnProfile profile) {
        if (VpnStatus.isVPNActive()) {
            if (profile.getUUIDString().equals(VpnStatus.getConnectedVpnProfile())) {
                if (mLastIntent != null) {
                    startActivity(mLastIntent);
                } else {
                    Intent disconnectVPN = new Intent(getActivity(), DisconnectVPN.class);
                    startActivity(disconnectVPN);
                }
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.vpn_connected);
                builder.setMessage(R.string.disconnect_vpn);
                builder.setPositiveButton(android.R.string.ok, null);
                builder.show();
            }
        } else {
            ProfileManager.getInstance(getActivity()).saveProfile(getActivity(), profile);
            Intent startVPN = new Intent(getActivity(), LaunchOpenVPN.class);
            startVPN.putExtra(LaunchOpenVPN.EXTRA_KEY, profile.getUUIDString());
            startVPN.setAction(Intent.ACTION_MAIN);
            startActivity(startVPN);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private void updateDynamicShortcuts() {
        ShortcutManager shortcutManager = getContext().getSystemService(ShortcutManager.class);
        if (shortcutManager == null || shortcutManager.isRateLimitingActive())
            return;

        List<ShortcutInfo> shortcuts = shortcutManager.getDynamicShortcuts();
        int maxvpn = shortcutManager.getMaxShortcutCountPerActivity() - 1;

        PersistableBundle versionExtras = new PersistableBundle();
        versionExtras.putInt("version", SHORTCUT_VERSION);

        ShortcutInfo disconnectShortcut = new ShortcutInfo.Builder(getContext(), "disconnectVPN")
            .setShortLabel(getString(R.string.cancel_connection))
            .setLongLabel(getString(R.string.cancel_connection_long))
            .setIntent(new Intent(getContext(), DisconnectVPN.class).setAction(OpenVPNService.DISCONNECT_VPN))
            .setIcon(Icon.createWithResource(getContext(), R.drawable.ic_shortcut_cancel))
            .setExtras(versionExtras)
            .build();

        List<ShortcutInfo> newShortcuts = new LinkedList<>();
        List<ShortcutInfo> updateShortcuts = new LinkedList<>();

        List<String> removeShortcuts = new LinkedList<>();
        List<String> disableShortcuts = new LinkedList<>();

        boolean addDisconnect = true;

        TreeSet<VpnProfile> sortedProfilesLRU = new TreeSet<>(new VpnProfileLRUComparator());
        ProfileManager profileManager = ProfileManager.getInstance(getContext());
        sortedProfilesLRU.addAll(profileManager.getProfiles());

        LinkedList<VpnProfile> LRUProfiles = new LinkedList<>();
        maxvpn = Math.min(maxvpn, sortedProfilesLRU.size());

        for (int i = 0; i < maxvpn; i++) {
            LRUProfiles.add(sortedProfilesLRU.pollFirst());
        }

        for (ShortcutInfo shortcut : shortcuts) {
            if (shortcut.getId().equals("disconnectVPN")) {
                addDisconnect = false;
                if (shortcut.getExtras() == null || shortcut.getExtras().getInt("version") != SHORTCUT_VERSION)
                    updateShortcuts.add(disconnectShortcut);

            } else {
                VpnProfile profile = profileManager.get(getContext(), shortcut.getId());
                if (profile == null) {
                    if (shortcut.isEnabled()) {
                        disableShortcuts.add(shortcut.getId());
                        removeShortcuts.add(shortcut.getId());
                    }
                    if (!shortcut.isPinned())
                        removeShortcuts.add(shortcut.getId());

                } else {
                    if (LRUProfiles.contains(profile))
                        LRUProfiles.remove(profile);
                    else
                        removeShortcuts.add(profile.getUUIDString());

                    if (!profile.getName().equals(shortcut.getShortLabel()) || shortcut.getExtras() == null
                        || shortcut.getExtras().getInt("version") != SHORTCUT_VERSION)
                        updateShortcuts.add(createShortcut(profile));
                }
            }
        }

        if (addDisconnect)
            newShortcuts.add(disconnectShortcut);
        for (VpnProfile p : LRUProfiles)
            newShortcuts.add(createShortcut(p));

        if (updateShortcuts.size() > 0)
            shortcutManager.updateShortcuts(updateShortcuts);
        if (removeShortcuts.size() > 0)
            shortcutManager.removeDynamicShortcuts(removeShortcuts);
        if (newShortcuts.size() > 0)
            shortcutManager.addDynamicShortcuts(newShortcuts);
        if (disableShortcuts.size() > 0)
            shortcutManager.disableShortcuts(disableShortcuts, "VpnProfile does not exist anymore.");
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private ShortcutInfo createShortcut(VpnProfile profile) {
        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClass(getActivity(), LaunchOpenVPN.class);
        shortcutIntent.putExtra(LaunchOpenVPN.EXTRA_KEY, profile.getUUIDString());
        shortcutIntent.setAction(Intent.ACTION_MAIN);
        shortcutIntent.putExtra("EXTRA_HIDELOG", true);

        PersistableBundle versionExtras = new PersistableBundle();
        versionExtras.putInt("version", SHORTCUT_VERSION);

        return new ShortcutInfo.Builder(getContext(), profile.getUUIDString())
            .setShortLabel(profile.getName())
            .setLongLabel(getString(R.string.qs_connect, profile.getName()))
            .setIcon(Icon.createWithResource(getContext(), R.drawable.ic_shortcut_vpn_key))
            .setIntent(shortcutIntent)
            .setExtras(versionExtras)
            .build();
    }

    @Override
    public void onResume() {
        super.onResume();
        setListAdapter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            updateDynamicShortcuts();
        }
        VpnStatus.addStatusListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        VpnStatus.removeStatusListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.vpn_profile_list, container, false);

        ((TextView) v.findViewById(R.id.add_new_vpn_hint)).setText(Html.fromHtml(getString(R.string.add_new_vpn_hint),
            new MiniImageGetter(), null));
        ((TextView) v.findViewById(R.id.import_vpn_hint)).setText(Html.fromHtml(getString(R.string.vpn_import_hint),
            new MiniImageGetter(), null));

        ImageButton fab_add = v.findViewById(R.id.fab_add);
        if (fab_add != null)
            fab_add.setOnClickListener(this);

        ImageButton fab_import = v.findViewById(R.id.fab_import);
        if (fab_import != null)
            fab_import.setOnClickListener(this);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter();
    }

    private void setListAdapter() {
        if (mArrayadapter == null) {
            mArrayadapter = new VpnProfileArrayAdapter(getActivity(), R.layout.vpn_list_item, R.id.vpn_item_title);
        }
        populateVpnList();
    }

    private void populateVpnList() {
        boolean sortByLRU = Preferences.getVariableSharedPreferences(getActivity()).getBoolean(PREF_SORT_BY_LRU, false);
        ProfileManager profileManager = ProfileManager.getInstance(getActivity());

        List<VpnProfile> profiles = new ArrayList<>();
        profiles.addAll(ProfileManager.getInstance(getActivity()).getProfiles());
        if (sortByLRU)
            Collections.sort(profiles, new VpnProfileLRUComparator());
        else
            Collections.sort(profiles, new VpnProfileNameComparator());

        mArrayadapter.clear();
        VpnProfile tmpProfile = profileManager.getTemporaryProfile();
        if (tmpProfile != null && tmpProfile == profileManager.getConnectedVpnProfile())
            mArrayadapter.add(tmpProfile);
        mArrayadapter.addAll(profiles);

        setListAdapter(mArrayadapter);
        mArrayadapter.notifyDataSetChanged();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.add(0, MENU_ADD_PROFILE, 0, R.string.menu_add_profile)
            .setIcon(R.drawable.ic_menu_add)
            .setAlphabeticShortcut('a')
            .setTitleCondensed(getActivity().getString(R.string.add))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(0, MENU_IMPORT_PROFILE, 0, R.string.menu_import)
            .setIcon(R.drawable.ic_menu_import)
            .setAlphabeticShortcut('i')
            .setTitleCondensed(getActivity().getString(R.string.menu_import_short))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(0, MENU_CHANGE_SORTING, 0, R.string.change_sorting)
            .setIcon(R.drawable.ic_sort)
            .setAlphabeticShortcut('s')
            .setTitleCondensed(getString(R.string.sort))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == MENU_ADD_PROFILE) {
            onAddOrDuplicateProfile(null);
            return true;
        } else if (itemId == MENU_IMPORT_PROFILE) {
            return startImportConfigFilePicker();
        } else if (itemId == MENU_CHANGE_SORTING) {
            return changeSorting();
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fab_import) {
            startImportConfigFilePicker();
        } else if (v.getId() == R.id.fab_add) {
            onAddOrDuplicateProfile(null);
        }
    }

    private boolean changeSorting() {
        SharedPreferences prefs = Preferences.getVariableSharedPreferences(requireActivity());
        boolean oldValue = prefs.getBoolean(PREF_SORT_BY_LRU, false);
        prefs.edit().putBoolean(PREF_SORT_BY_LRU, !oldValue).apply();
        if (oldValue) {
            Toast.makeText(getActivity(), R.string.sorted_az, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), R.string.sorted_lru, Toast.LENGTH_SHORT).show();
        }
        populateVpnList();
        return true;
    }

    public boolean startImportConfigFilePicker() {
        Intent intent = FilePickerUtils.getFilePickerIntent(getActivity(), FilePickerUtils.FileType.OVPN_CONFIG);
        if (intent != null) {
            startActivityForResult(intent, FILE_PICKER_RESULT);
            return true;
        } else {
            return false;
        }
    }

    public void onAddOrDuplicateProfile(final VpnProfile copyProfile) {
        Context context = getActivity();
        if (context == null) {
            return;
        }

        final EditText entry = new EditText(context);
        entry.setSingleLine();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (copyProfile == null)
            builder.setTitle(R.string.menu_add_profile);
        else {
            builder.setTitle(context.getString(R.string.duplicate_profile_title, copyProfile.getName()));
            entry.setText(getString(R.string.copy_of_profile, copyProfile.getName()));
        }

        builder.setMessage(R.string.add_profile_name_prompt);
        builder.setView(entry);

        builder.setNeutralButton(R.string.menu_import_short, (dialog, which) -> startImportConfigFilePicker());
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String name = entry.getText().toString();
            if (!TextUtils.isEmpty(name) && ProfileManager.getInstance(getActivity()).getProfileByName(name) == null) {
                VpnProfile newProfile;
                if (copyProfile != null) {
                    newProfile = copyProfile.copy(name);
                    // Remove restrictions on copy profile
                    newProfile.setProfileCreator(null);
                    newProfile.setUserEditable(true);
                } else
                    newProfile = new VpnProfile(name);

                addProfile(newProfile);
                editVPN(newProfile);

            } else {
                Toast.makeText(getActivity(), R.string.duplicate_profile_name, Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void addProfile(@NonNull VpnProfile profile) {
        ProfileManager profileManager = ProfileManager.getInstance(getActivity());
        profileManager.addProfile(profile);
        profileManager.saveProfile(getActivity(), profile);
        profileManager.saveProfileList(getActivity());
        mArrayadapter.add(profile);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        ProfileManager profileManager = ProfileManager.getInstance(getActivity());

        if (resultCode == RESULT_VPN_DELETED) {
            if (mArrayadapter != null && mEditProfile != null)
                mArrayadapter.remove(mEditProfile);

        } else if (resultCode == RESULT_VPN_DUPLICATE && data != null) {
            String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILE_UUID);
            VpnProfile profile = profileManager.get(getActivity(), profileUUID);
            if (profile != null)
                onAddOrDuplicateProfile(profile);
        }

        if (resultCode == AppCompatActivity.RESULT_OK) {
            if (requestCode == START_VPN_CONFIG) {
                int profileOldHash = data.getIntExtra(VpnProfile.EXTRA_PROFILE_HASH, 0);
                String profileUuid = data.getStringExtra(VpnProfile.EXTRA_PROFILE_UUID);
                VpnProfile profile = profileManager.get(getActivity(), profileUuid);
                ProfileManager.getInstance(getActivity()).saveProfile(getActivity(), profile);
                // Name could be modified, reset List adapter
                setListAdapter();

            } else if (requestCode == FILE_PICKER_RESULT) {
                if (data != null) {
                    Uri uri = data.getData();
                    startConfigImport(uri);
                }

            } else if (requestCode == IMPORT_PROFILE) {
                String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILE_UUID);
                mArrayadapter.add(profileManager.get(getActivity(), profileUUID));
            }
        }
    }

    private void startConfigImport(@NonNull Uri uri) {
        Intent startImport = new Intent(getActivity(), ConfigConverter.class);
        startImport.setAction(ConfigConverter.IMPORT_PROFILE);
        startImport.setData(uri);
        startActivityForResult(startImport, IMPORT_PROFILE);
    }

    private void editVPN(@NonNull VpnProfile profile) {
        Intent intent = new Intent(getActivity(), VPNPreferences.class);
        mEditProfile = profile;

        intent.putExtra(VpnProfile.EXTRA_PROFILE_HASH, profile.hashCode());
        intent.putExtra(VpnProfile.EXTRA_PROFILE_UUID, profile.getUUIDString());

        startActivityForResult(intent, START_VPN_CONFIG);
    }

    static private class VpnProfileNameComparator implements Comparator<VpnProfile> {

        @Override
        public int compare(VpnProfile lhs, VpnProfile rhs) {
            if (lhs == rhs)
                // Catches also both null
                return 0;

            if (lhs == null)
                return -1;
            if (rhs == null)
                return 1;

            if (lhs.getName() == null)
                return -1;
            if (rhs.getName() == null)
                return 1;

            return lhs.getName().compareTo(rhs.getName());
        }

    }

    static private class VpnProfileLRUComparator implements Comparator<VpnProfile> {

        VpnProfileNameComparator nameComparator = new VpnProfileNameComparator();

        @Override
        public int compare(VpnProfile lhs, VpnProfile rhs) {
            if (lhs == rhs)
                // Catches also both null
                return 0;

            if (lhs == null)
                return -1;
            if (rhs == null)
                return 1;

            // Copied from Long.compare
            if (lhs.getLastUsed() > rhs.getLastUsed())
                return -1;
            if (lhs.getLastUsed() < rhs.getLastUsed())
                return 1;
            else
                return nameComparator.compare(lhs, rhs);
        }

    }

    private class VpnProfileArrayAdapter extends ArrayAdapter<VpnProfile> {

        public VpnProfileArrayAdapter(Context context, int resource, int textViewResourceId) {
            super(context, resource, textViewResourceId);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = super.getView(position, convertView, parent);

            final VpnProfile profile = (VpnProfile) getListAdapter().getItem(position);

            View title = v.findViewById(R.id.vpn_list_item_left);
            title.setOnClickListener((view) -> startOrStopVPN(profile));

            View settings = v.findViewById(R.id.quickedit_settings);
            settings.setOnClickListener((view) -> editVPN(profile));

            TextView subtitle = v.findViewById(R.id.vpn_item_subtitle);
            if (profile.getUUIDString().equals(VpnStatus.getConnectedVpnProfile())) {
                subtitle.setText(mLastConnectionStatus.getString(getActivity()));
                subtitle.setVisibility(View.VISIBLE);
            } else {
                subtitle.setText("");
                subtitle.setVisibility(View.GONE);
            }

            return v;
        }
    }

    private class MiniImageGetter implements ImageGetter {

        @Override
        public Drawable getDrawable(String source) {
            Drawable drawable = null;
            if ("ic_menu_add".equals(source)) {
                drawable = requireActivity().getResources().getDrawable(R.drawable.ic_menu_add_grey);
            } else if ("ic_menu_archive".equals(source)) {
                drawable = requireActivity().getResources().getDrawable(R.drawable.ic_menu_import_grey);
            }

            if (drawable != null)
                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());

            return drawable;
        }
    }

}
