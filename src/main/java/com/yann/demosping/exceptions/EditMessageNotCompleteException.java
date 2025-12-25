package com.yann.demosping.exceptions;

import it.tdlight.jni.TdApi;

public class EditMessageNotCompleteException extends TelegramException{
    public EditMessageNotCompleteException(String message, Long chatId, Long messageId) {
        super(message, chatId, messageId);
    }

    public EditMessageNotCompleteException(String message, Long chatId, Long messageId, TdApi.Error tdError) {
        super(message, chatId, messageId, tdError);
    }
}
