package com.yann.demosping.manager;

import com.yann.demosping.plugin.Update;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;

@Slf4j
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

            String rawLog = new String(Files.readAllBytes(logFile.toPath()));

            log.info("Applying Patches");
            log.info(rawLog);
            String safeLog = rawLog
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");

            client.send(
                    new TdApi.ParseTextEntities("<b>Update Log:</b>\n<blockquote expandable>" + safeLog + "</blockquote>", new TdApi.TextParseModeHTML()),
                    formatted -> {
                        if (formatted.isError()) {
                            System.err.println("Gagal parsing HTML: " + formatted.getError().message);
                            client.send(new TdApi.SendMessage(chatId, 0, null, null, null, new TdApi.InputMessageText(new TdApi.FormattedText(rawLog, new TdApi.TextEntity[0]), null, false)));
                            return;
                        }
                        client.send(
                                new TdApi.SendMessage(chatId, 0, null, null, null, new TdApi.InputMessageText(formatted.get(), new TdApi.LinkPreviewOptions(), false))
                        );
                    }
            );
            logFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void editMessage(Long chatId, Long messageId, String text) {
        Update.sendMessage(chatId, messageId, text, client);
    }
}