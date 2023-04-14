/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

    public static boolean isEmpty(@NonNull CharSequence charSequence) {
        return charSequence == null || charSequence.length() == 0;
    }

    public static String escape(String unescaped) {
        if (unescaped == null) {
            return null;
        }

        String escaped = unescaped.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        escaped = escaped.replace("\n", "\\n");

        if (escaped.equals(unescaped) && !escaped.contains(" ") && !escaped.contains("#")
                && !escaped.contains(";") && !escaped.equals("")) {
            return unescaped;
        } else {
            return '"' + escaped + '"';
        }
    }

    public static boolean endWithNewLine(@NonNull CharSequence charSequence) {
        int length = charSequence.length();
        if (length > 0) {
            char c = charSequence.charAt(length - 1);
            if (c == '\r' || c == '\n')
                return true;
        }
        return false;
    }

    public static String removeHeadTrail(@NonNull String text, @NonNull CharSequence what) {
        int startIdx = 0, endIdx = text.length() - 1;
        for (int i = 0; i < what.length(); ++i) {
            char c = what.charAt(i);
            if (text.charAt(startIdx) == c)
                ++startIdx;
            if (text.charAt(endIdx) == c)
                --endIdx;
        }

        return (endIdx > startIdx) ? text.substring(startIdx, endIdx + 1) : "";
    }

    public static boolean contains(@NonNull String regex, @NonNull CharSequence input) {
        Matcher matcher = Pattern.compile(regex).matcher(input);
        return matcher.find();
    }

}
