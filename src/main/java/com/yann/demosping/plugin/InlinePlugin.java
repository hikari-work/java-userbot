package com.yann.demosping.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.service.GetInlineResults;
import com.yann.demosping.service.SendMessageUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InlinePlugin {

    private final SimpleTelegramClient client;
    private final GetInlineResults getInlineResults;
    private final SendMessageUtils sendMessageUtils;

    @Value("${bot.id}")
    private long botId;

    public InlinePlugin(@Qualifier("userBotClient") SimpleTelegramClient client,
                        GetInlineResults getInlineResults,
                        SendMessageUtils sendMessageUtils) {
        this.client = client;
        this.getInlineResults = getInlineResults;
        this.sendMessageUtils = sendMessageUtils;
    }

    /*
      Send a message "via @bot" using an inline query result.
      Usage: * e Hello World → sends "Hello World" via @bot, * e 2 Hello World → picks a result at index 1 (0-based), sends it
      The text can include button rows (see SendInlineHandler for syntax): * e Hello World\n[Button|https://example.com]
     */
    /** Extract the 'id' field from any concrete InlineQueryResult subtype via reflection. */
    private String extractResultId(TdApi.InlineQueryResult result) {
        try {
            return (String) result.getClass().getField("id").get(result);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("No id field on {}", result.getClass().getSimpleName(), e);
            return null;
        }
    }

    @Async
    @UserBotCommand(commands = {"inline"}, description = "Send message via bot inline (via @bot)", sudoOnly = true)
    public void sendInline(TdApi.UpdateNewMessage message, String args) {
        if (args.isBlank()) return;

        long chatId = message.message.chatId;
        long msgId = message.message.id;

        // Optional leading integer = result index to pick (0-based). Default: 0.
        int resultIndex = 0;
        String query = args;

        String[] split = args.split(" ", 2);
        try {
            resultIndex = Integer.parseInt(split[0]);
            query = split.length > 1 ? split[1] : "";
        } catch (NumberFormatException ignored) {

        }

        if (query.isBlank()) return;

        // Prefix with "send" so the bot routes it to SendInlineHandler
        final String botQuery = "send " + query;
        final int finalIndex = resultIndex;


        getInlineResults.inlineQuery(chatId, botId, botQuery)
                .thenAccept(results -> {
                    if (results.results == null || results.results.length == 0) {
                        log.warn("No inline results for query: {}", botQuery);
                        return;
                    }

                    int idx = Math.min(finalIndex, results.results.length - 1);
                    String resultId = extractResultId(results.results[idx]);
                    if (resultId == null) {
                        log.warn("Could not extract resultId from inline result at index {}", idx);
                        return;
                    }

                    sendMessageUtils.deleteMessage(chatId, msgId);

                    TdApi.SendInlineQueryResultMessage send = new TdApi.SendInlineQueryResultMessage();
                    send.chatId = chatId;
                    send.queryId = results.inlineQueryId;
                    send.resultId = resultId;
                    send.hideViaBot = false;

                    client.send(send, resp -> {
                        if (resp.isError()) {
                            log.error("SendInlineQueryResultMessage failed: {} - {}",
                                    resp.getError().code, resp.getError().message);
                        }
                    });
                })
                .exceptionally(ex -> {
                    log.error("GetInlineQueryResults failed for query '{}'", botQuery, ex);
                    return null;
                });
    }
}
