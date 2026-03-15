package com.yann.demosping.bot.inline;

import com.yann.demosping.bot.manager.InlineQuery;
import com.yann.demosping.service.GcastMessageCache;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GcastInlineHandler {

    private final SimpleTelegramClient botClient;
    private final GcastMessageCache messageCache;
    private final GcastCallbackHandler gcastCallbackHandler;

    public GcastInlineHandler(
            @Qualifier("botClient") SimpleTelegramClient botClient,
            GcastMessageCache messageCache,
            @Lazy GcastCallbackHandler gcastCallbackHandler) {
        this.botClient = botClient;
        this.messageCache = messageCache;
        this.gcastCallbackHandler = gcastCallbackHandler;
    }

    @InlineQuery(commands = "gcast")
    public void handle(TdApi.UpdateNewInlineQuery query) {
        String q = query.query.trim();
        // format: "gcast <sessionId>"
        if (!q.startsWith("gcast ") || q.length() <= 6) {
            answerEmpty(query.id);
            return;
        }
        String sid = q.substring(6).trim();

        TdApi.InputMessageContent content = messageCache.get(sid);
        if (content == null) {
            log.warn("GcastInlineHandler: no cached content for sid={}", sid);
            answerEmpty(query.id);
            return;
        }

        TdApi.InputInlineQueryResultArticle result = new TdApi.InputInlineQueryResultArticle();
        result.id = "gcast_" + sid;
        result.title = "GCast";
        result.inputMessageContent = content;

        TdApi.AnswerInlineQuery answer = new TdApi.AnswerInlineQuery();
        answer.inlineQueryId = query.id;
        answer.results = new TdApi.InputInlineQueryResult[]{result};
        answer.cacheTime = 0;
        answer.isPersonal = false;

        botClient.send(answer, r -> {
            if (r.isError()) log.error("GcastInlineHandler AnswerInlineQuery failed: {}", r.getError().message);
        });
    }

    @InlineQuery(commands = "gcast_setup")
    public void handleSetup(TdApi.UpdateNewInlineQuery query) {
        String q = query.query.trim();
        // format: "gcast_setup <sid>"
        if (!q.startsWith("gcast_setup ") || q.length() <= 12) {
            answerEmpty(query.id);
            return;
        }
        String sid = q.substring(12).trim();

        TdApi.InputMessageText inputText = new TdApi.InputMessageText();
        inputText.text = new TdApi.FormattedText("📢 GCast Setup\nLangkah 1: Pilih jeda antar pesan", new TdApi.TextEntity[0]);

        TdApi.InputInlineQueryResultArticle result = new TdApi.InputInlineQueryResultArticle();
        result.id = "wizard_" + sid;
        result.title = "GCast Setup";
        result.inputMessageContent = inputText;
        result.replyMarkup = gcastCallbackHandler.buildDelayKeyboard(sid);

        TdApi.AnswerInlineQuery answer = new TdApi.AnswerInlineQuery();
        answer.inlineQueryId = query.id;
        answer.results = new TdApi.InputInlineQueryResult[]{result};
        answer.cacheTime = 0;
        answer.isPersonal = true;

        botClient.send(answer, r -> {
            if (r.isError()) log.error("GcastInlineHandler handleSetup failed: {}", r.getError().message);
        });
    }

    private void answerEmpty(long queryId) {
        TdApi.AnswerInlineQuery answer = new TdApi.AnswerInlineQuery();
        answer.inlineQueryId = queryId;
        answer.results = new TdApi.InputInlineQueryResult[0];
        answer.cacheTime = 0;
        botClient.send(answer, r -> {});
    }
}
