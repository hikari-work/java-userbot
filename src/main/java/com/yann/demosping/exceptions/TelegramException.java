package com.yann.demosping.exceptions;

import it.tdlight.jni.TdApi;
import lombok.Getter;

@Getter
public class TelegramException extends RuntimeException {
    private final Long chatId;
    private final Long messageId;
    private final TdApi.Error tdError;

    public TelegramException(String message, Long chatId, Long messageId) {
        super(message);
        this.chatId = chatId;
        this.messageId = messageId;
        this.tdError = null;
    }

    public TelegramException(String message, Long chatId, Long messageId, TdApi.Error tdError) {
        super(message + ": " + (tdError != null ? tdError.message: " "));
        this.chatId = chatId;
        this.messageId = messageId;
        this.tdError = tdError;
    }
    public int getErrorCode() {
        return tdError != null ? tdError.code : -1;
    }

}
