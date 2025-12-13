package com.yann.demosping.utils;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ArgsParser {

    public static Map<String, String> parse(String args) {
        Map<String, String> params = new HashMap<>();
        if (args == null || args.isEmpty()) return Collections.emptyMap();
        String[] parts = args.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("-")) {
                String key = part.substring("-".length());
                if (i + 1 < parts.length && !parts[i + 1].startsWith("-")) {
                    params.put(key, parts[i + 1]);
                } else {
                    params.put(key, "true");
                }
            }
        }
        return params;
    }
}
