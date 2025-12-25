package com.yann.demosping.exceptions;

import it.tdlight.jni.TdApi;

public class SendMessageNotCompleteException extends TelegramException{
    public SendMessageNotCompleteException(String message, Long chatId, Long messageId) {
        super(message, chatId, messageId);
    }

    public SendMessageNotCompleteException(String message, Long chatId, Long messageId, TdApi.Error tdError) {
        super(message, chatId, messageId, tdError);
    }
    public boolean isRateLimited() {
        return getTdError() != null && getTdError().code == 429;
    }
    public boolean isChatNotFound() {
        return getChatId() != null && getTdError().code == 400 &&
                getTdError().message.contains("chat not found");
    }
}
