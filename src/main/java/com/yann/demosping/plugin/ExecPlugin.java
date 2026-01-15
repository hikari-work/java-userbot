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

    public ExecPlugin(ShellExecutors shellExecutors, EditMessage editMessage,
                      SendMessageUtils sendMessageUtils, OutputPaste outputPaste) {
        this.shellExecutors = shellExecutors;
        this.editMessage = editMessage;
        this.sendMessageUtils = sendMessageUtils;
        this.outputPaste = outputPaste;
    }

    @UserBotCommand(commands = "exec", description = "Execute shell command", sudoOnly = true)
    public void exec(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long replyToMessageId = message.message.id;

        String placeholderText = "<b>Input:</b>\n<pre>" + args + "</pre>\n\n" +
                "<b>Status:</b>\n<i>⏳ Executing command...</i>";

        sendMessageUtils.sendMessage(chatId, replyToMessageId, placeholderText)
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
                    if (sentMsg == null) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Eksekusi command
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
                    if (data == null) {
                        return CompletableFuture.completedFuture(null);
                    }

                    String output = (String) data[0];
                    String pasteUrl = (String) data[1];
                    TdApi.Message sentMsg = (TdApi.Message) data[2];

                    String safeResult = output.length() > 2000 ?
                            output.substring(0, 2000) + "... (truncated)" : output;

                    String finalText = "<b>Input:</b>\n<pre>" + args + "</pre>\n\n" +
                            "<b>Output:</b>\n<pre>" + safeResult + "</pre>";

                    TdApi.InputMessageText finalMessage = new TdApi.InputMessageText();
                    finalMessage.text = new TdApi.FormattedText(finalText, new TdApi.TextEntity[0]);

                    TdApi.ReplyMarkupInlineKeyboard replyMarkup;
                    if (pasteUrl != null && !pasteUrl.isEmpty()) {
                        replyMarkup = new TdApi.ReplyMarkupInlineKeyboard(
                                new TdApi.InlineKeyboardButton[][]{
                                        {
                                                new TdApi.InlineKeyboardButton(
                                                        "📄 Full Output",
                                                        new TdApi.InlineKeyboardButtonTypeUrl(pasteUrl)
                                                )
                                        }
                                }
                        );
                    } else {
                        replyMarkup = null;
                    }

                    log.info("Editing message: chatId={}, messageId={}", chatId, sentMsg.id);

                    return editMessage.editMessage(chatId, sentMsg.id, finalMessage, replyMarkup)
                            .exceptionally(editEx -> {
                                log.error("Failed to edit message, sending new one instead", editEx);

                                sendMessageUtils.sendMessage(chatId, sentMsg.id, finalText, replyMarkup)
                                        .exceptionally(sendEx -> {
                                            log.error("Failed to send fallback message", sendEx);
                                            return null;
                                        });

                                return null;
                            });
                })
                .exceptionally(ex -> {
                    log.error("Error executing command", ex);
                    
                    String errorText = "<b>Input:</b>\n<pre>" + args + "</pre>\n\n" +
                            "<b>Error:</b>\n<pre>" + ex.getMessage() + "</pre>";

                    sendMessageUtils.sendMessage(chatId, replyToMessageId, errorText)
                            .exceptionally(sendEx -> {
                                log.error("Failed to send error message", sendEx);
                                return null;
                            });

                    return null;
                });
    }
}