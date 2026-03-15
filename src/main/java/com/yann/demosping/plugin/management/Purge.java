package com.yann.demosping.plugin.management;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.service.SendMessageUtils;
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
                    List<Long> toDelete = new ArrayList<>();
                    toDelete.add(commandMsgId);
                    fetchUntilFound(chatId, commandMsgId, replyMsgId, toDelete, 0);
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

                client.send(new TdApi.GetChatHistory(chatId, commandMsgId, 0, count, false), result -> {
                    if (result.isError()) {
                        log.error("Purgeme error: {}", result.getError().message);
                        return;
                    }
                    TdApi.Messages history = result.get();
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

    private static final int FETCH_BATCH = 100;
    private static final int MAX_BATCHES = 100; // max ~10.000 pesan

    /**
     * Rekursif fetch history ke belakang sampai targetMsgId ditemukan,
     * lalu hapus semua pesan yang terkumpul.
     */
    private void fetchUntilFound(long chatId, long fromMsgId, long targetMsgId,
                                  List<Long> accumulated, int batchCount) {
        if (batchCount >= MAX_BATCHES) {
            log.warn("Purge: batas batch tercapai tanpa menemukan target msgId={}", targetMsgId);
            deleteMessageIds(chatId, accumulated);
            return;
        }

        client.send(new TdApi.GetChatHistory(chatId, fromMsgId, 0, FETCH_BATCH, false), result -> {
            if (result.isError()) {
                log.error("Purge fetch error: {}", result.getError().message);
                return;
            }
            TdApi.Messages history = result.get();
            if (history.messages.length == 0) {
                log.warn("Purge: tidak ada pesan lagi, target msgId={} tidak ditemukan", targetMsgId);
                return;
            }

            long lastId = fromMsgId;
            boolean found = false;
            for (TdApi.Message msg : history.messages) {
                accumulated.add(msg.id);
                lastId = msg.id;
                if (msg.id == targetMsgId) {
                    found = true;
                    break;
                }
            }

            if (found) {
                log.info("Purge: ditemukan setelah {} batch, total {} pesan", batchCount + 1, accumulated.size());
                deleteMessageIds(chatId, accumulated);
            } else {
                final long nextFrom = lastId;
                fetchUntilFound(chatId, nextFrom, targetMsgId, accumulated, batchCount + 1);
            }
        });
    }

    /** Hapus message IDs dalam chunks 100 (batas TDLib DeleteMessages). */
    private void deleteMessageIds(long chatId, List<Long> ids) {
        for (int i = 0; i < ids.size(); i += 100) {
            long[] chunk = ids.subList(i, Math.min(i + 100, ids.size()))
                    .stream().mapToLong(Long::longValue).toArray();
            client.send(new TdApi.DeleteMessages(chatId, chunk, true), result -> {
                if (result.isError()) {
                    log.error("Purge delete error: {}", result.getError().message);
                }
            });
        }
    }

    /**
     * Helper untuk menghapus list pesan
     */
    private void deleteMessages(long chatId, List<TdApi.Message> messages) {
        if (messages.isEmpty()) return;
        deleteMessageIds(chatId, messages.stream().map(m -> m.id).toList());
    }

    private void sendError(long chatId, long replyToMsgId, String text) {
        sendMessageUtils.sendMessage(chatId, replyToMsgId, text)
                .doOnError(globalTelegramExceptionHandler::handle)
                .subscribe();
    }
}