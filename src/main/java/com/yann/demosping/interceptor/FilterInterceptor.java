package com.yann.demosping.interceptor;

import com.yann.demosping.manager.MessageInterceptor;
import com.yann.demosping.service.CopyMessage;
import com.yann.demosping.service.ModuleStateService;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Order(2)
@Component
public class FilterInterceptor implements MessageInterceptor {

    private final ModuleStateService moduleStateService;
    private final SimpleTelegramClient client;
    private final CopyMessage copyMessage;

    public FilterInterceptor(ModuleStateService moduleStateService,
                             @Qualifier("userBotClient") SimpleTelegramClient client,
                             CopyMessage copyMessage) {
        this.moduleStateService = moduleStateService;
        this.client = client;
        this.copyMessage = copyMessage;
    }

    @Override
    public boolean preHandle(TdApi.UpdateNewMessage message, String text) {
        if (message.message.isOutgoing) {
            return true;
        }

        long chatId = message.message.chatId;
        String messageText = extractText(message);
        if (messageText == null || messageText.isBlank()) {
            return true;
        }

        Map<Object, Object> filters = moduleStateService.getAllFilters(chatId);
        if (filters == null || filters.isEmpty()) {
            return true;
        }

        String savedValue = findMatchingFilter(messageText, filters);
        if (savedValue == null) {
            return true;
        }

        replyWithFilteredMessage(chatId, message.message.id, savedValue);
        return true;
    }

    private String extractText(TdApi.UpdateNewMessage message) {
        if (message.message.content instanceof TdApi.MessageText text) {
            return text.text.text;
        } else if (message.message.content instanceof TdApi.MessageVideo video) {
            return video.caption.text;
        } else if (message.message.content instanceof TdApi.MessagePhoto photo) {
            return photo.caption.text;
        }
        return null;
    }

    private String findMatchingFilter(String messageText, Map<Object, Object> filters) {
        for (Map.Entry<Object, Object> entry : filters.entrySet()) {
            String trigger = entry.getKey().toString();
            if (messageText.matches(".*\\b" + Pattern.quote(trigger) + "\\b.*")) {
                return entry.getValue().toString();
            }
        }
        return null;
    }

    private void replyWithFilteredMessage(long chatId, long replyToMessageId, String savedValue) {
        String[] parts = savedValue.split(":");
        long sourceChatId = Long.parseLong(parts[0]);
        long sourceMessageId = Long.parseLong(parts[1]);

        client.send(new TdApi.GetMessage(sourceChatId, sourceMessageId), result -> {
            if (result.isError()) {
                return;
            }
            TdApi.InputMessageContent inputContent = copyMessage.convertToInput(result.get().content);
            if (inputContent != null) {
                client.send(new TdApi.SendMessage(
                        chatId,
                        0,
                        new TdApi.InputMessageReplyToMessage(replyToMessageId, null, 0),
                        null,
                        null,
                        inputContent
                ));
            }
        });
    }
}