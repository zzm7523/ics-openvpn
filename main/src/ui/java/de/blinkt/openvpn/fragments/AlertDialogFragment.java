/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class AlertDialogFragment extends DialogFragment {

    public static AlertDialogFragment newInstance(@NonNull String title, @NonNull String message, boolean finish) {
        AlertDialogFragment dialogFragment = new AlertDialogFragment();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        args.putBoolean("finish", finish);
        dialogFragment.setArguments(args);
        return dialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String title = requireArguments().getString("title");
        String message = requireArguments().getString("message");
        boolean finish = requireArguments().getBoolean("finish");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        if (finish) {
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> requireActivity().finish());
            builder.setOnCancelListener((dialog) -> requireActivity().finish());
            builder.setOnDismissListener((dialog) -> requireActivity().finish());
        } else {
            builder.setPositiveButton(android.R.string.ok, null);
        }

        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

}
