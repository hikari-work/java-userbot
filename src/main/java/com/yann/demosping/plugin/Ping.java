package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Ping {

    @Value("${user.id}")
    private Long userId;

    private final SimpleTelegramClient client;

    @Async
    @UserBotCommand(commands = {"p", "ping"}, description = "Cek Ping Server")
    public void pingHandler(TdApi.UpdateNewMessage update, String args) {
        long chatId = update.message.chatId;
        long messageId = update.message.id;
        long startTime = System.currentTimeMillis();
        if (client.getMe().id != userId) return;

        try {
            String initialText = "<i>Pinging..</i>";
            client.send(new TdApi.ParseTextEntities(initialText, new TdApi.TextParseModeHTML()), parseResult -> {
                if (parseResult.isError()) return;

                TdApi.FormattedText initialFmt = parseResult.get();

                client.send(new TdApi.EditMessageText(chatId, messageId, null,
                        new TdApi.InputMessageText(initialFmt, new TdApi.LinkPreviewOptions(), false)
                ), sentResult -> {
                    if (!sentResult.isError()) {
                        long totalTime = (System.currentTimeMillis() - startTime) / 2;
                        String finalStats = String.format("<b>Pong!</b>\nLatency: <code>%dms</code>", totalTime);

                        client.send(
                                new TdApi.ParseTextEntities(finalStats,
                                        new TdApi.TextParseModeHTML()), finalParseResult -> {
                            if (finalParseResult.isError()) return;

                            TdApi.FormattedText finalFmt = finalParseResult.get();
                            client.send(new TdApi.EditMessageText(
                                    chatId, messageId, null,
                                    new TdApi.InputMessageText(finalFmt, new TdApi.LinkPreviewOptions(), false)
                            ));
                        });
                    }
                });
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}