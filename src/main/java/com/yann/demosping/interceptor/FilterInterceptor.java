package com.yann.demosping.interceptor;


import com.yann.demosping.manager.BotInterceptor;
import com.yann.demosping.service.ModuleStateService;
import com.yann.demosping.utils.CopyMessageUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Order(0)
public class FilterInterceptor implements BotInterceptor {

    private final ModuleStateService stateService;

    private final SimpleTelegramClient client;

    @Override
    public boolean preHandle(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        String textMessage = null;
        if (message.message.content instanceof TdApi.MessageVideo video) {
            textMessage = video.caption.text;
        } else if (message.message.content instanceof TdApi.MessagePhoto p) {
            textMessage = p.caption.text;
        } else if (message.message.content instanceof TdApi.MessageText text) {
            textMessage = text.text.text;
        }
        if (textMessage == null || textMessage.isEmpty()) {
            return true;
        }
        if (textMessage.contains("delfilter")) {
            return true;
        }
        Map<Object, Object> allFilters = stateService.getAllFilters(chatId);
        if (allFilters == null || allFilters.isEmpty()) {
            return true;
        }
        String storeValue = null;
        for (Map.Entry<Object, Object> entry : allFilters.entrySet()) {
            String trigger = entry.getKey().toString();
            if (textMessage.matches(".*\\b" + Pattern.quote(trigger) + "\\b.*")) {
                storeValue = entry.getValue().toString();
                break;
            }
        }
        if (storeValue == null || storeValue.isEmpty()) {
            return true;
        }
        String[] parts = storeValue.split(":");
        long sourceChatId = Long.parseLong(parts[0]);
        long sourceMessageId = Long.parseLong(parts[1]);

        client.send(
                new TdApi.GetMessage(sourceChatId, sourceMessageId), messageresult -> {
                    if (messageresult.isError()) {
                        return;
                    }
                    TdApi.InputMessageContent inputMessageContent = CopyMessageUtils.convertToInput(messageresult.get().content);
                    if (inputMessageContent != null) {
                        client.send(
                                new TdApi.SendMessage(
                                        chatId,
                                        0,
                                        new TdApi.InputMessageReplyToMessage(message.message.id, null, 0),
                                        null,
                                        null,
                                        inputMessageContent
                                )
                        );
                    }

                }
        );
        return true;
    }
}
