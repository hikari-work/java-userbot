package com.yann.demosping.plugin.management;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.manager.CommandContainer;
import com.yann.demosping.manager.CommandRegistry;
import com.yann.demosping.utils.ArgsParser;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class Ban {

    private final SimpleTelegramClient client;
    private final CommandRegistry commandRegistry;

    @UserBotCommand(commands = {"kick", "ban"}, description = "Gunakan reply lalu: .kick -t 1h")
    public void ban(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;

        if (message.message.replyTo instanceof TdApi.MessageReplyToMessage reply) {
            Map<String, String> param = ArgsParser.parse(args);
            int bannedUntil = parseDuration(param.getOrDefault("t", "0"));

            getMessageInfo(reply.chatId, reply.messageId)
                    .thenComposeAsync(targetMsg -> {
                        if (targetMsg.senderId instanceof TdApi.MessageSenderUser user) {
                            return banUser(chatId, user.userId, bannedUntil);
                        }
                        return CompletableFuture.failedFuture(new RuntimeException("Bukan user (mungkin Channel/Chat)"));
                    })
                    .thenAccept(v -> sendSystemMessage(chatId, "Berhasil mengeksekusi hukuman."))
                    .exceptionally(ex -> {
                        sendSystemMessage(chatId, "Gagal: " + ex.getMessage());
                        return null;
                    });
        } else {
            showHelp(chatId);
        }
    }

    public CompletableFuture<TdApi.Message> getMessageInfo(long chatId, long messageId) {
        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
        client.send(new TdApi.GetMessage(chatId, messageId), res -> {
            if (res.isError()) future.completeExceptionally(new RuntimeException(res.getError().message));
            else future.complete(res.get());
        });
        return future;
    }

    public CompletableFuture<Void> banUser(long chatId, long userId, int bannedUntil) {
        int executeTime = (bannedUntil > 0) ? (int) (System.currentTimeMillis() / 1000) + bannedUntil : 0;

        CompletableFuture<Void> future = new CompletableFuture<>();
        client.send(new TdApi.SetChatMemberStatus(
                chatId,
                new TdApi.MessageSenderUser(userId),
                new TdApi.ChatMemberStatusBanned(executeTime)
        ), res -> {
            if (res.isError()) future.completeExceptionally(new RuntimeException(res.getError().message));
            else future.complete(null);
        });
        return future;
    }

    public int parseDuration(String input) {
        if (input == null || input.isEmpty()) return 0;
        if (input.matches("\\d+")) return Integer.parseInt(input);

        Pattern pattern = Pattern.compile("(\\d+)([dhms])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        int totalSeconds = 0;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            switch (unit) {
                case 'd' -> totalSeconds += value * 86400;
                case 'h' -> totalSeconds += value * 3600;
                case 'm' -> totalSeconds += value * 60;
                case 's' -> totalSeconds += value;
            }
        }
        return totalSeconds;
    }

    private void showHelp(long chatId) {
        CommandContainer ban = commandRegistry.getCommand("ban");
        String text = "<b>Format Salah!</b>\n" + (ban != null ? ban.command().description() : "Reply pesan target.");

        send(chatId, text);
    }

    private void send(long chatId, String text) {
        client.send(new TdApi.ParseTextEntities(text, new TdApi.TextParseModeHTML()), parseResult -> {
            TdApi.FormattedText formattedText = parseResult.isError() ?
                    new TdApi.FormattedText(text, null) : parseResult.get();

            client.send(new TdApi.SendMessage(chatId, 0, null, null, null,
                    new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), true)));
        });
    }

    private void sendSystemMessage(long chatId, String text) {
        send(chatId, text);
    }
}
