package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.yann.demosping.service.EditMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class Update {
    private final EditMessage editMessage;

    private final GlobalTelegramExceptionHandler globalTelegramExceptionHandler;

    public Update(EditMessage editMessage,
                  GlobalTelegramExceptionHandler globalTelegramExceptionHandler) {
        this.editMessage = editMessage;
        this.globalTelegramExceptionHandler = globalTelegramExceptionHandler;
    }

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
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            Files.write(Paths.get("log.txt"), builder.toString().getBytes());
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

            String currentDir = System.getProperty("user.dir");
            String scriptPath = currentDir + "/update.sh";

            String cmd = String.format("setsid bash %s %d > update_debug.log 2>&1 &", scriptPath, currentPid);

            System.out.println("Running restart command: " + cmd);

            new ProcessBuilder("/bin/bash", "-c", cmd).start();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                log.info("Interrupted Exception");
            }

            System.out.println("Bye bye...");
            System.exit(0);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void editMessage(Long chatId, Long messageId, String text) {
        sendMessage(chatId, messageId, text);
    }

    public void sendMessage(Long chatId, Long messageId, String text) {
        editMessage.editMessage(chatId, messageId, text).exceptionally(ex -> {
            globalTelegramExceptionHandler.handle(ex);
            return null;
        });
    }
}
