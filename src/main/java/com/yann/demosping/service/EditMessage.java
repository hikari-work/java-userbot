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
public class EditMessage {

    private final SimpleTelegramClient client;
    private final ParseTextEntitiesUtils parseTextEntitiesUtils;

    public EditMessage(@Qualifier("userBotClient") SimpleTelegramClient client, ParseTextEntitiesUtils parseTextEntitiesUtils) {
        this.client = client;
        this.parseTextEntitiesUtils = parseTextEntitiesUtils;
    }

    public Mono<TdApi.Message> editMessage(long chatId, long messageId, String text) {
        return parseTextEntitiesUtils.formatText(text).flatMap(formattedText ->
                Mono.create(sink ->
                        client.send(new TdApi.EditMessageText(chatId, messageId, null,
                                new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), true)), send -> {
                            if (send.isError()) {
                                TdApi.Error error = send.getError();
                                if (error.code == 429) {
                                    sink.error(new SendMessageNotCompleteException("Error Flood Wait", chatId, messageId));
                                } else if (error.code == 400 && error.message.toLowerCase().contains("message not found")) {
                                    log.warn("Message not found: chatId={}, messageId={}", chatId, messageId);
                                    sink.success(null);
                                } else {
                                    sink.error(new SendMessageNotCompleteException("Error : " + error.message, chatId, messageId, error));
                                }
                            } else {
                                sink.success(send.get());
                            }
                        })
                )
        );
    }

    public Mono<TdApi.Message> editMessage(long chatId, long messageId, TdApi.InputMessageContent content, TdApi.ReplyMarkup replyMarkup) {
        return Mono.create(sink ->
                client.send(new TdApi.EditMessageText(chatId, messageId, replyMarkup, content), send -> {
                    if (send.isError()) {
                        TdApi.Error error = send.getError();
                        if (error.code == 429) {
                            sink.error(new SendMessageNotCompleteException("Error Flood Wait", chatId, messageId));
                        } else if (error.code == 400 && error.message.toLowerCase().contains("message not found")) {
                            log.warn("Message not found: chatId={}, messageId={}", chatId, messageId);
                            sink.success(null);
                        } else {
                            sink.error(new SendMessageNotCompleteException("Error : " + error.message, chatId, messageId, error));
                        }
                    } else {
                        sink.success(send.get());
                    }
                })
        );
    }
}
