package com.yann.demosping.interceptor;

import com.yann.demosping.manager.BotInterceptor;
import com.yann.demosping.service.ModuleStateService;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(0)
@Component
@RequiredArgsConstructor
public class AFKInterceptor implements BotInterceptor {

    private final SimpleTelegramClient client;

    @Value("${user.id}")
    private String userId;

    private final ModuleStateService moduleStateService;


    @Override
    public boolean preHandle(TdApi.UpdateNewMessage message, String args) {
        if (isMe(message)) {
            if (moduleStateService.isAfk()) {
                disableAfk(message);
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
                    if (mention.userId == Long.parseLong(userId)) return true;
                }
            }
        }
        return false;
    }
    private void disableAfk(TdApi.UpdateNewMessage message) {
        long chatId = message.message.chatId;
        String sb = "<b>Kembali Online</b>\n" +
                "AFK Setelah : <code>" + moduleStateService.getAfkDuration() + "</code>";
        client.send(new TdApi.ParseTextEntities(sb, new TdApi.TextParseModeHTML()), parsedText -> {
            if (parsedText.isError()) {
                return;
            }
            TdApi.FormattedText text = parsedText.get();
            client.send(
                    new TdApi.SendMessage(
                        chatId, 0L, null, null, null,
                            new TdApi.InputMessageText(text, new TdApi.LinkPreviewOptions(), false)
            ));
        });
        moduleStateService.setAfk(false, "false");
    }
    private void notifyIsUserAfk(TdApi.UpdateNewMessage message) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;
        String text = "<b>User sedang AFK\nKarena : " + moduleStateService.getAfkReason();
        client.send(
                new TdApi.ParseTextEntities(
                        text, new TdApi.TextParseModeHTML()
                ), textFormat -> {
                    TdApi.FormattedText formattedText = textFormat.get();
                    client.send(
                            new TdApi.SendMessage(
                                    chatId, messageId, null, null, null,
                                    new TdApi.InputMessageText(
                                            formattedText, new TdApi.LinkPreviewOptions(), false
                                    )
                            )
                    );
                }
        );
    }
}
