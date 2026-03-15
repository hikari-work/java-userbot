package com.yann.demosping.plugin.management;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.manager.CommandContainer;
import com.yann.demosping.manager.CommandRegistry;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.service.SendMessageUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Ban {

    private final SimpleTelegramClient client;
    private final CommandRegistry commandRegistry;
    private final SendMessageUtils sendMessageUtils;
    private final GlobalTelegramExceptionHandler globalTelegramExceptionHandler;

    public Ban(@Qualifier("userBotClient") SimpleTelegramClient client,
               CommandRegistry commandRegistry,
               SendMessageUtils sendMessageUtils,
               GlobalTelegramExceptionHandler globalTelegramExceptionHandler) {
        this.client = client;
        this.commandRegistry = commandRegistry;
        this.sendMessageUtils = sendMessageUtils;
        this.globalTelegramExceptionHandler = globalTelegramExceptionHandler;
    }

    @UserBotCommand(commands = {"kick", "ban"}, description = "Gunakan reply lalu: .kick -t 1h", sudoOnly = true)
    public void ban(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;

        if (message.message.replyTo instanceof TdApi.MessageReplyToMessage reply) {
            Map<String, String> param = ArgsParser.parse(args);
            int bannedUntil = parseDuration(param.getOrDefault("t", "0"));

            getMessageInfo(reply.chatId, reply.messageId)
                    .flatMap(targetMsg -> {
                        if (targetMsg.senderId instanceof TdApi.MessageSenderUser user) {
                            return banUser(chatId, user.userId, bannedUntil);
                        }
                        return Mono.error(new RuntimeException("Bukan user (mungkin Channel/Chat)"));
                    })
                    .subscribe(
                            v -> sendSystemMessage(chatId, "Berhasil mengeksekusi hukuman."),
                            ex -> sendSystemMessage(chatId, "Gagal: " + ex.getMessage())
                    );
        } else {
            showHelp(chatId);
        }
    }

    public Mono<TdApi.Message> getMessageInfo(long chatId, long messageId) {
        return Mono.create(sink ->
                client.send(new TdApi.GetMessage(chatId, messageId), res -> {
                    if (res.isError()) sink.error(new RuntimeException(res.getError().message));
                    else sink.success(res.get());
                })
        );
    }

    public Mono<Void> banUser(long chatId, long userId, int bannedUntil) {
        int executeTime = (bannedUntil > 0) ? (int) (System.currentTimeMillis() / 1000) + bannedUntil : 0;
        return Mono.create(sink ->
                client.send(new TdApi.SetChatMemberStatus(
                        chatId,
                        new TdApi.MessageSenderUser(userId),
                        new TdApi.ChatMemberStatusBanned(executeTime)
                ), res -> {
                    if (res.isError()) sink.error(new RuntimeException(res.getError().message));
                    else sink.success();
                })
        );
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
        sendMessageUtils.sendMessage(chatId, text)
                .doOnError(globalTelegramExceptionHandler::handle)
                .subscribe();
    }

    private void sendSystemMessage(long chatId, String text) {
        send(chatId, text);
    }
}
