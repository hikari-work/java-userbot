package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.utils.ArgsParser;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class Log {

    private final SimpleTelegramClient client;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final String LOG_FILE_NAME = "bot_runtime.log";

    @UserBotCommand(commands = {"log"}, description = "Read, Tail, or Get Log File", sudoOnly = true)
    public void logProcess(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;

        if (args == null || args.isBlank()) {
            editMessage(chatId, messageId, "📂 Uploading log file...");
            sendLogFiles(chatId, messageId);
            return;
        }

        Map<String, String> parse = ArgsParser.parse(args);

        String head = parse.get("h");
        if (head != null) {
            int lines = parseIntSafe(head);
            String output = getLogDetails("head", lines);
            sendOutputMessage(chatId, messageId, "Head (First " + lines + " lines)", output);
            return;
        }

        String tail = parse.get("t");
        if (tail != null) {
            int lines = parseIntSafe(tail);
            String output = getLogDetails("tail", lines);
            sendOutputMessage(chatId, messageId, "Tail (Last " + lines + " lines)", output);
            return;
        }

        if (parse.containsKey("l")) {
            startLiveLog(chatId, messageId);
        }
    }

    public void sendLogFiles(long chatId, long messageId) {
        File file = new File(LOG_FILE_NAME);
        if (!file.exists()) {
            editMessage(chatId, messageId, "❌ Log file not found: " + LOG_FILE_NAME);
            return;
        }

        String captionText = "📂 <b>Runtime Log</b>";
        client.send(new TdApi.ParseTextEntities(captionText, new TdApi.TextParseModeHTML()), parseResult -> {
            if (parseResult.isError()) return;
            TdApi.FormattedText caption = parseResult.get();

            TdApi.InputFile inputFile = new TdApi.InputFileLocal(LOG_FILE_NAME);

            client.send(
                    new TdApi.SendMessage(chatId, 0, null, null, null, new TdApi.InputMessageDocument(inputFile, null, false, caption)),
                    result -> {
                        if (!result.isError()) {
                            client.send(new TdApi.DeleteMessages(chatId, new long[]{messageId}, true));
                        } else {
                            editMessage(chatId, messageId, "❌ Failed to send file: " + result.getError().message);
                        }
                    }
            );
        });
    }

    public String getLogDetails(String command, int lines) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "-n", String.valueOf(lines), LOG_FILE_NAME);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.lines().collect(Collectors.joining("\n"));
                if (result.isBlank()) return "Log file is empty or not found.";
                if (result.length() > 3500) return result.substring(result.length() - 3500) + "\n...(truncated)";
                return result;
            }
        } catch (Exception e) {
            return "Error reading log: " + e.getMessage();
        }
    }

    private void startLiveLog(long chatId, long messageId) {
        String tail = getLogDetails("tail", 10);
        editMessage(chatId, messageId, "🖥️ <b>Starting Live Log...</b>\n" + "<blockquote expandable>" + tail + "</blockquote>");

        LinkedList<String> logBuffer = new LinkedList<>();
        int MAX_BUFFER_LINES = 15;
        long TIMEOUT_MS = 60_000;

        scheduler.execute(() -> {
            Process process = null;
            try {
                process = new ProcessBuilder("tail", "-f", LOG_FILE_NAME).start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                long startTime = System.currentTimeMillis();
                Process finalProcess = process;

                var updateTask = scheduler.scheduleAtFixedRate(() -> {
                    if (System.currentTimeMillis() - startTime > TIMEOUT_MS) return;

                    if (!logBuffer.isEmpty()) {
                        String logs;
                        synchronized (logBuffer) {
                            logs = String.join("\n", logBuffer);
                        }

                        String safeLogs = logs.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                        String fullText = "🖥️ <b>Live Log (tail -f):</b>\n<blockquote expandable>" + safeLogs + "</blockquote>";

                        client.send(new TdApi.ParseTextEntities(fullText, new TdApi.TextParseModeHTML()), parseResult -> {
                            if (parseResult.isError()) {
                                System.err.println("Parse Error: " + parseResult.getError());
                                return;
                            }
                            TdApi.FormattedText formattedText = parseResult.get();

                            client.send(new TdApi.EditMessageText(
                                    chatId, messageId, null,
                                    new TdApi.InputMessageText(formattedText, null, true)
                            ));
                        });
                    }
                }, 2, 3, TimeUnit.SECONDS);

                String line;
                while ((System.currentTimeMillis() - startTime < TIMEOUT_MS) && (line = reader.readLine()) != null) {
                    synchronized (logBuffer) {
                        logBuffer.add(line);
                        if (logBuffer.size() > MAX_BUFFER_LINES) {
                            logBuffer.removeFirst();
                        }
                    }
                }

                updateTask.cancel(false);
                if (finalProcess.isAlive()) finalProcess.destroy();

                editMessage(chatId, messageId, "✅ <b>Live Log Stopped.</b>");

            } catch (Exception e) {
                if (process != null && process.isAlive()) process.destroy();
                editMessage(chatId, messageId, "❌ Live Log Error: " + e.getMessage());
            }
        });
    }

    private void sendOutputMessage(long chatId, long messageId, String title, String content) {
        String safeContent = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String fullText = "📄 <b>" + title + "</b>\n<blockquote expandable>" + safeContent + "</blockquote>";

        editMessage(chatId, messageId, fullText);
    }

    private void editMessage(long chatId, long messageId, String rawHtmlText) {
        client.send(new TdApi.ParseTextEntities(rawHtmlText, new TdApi.TextParseModeHTML()), parseResult -> {
            if (parseResult.isError()) {
                System.err.println("Gagal parse HTML: " + parseResult.getError().message);
                return;
            }

            TdApi.FormattedText formattedText = parseResult.get();

            client.send(new TdApi.EditMessageText(
                    chatId, messageId, null,
                    new TdApi.InputMessageText(formattedText, null, true)
            ));
        });
    }

    private int parseIntSafe(String val) {
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 20; }
    }
}