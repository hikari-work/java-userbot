package com.yann.demosping.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArgsParser {

    /**
     * Regex Penjelasan:
     * -([\w-]+)       -> Menangkap Key yang diawali '-' (Group 1)
     * (?:\s+          -> Group non-capturing untuk spasi pemisah
     * (?:
     * "([^"]*)"   -> Opsi A: Value dalam tanda kutip ganda (Group 2)
     * |
     * '([^']*)'   -> Opsi B: Value dalam tanda kutip satu (Group 3)
     * |
     * ([^\s-][^\s]*) -> Opsi C: Value biasa (TIDAK boleh diawali '-') (Group 4)
     * )
     * )?              -> Seluruh bagian value bersifat opsional (untuk flag boolean)
     */
    private static final Pattern ARG_PATTERN = Pattern.compile("-([\\w-]+)(?:\\s+(?:\"([^\"]*)\"|'([^']*)'|([^\\s-]\\S*)))?");

    public static Map<String, String> parse(String text) {
        Map<String, String> params = new HashMap<>();
        if (text == null || text.isEmpty()) return params;

        Matcher matcher = ARG_PATTERN.matcher(text);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = null;

            if (matcher.group(2) != null) {
                value = matcher.group(2);
            } else if (matcher.group(3) != null) {
                value = matcher.group(3);
            } else if (matcher.group(4) != null) {
                value = matcher.group(4);
            }

            params.put(key, value != null ? value : "true");
        }
        return params;
    }
}