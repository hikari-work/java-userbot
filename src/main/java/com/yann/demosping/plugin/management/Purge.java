package com.yann.demosping.plugin.management;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.utils.SendMessageUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class Purge {

    private final SimpleTelegramClient client;
    private final SendMessageUtils sendMessageUtils;

    public Purge(@Qualifier("userBotClient") SimpleTelegramClient client,
                 SendMessageUtils sendMessageUtils,
                 GlobalTelegramExceptionHandler globalTelegramExceptionHandler) {
        this.client = client;
        this.sendMessageUtils = sendMessageUtils;
        this.globalTelegramExceptionHandler = globalTelegramExceptionHandler;
    }

    private final GlobalTelegramExceptionHandler globalTelegramExceptionHandler;

    @UserBotCommand(
            commands = {"purge", "purgeme", "del"},
            description = "Purge messages. .purge (reply), .purgeme [count]",
            sudoOnly = true
    )
    public void purgeCommands(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long commandMsgId = message.message.id;

        String command = "";
        if (message.message.content instanceof TdApi.MessageText text) {

            command = text.text.text.split(" ", 2)[0].replace(".", "");
        }

        if (command.isBlank()) return;

        switch (command) {
            case "purge" -> {
                if (message.message.replyTo instanceof TdApi.MessageReplyToMessage replyTo) {
                    long replyMsgId = replyTo.messageId;
                    client.send(new TdApi.GetChatHistory(chatId, commandMsgId, 0, 1000, false))
                            .thenAccept(history -> {
                                if (history.totalCount == 0) return;
                                log.info("Total Count Message is {}", history.totalCount);

                                List<TdApi.Message> messagesToDelete = new ArrayList<>();
                                messagesToDelete.add(message.message);

                                boolean foundReply = false;

                                for (TdApi.Message msg : history.messages) {
                                    messagesToDelete.add(msg);
                                    if (msg.id == replyMsgId) {
                                        foundReply = true;
                                        break;
                                    }
                                }

                                if (foundReply) {
                                    deleteMessages(chatId, messagesToDelete);
                                } else {
                                    sendError(chatId, commandMsgId, "⚠️ Pesan yang di-reply terlalu lama (lebih dari 1000 pesan yang lalu).");
                                }
                            })
                            .exceptionally(ex -> {
                                log.error("Purge error", ex);
                                return null;
                            });
                } else {
                    sendError(chatId, commandMsgId, "❌ Reply pesan yang ingin di-purge.");
                }
            }
            case "purgeme" -> {
                int count = 10;
                try {
                    if (args != null && !args.isBlank()) {
                        count = Integer.parseInt(args.trim());
                    }
                } catch (NumberFormatException ignored) {
                }

                if (count > 1000) count = 1000;

                client.send(new TdApi.GetChatHistory(chatId, commandMsgId, 0, count, false))
                        .thenAccept(history -> {
                            List<TdApi.Message> myMessages = new ArrayList<>();
                            myMessages.add(message.message);

                            for (TdApi.Message msg : history.messages) {
                                if (msg.isOutgoing) {
                                    myMessages.add(msg);
                                }
                            }
                            log.info("My Message Count is {}", myMessages.size());

                            if (!myMessages.isEmpty()) {
                                deleteMessages(chatId, myMessages);
                            }
                        });
            }

            case "del" -> {
                if (message.message.replyTo instanceof TdApi.MessageReplyToMessage replyTo) {
                    deleteMessages(chatId, List.of(message.message));
                    long[] targetId = {replyTo.messageId};
                    client.send(new TdApi.DeleteMessages(chatId, targetId, true));
                }
            }
        }
    }

    /**
     * Helper untuk menghapus list pesan
     */
    private void deleteMessages(long chatId, List<TdApi.Message> messages) {
        if (messages.isEmpty()) return;

        long[] messageIds = messages.stream()
                .mapToLong(msg -> msg.id)
                .toArray();

        client.send(new TdApi.DeleteMessages(chatId, messageIds, true), result -> {
            if (result.isError()) {
                log.error("Gagal delete: {}", result.getError().message);
            }
        });
    }

    private void sendError(long chatId, long replyToMsgId, String text) {
        sendMessageUtils.sendMessage(chatId, replyToMsgId, text).exceptionally(ex -> {
            globalTelegramExceptionHandler.handle(ex);
            return null;
        });
    }
}