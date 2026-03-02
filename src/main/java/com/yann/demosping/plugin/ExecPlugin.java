package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.service.*;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ExecPlugin {

    private final ShellExecutors shellExecutors;
    private final EditMessage editMessage;
    private final SendMessageUtils sendMessageUtils;
    private final OutputPaste outputPaste;
    private final ParseTextEntitiesUtils parseTextEntitiesUtils;

    public ExecPlugin(ShellExecutors shellExecutors, EditMessage editMessage,
                      SendMessageUtils sendMessageUtils, OutputPaste outputPaste, ParseTextEntitiesUtils parseTextEntitiesUtils) {
        this.shellExecutors = shellExecutors;
        this.editMessage = editMessage;
        this.sendMessageUtils = sendMessageUtils;
        this.outputPaste = outputPaste;
        this.parseTextEntitiesUtils = parseTextEntitiesUtils;
    }

    @UserBotCommand(commands = "e", description = "Execute shell command", sudoOnly = true)
    public void exec(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;

        sendMessageUtils.deleteMessage(chatId, message.message.id);

        String escapedArgs = escapeHtml(args);

        String placeholderText = "<b>Input:</b>\n<pre>" + escapedArgs + "</pre>\n\n" +
                "<b>Status:</b>\n<i>⏳ Executing command...</i>";

        sendMessageUtils.sendMessage(chatId, 0, placeholderText)
                .thenCompose(sentMsg -> {
                    if (sentMsg == null) {
                        log.error("Failed to send placeholder message");
                        return CompletableFuture.completedFuture(null);
                    }

                    log.info("Placeholder message sent: chatId={}, messageId={}", chatId, sentMsg.id);

                    return CompletableFuture.supplyAsync(() -> sentMsg,
                            CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS));
                })
                .thenCompose(sentMsg -> {
                    if (sentMsg == null) return CompletableFuture.completedFuture(null);

                    return shellExecutors.execute(args)
                            .thenCompose(result -> {
                                log.info("Command executed, uploading to paste...");
                                return outputPaste.post(result)
                                        .map(pasteUrl -> new Object[]{result, pasteUrl.trim(), sentMsg})
                                        .defaultIfEmpty(new Object[]{result, null, sentMsg})
                                        .toFuture();
                            })
                            .exceptionally(ex -> {
                                log.error("Shell execution error", ex);
                                return new Object[]{"Error: " + ex.getMessage(), null, sentMsg};
                            });
                })
                .thenCompose(data -> {
                    if (data == null) return CompletableFuture.completedFuture(null);

                    String output = (String) data[0];
                    TdApi.Message sentMsg = (TdApi.Message) data[2];
                    log.info("Output uploaded, editing message... {}", sentMsg.id);

                    String safeResult = output.length() > 2000 ?
                            output.substring(0, 2000) + "... (truncated)" : output;

                    String escapedResult = escapeHtml(safeResult);

                    String finalText = "<b>Input:</b>\n<pre>" + escapedArgs + "</pre>\n\n" +
                            "<b>Output:</b>\n<pre>" + escapedResult + "</pre>";

                    return parseTextEntitiesUtils.formatText(finalText, new TdApi.TextParseModeHTML())
                            .thenCompose(formattedText -> CompletableFuture.supplyAsync(
                                    () -> formattedText,
                                    CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                            ))
                            .thenCompose(text -> editMessage.editMessage(
                                    chatId,
                                    sentMsg.id,
                                    new TdApi.InputMessageText(text, null, false),
                                    null
                            ).exceptionally(
                                    ex -> {
                                        log.error("Failed to edit message", ex);
                                        return null;
                                    }
                            ));
                })
                .exceptionally(ex -> {
                    log.error("Error executing command", ex);

                    String escapedError = escapeHtml(ex.getMessage());
                    String errorText = "<b>Input:</b>\n<pre>" + escapedArgs + "</pre>\n\n" +
                            "<b>Error:</b>\n<pre>" + escapedError + "</pre>";

                    sendMessageUtils.sendMessage(chatId, 0, errorText)
                            .exceptionally(sendEx -> {
                                log.error("Failed to send error message", sendEx);
                                return null;
                            });

                    return null;
                });
    }
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}