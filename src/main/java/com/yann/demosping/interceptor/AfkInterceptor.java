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
import reactor.core.publisher.Mono;

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
    public Mono<Boolean> preHandle(TdApi.UpdateNewMessage message, String text) {
        boolean isOutgoing = message.message.isOutgoing;

        if (isOutgoing) {
            return moduleStateService.isAfk()
                    .flatMap(afk -> {
                        if (afk && !isAfkStatusMessage(message)) {
                            return disableAfk(message).thenReturn(false);
                        }
                        return Mono.just(true);
                    });
        }

        return moduleStateService.isAfk()
                .flatMap(afk -> {
                    if (afk && isMentionedOrDirectMessage(message)) {
                        sendAfkReply(message);
                        return Mono.just(false);
                    }
                    return Mono.just(true);
                });
    }

    private boolean isMentionedOrDirectMessage(TdApi.UpdateNewMessage message) {
        if (message.message.chatId > 0) return true;
        if (message.message.content instanceof TdApi.MessageText textContent) {
            for (TdApi.TextEntity entity : textContent.text.entities) {
                if (entity.type instanceof TdApi.TextEntityTypeMentionName mention) {
                    if (mention.userId == Long.parseLong(userId)) return true;
                }
            }
        }
        return false;
    }

    private Mono<Void> disableAfk(TdApi.UpdateNewMessage message) {
        long chatId = message.message.chatId;
        return moduleStateService.getAfkDuration()
                .flatMap(duration -> {
                    String responseText = "<b>Back Online</b>\nWas AFK for: <code>" + duration + "</code>";
                    return sendMessageUtils.sendMessage(chatId, responseText)
                            .then(moduleStateService.setAfk(false, "false"));
                })
                .doOnError(exceptionHandler::handle)
                .then();
    }

    private void sendAfkReply(TdApi.UpdateNewMessage message) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;
        moduleStateService.getAfkReason()
                .flatMap(reason -> {
                    String responseText = "<b>User is AFK</b>\nReason: " + reason;
                    return sendMessageUtils.sendMessage(chatId, messageId, responseText);
                })
                .doOnError(exceptionHandler::handle)
                .subscribe();
    }

    private boolean isAfkStatusMessage(TdApi.UpdateNewMessage message) {
        if (message.message.content instanceof TdApi.MessageText textContent) {
            String t = textContent.text.text;
            return t.contains("Back Online") || t.contains("User is AFK");
        }
        return false;
    }
}
