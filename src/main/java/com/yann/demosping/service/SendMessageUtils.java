package com.yann.demosping.service;

import com.yann.demosping.exceptions.SendMessageNotCompleteException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class SendMessageUtils {

    private final SimpleTelegramClient client;
    private final ParseTextEntitiesUtils parseTextEntitiesUtils;

    public SendMessageUtils(@Qualifier("userBotClient") SimpleTelegramClient client, ParseTextEntitiesUtils parseTextEntitiesUtils) {
        this.client = client;
        this.parseTextEntitiesUtils = parseTextEntitiesUtils;
    }

    public Mono<TdApi.Message> sendMessage(long chatId, long messageId, String text) {
        return parseTextEntitiesUtils.formatText(text).flatMap(formattedText ->
                Mono.create(sink ->
                        client.send(new TdApi.SendMessage(chatId, 0, null, null, null,
                                new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), false)), message -> {
                            if (message.isError()) {
                                TdApi.Error error = message.getError();
                                if (error.code == 429) {
                                    sink.error(new SendMessageNotCompleteException("Cannot Send Message FloodWait", chatId, messageId));
                                } else {
                                    sink.error(new SendMessageNotCompleteException("Cannot Send Message" + error.message, chatId, messageId));
                                }
                            } else {
                                sink.success(message.get());
                            }
                        })
                )
        );
    }

    public Mono<TdApi.Message> sendMessage(long chatId, String text) {
        return parseTextEntitiesUtils.formatText(text).flatMap(formattedText ->
                Mono.create(sink ->
                        client.send(new TdApi.SendMessage(chatId, 0, null, null, null,
                                new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), false)), message -> {
                            if (message.isError()) {
                                TdApi.Error error = message.getError();
                                if (error.code == 429) {
                                    sink.error(new SendMessageNotCompleteException("Cannot Send Message FloodWait", chatId, 0L));
                                } else {
                                    sink.error(new SendMessageNotCompleteException("Cannot Send Message" + error.message, chatId, 0L));
                                }
                            } else {
                                sink.success(message.get());
                            }
                        })
                )
        );
    }

    public Mono<TdApi.Message> sendMessage(long chatId, long replyToMessageId,
                                           String text, TdApi.ReplyMarkup replyMarkup) {
        TdApi.InputMessageText inputMessage = new TdApi.InputMessageText();
        inputMessage.text = new TdApi.FormattedText(text, new TdApi.TextEntity[0]);

        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = chatId;
        sendMessage.inputMessageContent = inputMessage;
        sendMessage.replyMarkup = replyMarkup;

        if (replyToMessageId != 0) {
            sendMessage.replyTo = new TdApi.InputMessageReplyToMessage(replyToMessageId, null, 0);
        }

        return Mono.create(sink ->
                client.send(sendMessage, result -> {
                    if (result.isError()) sink.error(new RuntimeException(result.getError().message));
                    else sink.success(result.get());
                })
        );
    }

    public Mono<TdApi.Message> sendMessage(long chatId, TdApi.InputMessageContent content) {
        return Mono.create(sink ->
                client.send(new TdApi.SendMessage(chatId, 0, null, null, null, content), result -> {
                    if (result.isError()) sink.error(new RuntimeException(result.getError().message));
                    else sink.success(result.get());
                })
        );
    }

    public Mono<TdApi.Message> sendMessage(long chatId, TdApi.FormattedText content) {
        return Mono.create(sink ->
                client.send(new TdApi.SendMessage(chatId, 0, null, null, null,
                        new TdApi.InputMessageText(content, new TdApi.LinkPreviewOptions(), false)), result -> {
                    if (result.isError()) sink.error(new RuntimeException(result.getError().message));
                    else sink.success(result.get());
                })
        );
    }

    public void deleteMessage(long chatId, long messageId) {
        client.send(new TdApi.DeleteMessages(chatId, new long[]{messageId}, true), result -> {
            if (result.isError()) {
                log.warn("Failed to delete message {}: {}", messageId, result.getError().message);
            }
        });
    }
}
