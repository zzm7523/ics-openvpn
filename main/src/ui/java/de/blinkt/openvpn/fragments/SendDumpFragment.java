/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import de.blinkt.xp.openvpn.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.utils.ActivityUtils;

public class SendDumpFragment extends Fragment {

    private static Pair<File, Long> getLastestDump(@NonNull Context c) {
        if (c.getCacheDir() == null) {
            return null;
        }

        long newestDumpTime = 0;
        File newestDumpFile = null;

        for (File f : c.getCacheDir().listFiles()) {
            if (!f.getName().endsWith(".dmp"))
                continue;

            if (newestDumpTime < f.lastModified()) {
                newestDumpTime = f.lastModified();
                newestDumpFile = f;
            }
        }

        // Ignore old dumps
        if (System.currentTimeMillis() - 48 * 60 * 1000 > newestDumpTime)
            return null;

        return Pair.create(newestDumpFile, newestDumpTime);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_senddump, container, false);
        v.findViewById(R.id.senddump).setOnClickListener(v1 -> emailMiniDumps());

        // Do in background since it does I/O
        new Thread(() -> {
            final Pair<File, Long> newestDump = getLastestDump(getActivity());
            if (newestDump != null) {
                ActivityUtils.runOnUiThread(getActivity(), () -> {
                    TextView dumpDateText = v.findViewById(R.id.dumpdate);
                    String datestr = (new Date(newestDump.second)).toString();
                    long timediff = System.currentTimeMillis() - newestDump.second;
                    long minutes = timediff / 1000 / 60 % 60;
                    long hours = timediff / 1000 / 60 / 60;
                    dumpDateText.setText(getString(R.string.lastdumpdate, hours, minutes, datestr));
                });
            }
        }).start();

        return v;
    }

    private void emailMiniDumps() {
        Pair<File, Long> newestDump = getLastestDump(getActivity());
        if (newestDump == null) {
            VpnStatus.logError("No Minidump found!");
            return;
        }

        // need to "send multiple" to get more than one attachment
        Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"Arne Schwabe <arne@rfc2549.org>"});

        String packageName = getActivity().getPackageName();
        String version;
        String name = "ics-openvpn";

        try {
            PackageInfo packageinfo = getActivity().getPackageManager().getPackageInfo(packageName, 0);
            version = packageinfo.versionName;
            name = packageinfo.applicationInfo.name;
        } catch (NameNotFoundException ex) {
            version = "error fetching version";
        }

        emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format("%s(%s) %s Minidump", name, packageName, version));
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Please describe the issue you have experienced");
        emailIntent.setType("*/*");

        String authority = packageName + ".FileProvider";
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(Uri.parse("content://" + authority + "/" + newestDump.first.getName()));
        uris.add(Uri.parse("content://" + authority + "/" + newestDump.first.getName() + ".log"));

        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        startActivity(emailIntent);
    }

}
