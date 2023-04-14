/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemWriter;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import javax.security.auth.x500.X500Principal;

import de.blinkt.xp.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.VpnStatus;

public class X509Utils {

    public static X509Certificate readX509Certificate(@NonNull String data) throws IOException, CertificateException {
        CertificateFactory certFact = CertificateFactory.getInstance("X.509");

        try (InputStream input = new ByteArrayInputStream(data.getBytes())) {
            return (X509Certificate) certFact.generateCertificate(input);
        }
    }

    public static Certificate[] readCertificatesFromFile(@NonNull String certfilename) throws IOException, CertificateException {
        CertificateFactory certFact = CertificateFactory.getInstance("X.509");
        List<Certificate> certificates = new ArrayList<>();

        if (VpnProfile.isEmbedded(certfilename)) {
            int subIndex = certfilename.indexOf("-----BEGIN CERTIFICATE-----");
            do {
                // The java certifcate reader is ... kind of stupid
                // It does NOT ignore chars before the --BEGIN ...
                subIndex = Math.max(0, subIndex);
                InputStream inStream = new ByteArrayInputStream(certfilename.substring(subIndex).getBytes());
                certificates.add(certFact.generateCertificate(inStream));
                subIndex = certfilename.indexOf("-----BEGIN CERTIFICATE-----", subIndex + 1);

            } while (subIndex > 0);

            return certificates.toArray(new Certificate[certificates.size()]);

        } else {
            try (InputStream input = new FileInputStream(certfilename)) {
                return new Certificate[]{certFact.generateCertificate(input)};
            }
        }
    }

    public static String getCertificateFriendlyName(@NonNull Context c, String filename) {
        if (!TextUtils.isEmpty(filename)) {
            try {
                X509Certificate cert = (X509Certificate) readCertificatesFromFile(filename)[0];
                String friendlycn = getCertificateFriendlyName(cert);
                friendlycn = getCertificateValidityString(cert, c.getResources()) + friendlycn;
                return friendlycn;

            } catch (Exception e) {
                VpnStatus.logError("Could not read certificate" + e.getLocalizedMessage());
            }
        }
        return c.getString(R.string.cannotparsecert);
    }

    public static String getCertificateValidityString(@NonNull X509Certificate cert, @NonNull Resources res) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException ce) {
            return String.format("%s: %s", res.getString(R.string.cert_expired), df.format(cert.getNotAfter()));
        } catch (CertificateNotYetValidException cny) {
            return String.format("%s: %s", res.getString(R.string.cert_no_yet_valid), df.format(cert.getNotBefore()));
        }

        Date certNotAfter = cert.getNotAfter();
        Date now = new Date();
        long timeLeft = certNotAfter.getTime() - now.getTime(); // Time left in ms

        // More than 72h left, display days
        // More than 3 months display months
        if (timeLeft > 90l * 24 * 3600 * 1000) {
            long months = getMonthsDifference(now, certNotAfter);
            return res.getQuantityString(R.plurals.months_left, (int) months, months);
        } else if (timeLeft > 72 * 3600 * 1000) {
            long days = timeLeft / (24 * 3600 * 1000);
            return res.getQuantityString(R.plurals.days_left, (int) days, days);
        } else {
            long hours = timeLeft / (3600 * 1000);
            return res.getQuantityString(R.plurals.hours_left, (int) hours, hours);
        }
    }

    public static int getMonthsDifference(@NonNull Date date1, @NonNull Date date2) {
        int m1 = date1.getYear() * 12 + date1.getMonth();
        int m2 = date2.getYear() * 12 + date2.getMonth();
        return m2 - m1 + 1;
    }

    private static String[] getCertificateFriendlyNameInternal(@NonNull X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        String friendlyName = principal.getName();

        // Really evil hack to decode email address
        // See: http://code.google.com/p/android/issues/detail?id=21531
        String[] parts = friendlyName.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("1.2.840.113549.1.9.1=#16")) {
                parts[i] = "email=" + ia5decode(part.replace("1.2.840.113549.1.9.1=#16", ""));
            }
        }

        return parts;
    }

    public static String getCertificateFriendlyName(@NonNull X509Certificate cert) {
        String[] parts = getCertificateFriendlyNameInternal(cert);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("1.2.840.113549.1.9.1=#16")) {
                parts[i] = "email=" + ia5decode(part.replace("1.2.840.113549.1.9.1=#16", ""));
            }
        }
        String friendlyName = TextUtils.join(",", parts);
        return friendlyName;
    }

    // E=me@myhost.mydomain,CN=Test-Client,O=OpenVPN-TEST,ST=NA,C=KG
    public static String getCertificateAlias(@NonNull X509Certificate cert, @Nullable String fieldName, @Nullable String suffix) {
        String[] parts = getCertificateFriendlyNameInternal(cert);
        String alias = null;
        if (!TextUtils.isEmpty(fieldName))
            alias = getFieldValue(parts, fieldName);
        if (alias == null)
            alias = getFieldValue(parts, "CN");
        if (alias == null)
            alias = getFieldValue(parts, "EMAIL");
        if (alias == null)
            alias = getFieldValue(parts, "E");
        if (alias == null)
            alias = getFieldValue(parts, "OU");
        if (alias == null)
            alias = getFieldValue(parts, "O");
        return (alias == null ? "" : alias) + (suffix == null ? "" : suffix);
    }

    private static String getFieldValue(String[] parts, String name) {
        String lcname = name.toLowerCase() + "=";
        for (String nv : parts) {
            String lcnv = nv.toLowerCase();
            if (lcnv.startsWith(lcname)) {
                return nv.substring(lcname.length()).trim();
            }
        }
        return null;
    }

    private static boolean isPrintableChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return (!Character.isISOControl(c)) &&
            block != null && block != Character.UnicodeBlock.SPECIALS;
    }

    private static String ia5decode(@NonNull String ia5string) {
        String d = "";
        for (int i = 1; i < ia5string.length(); i = i + 2) {
            String hexstr = ia5string.substring(i - 1, i + 1);
            char c = (char) Integer.parseInt(hexstr, 16);
            if (isPrintableChar(c)) {
                d += c;
            } else if (i == 1 && (c == 0x12 || c == 0x1b)) {
                // ignore
            } else {
                d += "\\x" + hexstr;
            }
        }
        return d;
    }

    public static String encodeToPem(@NonNull X509Certificate cert) throws CertificateException, IOException {
        StringWriter strWriter = new StringWriter();
        PemWriter pemWriter = new PemWriter(strWriter);
        pemWriter.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
        pemWriter.close();
        return strWriter.toString();
    }

    public static String encodeToPemChain(@NonNull X509Certificate[] certChain, int startIndex) throws CertificateException, IOException {
        StringBuilder buffer = new StringBuilder(2048);
        for (int i = startIndex; i < certChain.length; i++) {
            X509Certificate cert = certChain[i];
            if (cert != null)
                buffer.append(encodeToPem(cert));
        }
        return buffer.toString();
    }

}
