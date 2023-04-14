/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.utils.MailUtils;
import de.blinkt.openvpn.utils.OpenVPNUtils;

public class DiagnosticActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.diagnostic_activity);
        setFinishOnTouchOutside(false);
        // 后台发送
        sendDiagnosticInfo();
    }

    private void sendDiagnosticInfo() {
        new Thread(() -> {
            try {
                OpenVPNUtils.sleepForDebug(2000);
                MailUtils.sendMail(this);
                VpnStatus.logInfo("Send diagnostic info success");
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.mail_send_success, Toast.LENGTH_LONG).show();
                });

            } catch (Exception ex) {
                VpnStatus.logError("Send diagnostic info fail, " + ex.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.mail_send_fail, Toast.LENGTH_LONG).show();
                });

            } finally {
                runOnUiThread(() -> finish());
            }

        }).start();
    }

}
