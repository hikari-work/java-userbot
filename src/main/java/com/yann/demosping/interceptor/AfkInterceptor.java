package com.yann.demosping.interceptor;

import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.manager.MessageInterceptor;
import com.yann.demosping.service.ModuleStateService;
import com.yann.demosping.service.SendMessageUtils;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class AfkInterceptor implements MessageInterceptor {

    private final SendMessageUtils sendMessageUtils;
    private final ModuleStateService moduleStateService;
    private final GlobalTelegramExceptionHandler exceptionHandler;

    @Value("${user.id}")
    private String userId;

    @Override
    public boolean preHandle(TdApi.UpdateNewMessage message, String text) {
        boolean isOutgoing = message.message.isOutgoing;

        if (isOutgoing) {
            if (moduleStateService.isAfk() && !isAfkStatusMessage(message)) {
                disableAfk(message);
                return false;
            }
            return true;
        }

        if (moduleStateService.isAfk() && isMentionedOrDirectMessage(message)) {
            sendAfkReply(message);
            return false;
        }

        return true;
    }

    private boolean isMentionedOrDirectMessage(TdApi.UpdateNewMessage message) {
        if (message.message.chatId > 0) {
            return true;
        }
        if (message.message.content instanceof TdApi.MessageText textContent) {
            for (TdApi.TextEntity entity : textContent.text.entities) {
                if (entity.type instanceof TdApi.TextEntityTypeMentionName mention) {
                    if (mention.userId == Long.parseLong(userId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void disableAfk(TdApi.UpdateNewMessage message) {
        long chatId = message.message.chatId;
        String responseText = "<b>Back Online</b>\nWas AFK for: <code>" + moduleStateService.getAfkDuration() + "</code>";
        sendMessageUtils.sendMessage(chatId, responseText)
                .exceptionally(ex -> {
                    exceptionHandler.handle(ex);
                    return null;
                });
        moduleStateService.setAfk(false, "false");
    }

    private void sendAfkReply(TdApi.UpdateNewMessage message) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;
        String responseText = "<b>User is AFK</b>\nReason: " + moduleStateService.getAfkReason();
        sendMessageUtils.sendMessage(chatId, messageId, responseText)
                .exceptionally(ex -> {
                    exceptionHandler.handle(ex);
                    return null;
                });
    }

    private boolean isAfkStatusMessage(TdApi.UpdateNewMessage message) {
        if (message.message.content instanceof TdApi.MessageText textContent) {
            String text = textContent.text.text;
            return text.contains("Back Online") || text.contains("User is AFK");
        }
        return false;
    }
}