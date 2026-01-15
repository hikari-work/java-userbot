package com.yann.demosping.interceptor;

import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.manager.BotInterceptor;
import com.yann.demosping.service.ModuleStateService;
import com.yann.demosping.service.SendMessageUtils;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Order(0)
@Component
@RequiredArgsConstructor
public class AFKInterceptor implements BotInterceptor {

    private final SendMessageUtils sendMessageUtils;
    private final GlobalTelegramExceptionHandler globalTelegramExceptionHandler;

    @Value("${user.id}")
    private String userId;

    private final ModuleStateService moduleStateService;


    @Override
    public boolean preHandle(TdApi.UpdateNewMessage message, String args) {
        if (isMe(message)) {
            if (moduleStateService.isAfk()) {
                if (!isAfkNotification(message)) disableAfk(message);
                return false;
            }
            return true;
        }
        if (!isMe(message) && moduleStateService.isAfk()) {
            if (isMentioned(message)) {
                notifyIsUserAfk(message);
                return false;
            }
            return true;
        }
        return true;
    }
    private boolean isMe(TdApi.UpdateNewMessage message) {
        TdApi.Message messages = message.message;
        if (messages.senderId instanceof TdApi.MessageSenderUser sender) {
            return sender.userId == Long.parseLong(userId);
        }
        return false;
    }
    private boolean isMentioned(TdApi.UpdateNewMessage message) {
        if (message.message.chatId > 0) return true;
        if (message.message.content instanceof TdApi.MessageText text) {
            for (TdApi.TextEntity entity : text.text.entities) {
                if (entity.type instanceof TdApi.TextEntityTypeMentionName mention) {
                    if (mention.userId == Long.parseLong(userId)) return false;
                }
            }
        }
        return false;
    }
    private void disableAfk(TdApi.UpdateNewMessage message) {
        long chatId = message.message.chatId;
        String sb = "<b>Kembali Online</b>\n" +
                "Setelah AFK : <code>" + moduleStateService.getAfkDuration() + "</code>";
        sendMessageUtils.sendMessage(chatId, sb).exceptionally(ex -> {
           globalTelegramExceptionHandler.handle(ex);
           return null;
        });
        moduleStateService.setAfk(false, "false");
    }
    private void notifyIsUserAfk(TdApi.UpdateNewMessage message) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;
        String text = "<b>User sedang AFK</b>\nKarena : " + moduleStateService.getAfkReason();
        sendMessageUtils.sendMessage(chatId, messageId, text).exceptionally(ex -> {
            globalTelegramExceptionHandler.handle(ex);
            return null;
        });
    }
    private boolean isAfkNotification(TdApi.UpdateNewMessage message) {
        if (message.message.content instanceof TdApi.MessageText text) {
            log.info(text.text.text);
            return text.text.text.startsWith("User sedang AFK");
        }
        return false;
    }
}
