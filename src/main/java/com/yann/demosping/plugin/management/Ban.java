package com.yann.demosping.plugin.management;

import com.yann.demosping.annotations.UserBotCommand;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class Ban {

    private final SimpleTelegramClient client;

    @UserBotCommand(
            commands = {"kick"},
            description = ""
    )
    public void ban(TdApi.UpdateNewMessage message, String args) {
        if (message.message.replyTo instanceof TdApi.MessageReplyToMessage messageReplyToMessage) {

        } else {

        }
    }
    public CompletableFuture<TdApi.Message> getMessageInfo(TdApi.MessageReplyToMessage replyToMessage) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        client.send(
                new TdApi.GetMessage(replyToMessage.chatId, replyToMessage.messageId), message -> {
                    if (message.isError()) messageFuture.completeExceptionally(new RuntimeException("Error"));
                    messageFuture.complete(message.get());
                }
        );
        return messageFuture;
    }
    public CompletableFuture<Void> banUser(Long chatId, Long userId, Integer bannedUntil) {
        TdApi.ChatMemberStatusBanned banned = new TdApi.ChatMemberStatusBanned(bannedUntil == null ? 0 : bannedUntil);
        return CompletableFuture.runAsync(() -> {
            client.send(
                    new TdApi.SetChatMemberStatus(chatId, new TdApi.MessageSenderUser(userId), banned)
            );
        });

    }
    public int parseDuration(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }
        Pattern pattern = Pattern.compile("(\\d+)(dhmsDHMS)");
        Matcher matcher = pattern.matcher(input);
        int totalSeconds = 0;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            char unit = matcher.group(2).toLowerCase().charAt(0);
            switch (unit) {
                case 'd' :
                    totalSeconds += value * 86400;
                    break;
                case 'h':
                    totalSeconds += value * 3600;
                    break;
                case 'm' :
                    totalSeconds += value * 60;
                    break;
                case 's' :
                    totalSeconds += value;
                    break;
            }
        }
        return totalSeconds;
    }

}
