package com.yann.demosping.plugin;


import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.utils.CopyMessageUtils;
import com.yann.demosping.utils.MessageLinkResolver;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class Copy {

    private final MessageLinkResolver linkResolver;
    private final SimpleTelegramClient client;
    private final CopyMessageUtils copyMessageUtils;

    @UserBotCommand(commands = {"copy", "cpy"}, description = "Copy Message")
    public void copyMessage(TdApi.UpdateNewMessage update, String args) {
        long currentChatId = update.message.chatId;

        Map<String, String> params = ArgsParser.parse(args);
        String link = params.getOrDefault("link", null);

        if (link == null) {
            log.warn("Link kosong");
            return;
        }

        linkResolver.resolve(link).thenApplyAsync(complete -> copyMessageUtils.convertToInput(complete.content))
                .thenCompose(content -> sendMessageAsync(content, currentChatId))
                .exceptionally(ex -> {
                    log.info("Error Sending {}", ex.getMessage());
                    return null;
                });
    }
    public CompletableFuture<TdApi.Message> sendMessageAsync(TdApi.InputMessageContent content, long chatId) {
        TdApi.SendMessage sendMessage = new TdApi.SendMessage(
                chatId,
                0,
                null,
                null,
                null,
                content
        );
        return client.send(sendMessage);
    }
}
