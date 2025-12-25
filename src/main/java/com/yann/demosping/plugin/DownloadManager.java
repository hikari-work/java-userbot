package com.yann.demosping.plugin;

import com.yann.demosping.event.FileDownloadEvent;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class DownloadManager {

    private final ApplicationEventPublisher publisher;

    private final Map<Integer, String> pendingDownloads = new ConcurrentHashMap<>();

    public void trackDownload(int fileId, long chatId, long messageId) {
        pendingDownloads.put(fileId, chatId + ":" + messageId);
    }
    private void onUpdateFile(TdApi.UpdateFile updateFile) {
        TdApi.File file = updateFile.file;
        if (pendingDownloads.containsKey(file.id)) {

            if (file.local.isDownloadingCompleted) {
                String context = pendingDownloads.remove(file.id);
                String[] split = context.split(":");
                long chatId = Long.parseLong(split[0]);
                long messageId = Long.parseLong(split[1]);
                publisher.publishEvent(new FileDownloadEvent(
                        this,
                        file.id,
                        file.local.path,
                        chatId,
                        messageId
                ));
            }
        }
    }
}
