package com.yann.demosping.bot.inline;

import com.yann.demosping.bot.manager.InlineQuery;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HelpCommand {

    private final SimpleTelegramClient client;

    public HelpCommand(@Qualifier("botClient") SimpleTelegramClient client) {
        this.client = client;
    }

    @InlineQuery(commands = "help")
    public void help(TdApi.UpdateNewInlineQuery updateNewInlineQuery) {
        log.info("Requested Help");
        TdApi.InputInlineQueryResult[] results = new TdApi.InputInlineQueryResult[] {
                createArticle("help_1", "Search Command",
                        "Type: Search <your query>",
                        "Use Search")
        };
        client.send(
                new TdApi.AnswerInlineQuery(updateNewInlineQuery.id, false, null, results,300, "")
        );
    }
    private TdApi.InputInlineQueryResultArticle createArticle(
            String id,
            String title,
            String description,
            String content) {
        TdApi.FormattedText formattedText = new TdApi.FormattedText(
                content,
                new TdApi.TextEntity[0]
        );
        TdApi.InputMessageText inputMessage = new TdApi.InputMessageText(
                formattedText,
                new TdApi.LinkPreviewOptions(),
                true
        );
        return new TdApi.InputInlineQueryResultArticle(
                id, null, title, description, null, 0, 0, null, inputMessage
        );
    }
}
