package com.yann.demosping.manager;

import com.yann.demosping.plugin.Update;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;

@Slf4j
@Component
public class StartupHandler {

    private final SimpleTelegramClient client;
    private final Update update;

    public StartupHandler(@Qualifier("userBotClient") SimpleTelegramClient client, Update update) {
        this.client = client;
        this.update = update;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            File contextFile = new File("context_data.txt");
            if (!contextFile.exists()) {
                return;
            }
            String content = new String(Files.readAllBytes(contextFile.toPath()));
            String[] parts = content.split(":");
            long chatId = Long.parseLong(parts[0]);
            long messageId = Long.parseLong(parts[1]);

            update.editMessage(chatId, messageId, "✅ [4/4] Bot Updated Successfully!");
            contextFile.delete();

            File logFile = new File("log.txt");
            if (!logFile.exists()) {
                return;
            }

            String rawLog = new String(Files.readAllBytes(logFile.toPath()));
            log.info("Applying Patches");
            log.info(rawLog);

            String safeLog = rawLog
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");

            client.send(
                    new TdApi.ParseTextEntities(
                            "<b>Update Log:</b>\n<blockquote expandable>" + safeLog + "</blockquote>",
                            new TdApi.TextParseModeHTML()
                    ),
                    formatted -> {
                        if (formatted.isError()) {
                            log.error("Failed to parse HTML: {}", formatted.getError().message);
                            client.send(new TdApi.SendMessage(
                                    chatId, 0, null, null, null,
                                    new TdApi.InputMessageText(new TdApi.FormattedText(rawLog, new TdApi.TextEntity[0]), null, false)
                            ));
                            return;
                        }
                        client.send(new TdApi.SendMessage(
                                chatId, 0, null, null, null,
                                new TdApi.InputMessageText(formatted.get(), new TdApi.LinkPreviewOptions(), false)
                        ));
                    }
            );
            logFile.delete();
        } catch (Exception e) {
            log.error("Error during startup handler", e);
        }
    }

    public void editMessage(Long chatId, Long messageId, String text) {
        update.editMessage(chatId, messageId, text);
    }
}
