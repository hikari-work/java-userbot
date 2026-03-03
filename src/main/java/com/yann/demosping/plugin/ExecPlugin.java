package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.service.*;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecPlugin {

    private final ShellExecutors shellExecutors;
    private final EditMessage editMessage;
    private final ParseTextEntitiesUtils parseTextEntitiesUtils;


    @UserBotCommand(commands = "e", description = "Execute shell command", sudoOnly = true)
    public void exec(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;


        String escapedArgs = escapeHtml(args);
        String placeholderText = "<b>Input:</b>\n<pre>" + escapedArgs + "</pre>\n\n" +
                "<b>Status:</b>\n<i>⏳ Executing command...</i>";

        parseTextEntitiesUtils.formatText(placeholderText, new TdApi.TextParseModeHTML())
                .thenCompose(formatted -> editMessage.editMessage(chatId, message.message.id, placeholderText))
                .thenCompose(sentMessage -> {
                    long sentMessageId = sentMessage.id;

                    return shellExecutors.execute(args)
                            .thenApply(result -> truncateSafe(escapedArgs, result))
                            .thenCompose(finalHtml -> parseTextEntitiesUtils.formatText(finalHtml, new TdApi.TextParseModeHTML()))
                            .thenCompose(finalFormatted -> editMessage.editMessage(chatId, sentMessageId, new TdApi.InputMessageText(finalFormatted, new TdApi.LinkPreviewOptions(), false), null));
                })
                .exceptionally(ex -> {
                    log.error("Error in ExecPlugin: ", ex);
                    return null;
                });
    }

    private String truncateSafe(String input, String output) {
        if (output == null || output.isEmpty()) output = "No output";

        String truncatedOutput = (output.length() > 1000)
                ? output.substring(0, 1000) + "\n...(truncated)"
                : output;

        return "<b>Input:</b>\n<pre>" + input + "</pre>\n\n" +
                "<b>Output:</b>\n<pre>" + escapeHtml(truncatedOutput) + "</pre>";
    }
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}