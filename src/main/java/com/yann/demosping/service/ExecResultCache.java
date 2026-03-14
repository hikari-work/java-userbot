package com.yann.demosping.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary cache used to pass shell execution results from ExecPlugin (userbot side)
 * to the Exec inline handler (bot side), so the bot can answer inline queries
 * with the actual output and buttons already prepared.
 */
@Component
public class ExecResultCache {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public void put(String cmd, String result) {
        cache.put(cmd, result);
    }

    public String getAndRemove(String cmd) {
        return cache.remove(cmd);
    }
}
