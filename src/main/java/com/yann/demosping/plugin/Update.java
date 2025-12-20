package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class Update {

    private final SimpleTelegramClient client;

    @UserBotCommand(commands = {"update"}, description = "", sudoOnly = true)
    public void update(TdApi.UpdateNewMessage message, String args) {
        log.info("Received");
        long chatId = message.message.chatId;
        long messageId = message.message.id;
        editMessage(chatId, messageId, "⏳ [1/4] Processing Git Pull...");
        try {
            Process pPull = new ProcessBuilder("git", "pull").start();
            pPull.waitFor();
            String pullMessage = new BufferedReader(new InputStreamReader(pPull.getInputStream())).readLine();
            if (pullMessage.equals("Already up to date.")) {
                editMessage(chatId, messageId, pullMessage);
                return;
            }
            Process pLog = new ProcessBuilder("git", "log", "-1", "--pretty=%B").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(pLog.getInputStream()));
            String lastCommit = reader.readLine();

            Files.write(Paths.get("log.txt"), lastCommit.getBytes());
            editMessage(chatId, messageId, "\uD83D\uDD28 [2/4] Maven Build (Clean Package)...");
            Process pBuild = new ProcessBuilder("mvn", "clean", "package", "-DskipTests").start();
            boolean buildSuccess = pBuild.waitFor(2, TimeUnit.MINUTES);
            if (pBuild.exitValue() != 0) {
                throw new Exception("Maven build Error");
            }
            editMessage(chatId, messageId, "zzz [3/4] Shutdown & Restarting...");
            String contextData = chatId + ":" + messageId;
            Files.write(Paths.get("context_data.txt"), contextData.getBytes());

            long currentPid = ProcessHandle.current().pid();
            new ProcessBuilder("nohup", "./update.sh", String.valueOf(currentPid)).start();
            Thread.sleep(2_000);
            System.exit(0);


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
