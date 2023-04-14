/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class IOUtils {

    public static byte[] readAssert(@NonNull Context context, @NonNull String name) throws IOException {
        try (BufferedInputStream buffIn = new BufferedInputStream(context.getAssets().open(name))) {
            ByteArrayOutputStream buffOut = new ByteArrayOutputStream();
            byte[] bytes = new byte[2048];
            int readLen;

            while ((readLen = buffIn.read(bytes, 0, bytes.length)) > 0) {
                buffOut.write(bytes, 0, readLen);
            }
            buffOut.flush();

            return buffOut.toByteArray();
        }
    }

    public static String readAssertAsString(@NonNull Context context, @NonNull String name) throws IOException {
        byte[] bytes = readAssert(context, name);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static byte[] readStream(@NonNull InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream buffOut = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[2048];

        try {
            long totalRead = 0;
            while ((nRead = in.read(data, 0, data.length)) != -1 && totalRead < maxBytes) {
                buffOut.write(data, 0, nRead);
                totalRead += nRead;
            }
            buffOut.flush();

        } finally {
            in.close();
        }

        return buffOut.toByteArray();
    }

    public static String readStreamAsString(@NonNull InputStream in, long maxBytes) throws IOException {
        byte[] bytes = readStream(in, maxBytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static long copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        long totalLen = 0;
        int nLen = 0;
        byte[] bytes = new byte[1024*8];

        while ((nLen = in.read(bytes)) != -1) {
            out.write(bytes, 0, nLen);
            totalLen += nLen;
        }
        out.flush();

        return totalLen;
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }

    private IOUtils() {
    }

}
