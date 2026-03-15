package com.yann.demosping.service;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatFolderCache {

    private final ConcurrentHashMap<Integer, String> folders = new ConcurrentHashMap<>();

    public void update(TdApi.UpdateChatFolders update) {
        folders.clear();
        if (update.chatFolders == null) return;
        for (TdApi.ChatFolderInfo info : update.chatFolders) {
            String name = (info.name != null && info.name.text != null) ? info.name.text.text : "Folder " + info.id;
            folders.put(info.id, name);
        }
    }

    public Map<Integer, String> getFolders() {
        return Collections.unmodifiableMap(folders);
    }
}
