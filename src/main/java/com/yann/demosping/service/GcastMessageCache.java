package com.yann.demosping.service;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GcastMessageCache {
    private final ConcurrentHashMap<String, TdApi.InputMessageContent> contentMap = new ConcurrentHashMap<>();

    public void put(String sessionId, TdApi.InputMessageContent content) {
        contentMap.put(sessionId, content);
    }

    public TdApi.InputMessageContent get(String sessionId) {
        return contentMap.get(sessionId);
    }

    public void remove(String sessionId) {
        contentMap.remove(sessionId);
    }
}
