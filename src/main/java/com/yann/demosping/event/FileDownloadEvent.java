package com.yann.demosping.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class FileDownloadEvent extends ApplicationEvent {
    private final int fileId;
    private final String localPath;
    private final long chatId;
    private final long messageId;

    public FileDownloadEvent(Object source, int fileId, String localPath, long chatId, long messageId) {
        super(source);
        this.fileId = fileId;
        this.localPath = localPath;
        this.chatId = chatId;
        this.messageId = messageId;
    }
}