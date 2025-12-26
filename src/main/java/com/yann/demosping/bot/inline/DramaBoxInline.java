package com.yann.demosping.bot.inline;

import com.yann.demosping.bot.manager.InlineQuery;
import com.yann.demosping.service.dramabox.DramaBoxAPI;
import com.yann.demosping.utils.InlineHelper;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class DramaBoxInline {

    private final DramaBoxAPI dramaBoxAPI;

    private final SimpleTelegramClient client;
    private final InlineHelper inlineHelper;

    public DramaBoxInline(DramaBoxAPI dramaBoxAPI, @Qualifier("botClient") SimpleTelegramClient client, InlineHelper inlineHelper) {
        this.dramaBoxAPI = dramaBoxAPI;
        this.client = client;
        this.inlineHelper = inlineHelper;
    }

    @InlineQuery(commands = "dramabox", regex = "^dramabox .*")
    public void getDramaBox(TdApi.UpdateNewInlineQuery inlineQuery) {
        String rawQuery = inlineQuery.query;
        log.info("Raw Query is {}", rawQuery);
        String query = rawQuery.length() > "dramabox ".length()
                ? rawQuery.substring("dramabox ".length())
                : "";
        log.info("QUery is {}", query);

        if (query.isEmpty()) return;

        dramaBoxAPI.getDramaBoxSearch(query)
                .thenApply(inlineHelper::createSearch).thenAccept(results -> {

                    TdApi.InlineQueryResultsButton button = null;

                    TdApi.AnswerInlineQuery answer = new TdApi.AnswerInlineQuery(
                            inlineQuery.id,
                            false,
                            null,
                            results,
                            500,
                            ""
                    );
                    client.send(
                            answer
                    );
                }).exceptionally(ex -> {
                    log.error("Error Sending");
                    return null;
                });
    }
}

