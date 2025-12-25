package com.yann.demosping.exceptions;

import it.tdlight.jni.TdApi;

public class GetMessageException extends TelegramException{
    public GetMessageException(String message, Long chatId, Long messageId) {
        super(message, chatId, messageId);
    }

    public GetMessageException(String message, Long chatId, Long messageId, TdApi.Error tdError) {
        super(message, chatId, messageId, tdError);
    }
}
