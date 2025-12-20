package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.utils.MessageLinkResolver;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListMessage {

    private final SimpleTelegramClient client;
    private final MessageLinkResolver messageLinkResolver;

    @UserBotCommand(commands = {"list", "lst"}, description = "Generate links 1 by 1")
    public void generateMessageLinks(TdApi.UpdateNewMessage message, String args) {
        long currentChatId = message.message.chatId;
        long commandMsgId = message.message.id;

        Map<String, String> param = ArgsParser.parse(args);

        String link = param.get("from");
        String prefix = param.getOrDefault("prefix", "");

        int count = 1;
        try {
            if (param.get("count") != null) count = Integer.parseInt(param.get("count"));
        } catch (NumberFormatException ignored) {}

        if (count > 50) count = 50;
        final int finalCount = count;

        if (link == null || link.isEmpty()) {
            sendHtml(currentChatId, commandMsgId, "❌ Link required. Use <code>.copy -from [LINK]</code>");
            return;
        }

        sendHtml(currentChatId, commandMsgId, "⏳ <b>Fetching " + finalCount + " msgs...</b>", sentStatus -> {
            long statusMsgId = sentStatus.id;

            messageLinkResolver.resolve(link).thenAccept(startMsg -> {

                client.send(new TdApi.GetChat(startMsg.chatId), chatRes -> {

                    int offset = -finalCount + 1;
                    client.send(new TdApi.GetChatHistory(startMsg.chatId, startMsg.id, offset, finalCount, false), historyRes -> {
                        if (historyRes.isError()) {
                            editHtml(currentChatId, statusMsgId, "❌ Error: " + historyRes.getError().message);
                            return;
                        }

                        TdApi.Messages messages = historyRes.get();
                        if (messages.totalCount == 0) {
                            editHtml(currentChatId, statusMsgId, "❌ Kosong / Tidak ditemukan.");
                            return;
                        }

                        List<TdApi.Message> msgListMessage = new ArrayList<>(List.of(messages.messages));
                        Collections.reverse(msgListMessage);

                        editHtml(currentChatId, statusMsgId, "🚀 <b>Sending " + msgListMessage.size() + " messages...</b>");

                        sendNextLink(currentChatId, msgListMessage.iterator(), prefix, statusMsgId, 0);
                        deleteMessage(currentChatId, List.of(commandMsgId, statusMsgId));
                    });
                });

            }).exceptionally(ex -> {
                editHtml(currentChatId, statusMsgId, "❌ Link Error: " + ex.getMessage());
                return null;
            });
        });
    }

    private void sendNextLink(long chatId, Iterator<TdApi.Message> iterator, String prefix, long statusMsgId, int sentCount) {
        if (!iterator.hasNext()) {
            editHtml(chatId, statusMsgId, "✅ <b>Selesai!</b> Terkirim: " + sentCount);
            return;
        }

        TdApi.Message msg = iterator.next();
        String generatedLink = generatePrivateLink(msg.chatId, msg.id);

        String textToSend = (prefix.isEmpty() ? "" : prefix + " ") + generatedLink;


        client.send(new TdApi.ParseTextEntities(textToSend, new TdApi.TextParseModeHTML()), parseRes -> {
            if (parseRes.isError()) {
                sendRawLink(chatId, textToSend, iterator, prefix, statusMsgId, sentCount);
                return;
            }

            TdApi.LinkPreviewOptions options = new TdApi.LinkPreviewOptions();
            options.isDisabled = false;

            client.send(new TdApi.SendMessage(chatId, 0, null, null, null,
                    new TdApi.InputMessageText(parseRes.get(), options, false)
            ), result -> {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                sendNextLink(chatId, iterator, prefix, statusMsgId, sentCount + 1);
            });
        });
    }


    private void sendRawLink(long chatId, String text, Iterator<TdApi.Message> iter, String prefix, long statusId, int count) {
        client.send(new TdApi.SendMessage(chatId, 0, null, null, null,
                new TdApi.InputMessageText(new TdApi.FormattedText(text, new TdApi.TextEntity[0]), new TdApi.LinkPreviewOptions(), false)), res -> {
            sendNextLink(chatId, iter, prefix, statusId, count + 1);
        });
    }

    private String generatePrivateLink(long chatId, long messageId) {
        String chatIdStr = String.valueOf(chatId);
        if (chatIdStr.startsWith("-100")) {
            chatIdStr = chatIdStr.substring(4);
        }
        long publicMsgId = messageId >> 20;
        return "https://t.me/c/" + chatIdStr + "/" + publicMsgId;
    }

    private void sendHtml(long chatId, long replyToMsgId, String text) {
        sendHtml(chatId, replyToMsgId, text, null);
    }

    private void sendHtml(long chatId, long replyToMsgId, String text, Consumer<TdApi.Message> onSuccess) {
        client.send(new TdApi.ParseTextEntities(text, new TdApi.TextParseModeHTML()), res -> {
            if (res.isError()) {
                log.error("❌ Gagal Parse HTML: " + res.getError().message);
                return;
            }
            client.send(new TdApi.SendMessage(chatId, 0, new TdApi.InputMessageReplyToMessage(replyToMsgId, null, 0), null, null,
                    new TdApi.InputMessageText(res.get(), new TdApi.LinkPreviewOptions(), false)), sentRes -> {
                if (!sentRes.isError() && onSuccess != null) {
                    onSuccess.accept(sentRes.get());
                }
            });
        });
    }

    private void editHtml(long chatId, long msgId, String text) {
        client.send(new TdApi.ParseTextEntities(text, new TdApi.TextParseModeHTML()), res -> {
            if (res.isError()) return;
            client.send(new TdApi.EditMessageText(chatId, msgId, null,
                    new TdApi.InputMessageText(res.get(), new TdApi.LinkPreviewOptions(), false)));
        });
    }
    private void deleteMessage(long chatId, List<Long> messageId) {
        client.send(
                new TdApi.DeleteMessages(chatId, messageId.stream().mapToLong(l -> l).toArray(), true)
        );
    }
}