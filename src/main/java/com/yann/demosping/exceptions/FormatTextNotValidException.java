package com.yann.demosping.exceptions;

import it.tdlight.jni.TdApi;

public class FormatTextNotValidException extends TelegramException {

    public FormatTextNotValidException(String message, Long chatId, Long messageId) {
        super(message, chatId, messageId);
    }

    public FormatTextNotValidException(String message, Long chatId, Long messageId, TdApi.Error tdError) {
        super(message, chatId, messageId, tdError);
    }
}
