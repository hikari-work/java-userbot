package com.yann.demosping.manager;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;

@Component
@RequiredArgsConstructor
public class OnUpdate {

    private final SimpleTelegramClient client;

    @EventListener(ApplicationReadyEvent.class)
    public void update() {
        try {
            File file = new File("context_data.txt");
            if (!file.exists()) {
                return;
            }
            String content = new String(Files.readAllBytes(file.toPath()));
            String[] parts = content.split(":");
            long chatId = Long.parseLong(parts[0]);
            long messageId = Long.parseLong(parts[1]);
            editMessage(chatId, messageId, "✅ [4/4] Bot Updated Successfully!");
            file.delete();
            File logFile = new File("log.txt");
            if (!logFile.exists()) {
                return;
            }
            String log = new String(Files.readAllBytes(logFile.toPath()));
            client.send(
                    new TdApi.ParseTextEntities("<blockquote expandable>" + log + "</blockquote>", new TdApi.TextParseModeHTML()), formatted -> {
                        if (formatted.isError()) return;
                        client.send(
                                new TdApi.SendMessage(chatId, 0, null, null, null, new TdApi.InputMessageText(formatted.get(), new TdApi.LinkPreviewOptions(), false))
                        );
                    }
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void editMessage(Long chatId, Long messageId, String text) {
        client.send(
                new TdApi.ParseTextEntities(text, new TdApi.TextParseModeHTML()), formatted -> {
                    if (formatted.isError()) return;
                    TdApi.FormattedText formattedText = formatted.get();
                    client.send(
                            new TdApi.EditMessageText(chatId, messageId, null, new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), true))
                    );
                }
        );
    }
}
