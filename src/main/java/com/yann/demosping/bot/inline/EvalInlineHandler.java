package com.yann.demosping.bot.inline;

import com.yann.demosping.bot.manager.InlineQuery;
import com.yann.demosping.service.ExecResultCache;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EvalInlineHandler {

    private final ExecResultCache resultCache;
    private final SimpleTelegramClient client;

    public EvalInlineHandler(ExecResultCache resultCache,
                             @Qualifier("botClient") SimpleTelegramClient client) {
        this.resultCache = resultCache;
        this.client = client;
    }

    @InlineQuery(commands = "eval", regex = "^eval .+")
    public void eval(TdApi.UpdateNewInlineQuery query) {
        // query format: "eval <evalId>"
        String evalId = query.query.substring(5).trim();
        String resultId = "eval_" + System.currentTimeMillis();

        String output = resultCache.getAndRemove(evalId);
        if (output == null) {
            log.warn("No cached eval result for evalId={}", evalId);
            return;
        }

        buildAndAnswer(query.id, resultId, evalId, output);
    }

    private void buildAndAnswer(long queryId, String resultId, String evalId, String output) {
        String truncated = output.length() > 2000 ? output.substring(0, 2000) + "\n...(truncated)" : output;
        String html = "<b>Eval output:</b>\n<pre>" + escapeHtml(truncated) + "</pre>";

        client.send(new TdApi.ParseTextEntities(html, new TdApi.TextParseModeHTML()), parseResult -> {
            TdApi.FormattedText formattedText = parseResult.isError()
                    ? new TdApi.FormattedText(html, new TdApi.TextEntity[0])
                    : parseResult.get();

            TdApi.ReplyMarkupInlineKeyboard keyboard = buildKeyboard(resultId, evalId);

            TdApi.InputMessageText inputMessage = new TdApi.InputMessageText();
            inputMessage.text = formattedText;
            inputMessage.clearDraft = true;

            TdApi.InputInlineQueryResultArticle article = new TdApi.InputInlineQueryResultArticle();
            article.id = resultId;
            article.title = "Eval result";
            article.description = truncated.lines().findFirst().orElse("(empty)");
            article.inputMessageContent = inputMessage;
            article.replyMarkup = keyboard;

            TdApi.AnswerInlineQuery answer = new TdApi.AnswerInlineQuery();
            answer.inlineQueryId = queryId;
            answer.results = new TdApi.InputInlineQueryResult[]{article};
            answer.isPersonal = true;
            answer.cacheTime = 0;

            client.send(answer, resp -> {
                if (resp.isError()) {
                    log.error("AnswerInlineQuery (eval) failed: {} - {}",
                            resp.getError().code, resp.getError().message);
                }
            });
        });
    }

    /**
     * Delete: "del:<resultId>"
     * Re-run: "reeval:<resultId>:<evalId>"  (evalId used to look up snapshot + code)
     */
    public static TdApi.ReplyMarkupInlineKeyboard buildKeyboard(String resultId, String evalId) {
        TdApi.InlineKeyboardButton deleteBtn = new TdApi.InlineKeyboardButton();
        deleteBtn.text = "🗑 Delete";
        TdApi.InlineKeyboardButtonTypeCallback delCb = new TdApi.InlineKeyboardButtonTypeCallback();
        delCb.data = ("del:" + resultId).getBytes();
        deleteBtn.type = delCb;

        TdApi.InlineKeyboardButton rerunBtn = new TdApi.InlineKeyboardButton();
        rerunBtn.text = "🔄 Re-run";
        TdApi.InlineKeyboardButtonTypeCallback rerunCb = new TdApi.InlineKeyboardButtonTypeCallback();
        rerunCb.data = ("reeval:" + resultId + ":" + evalId).getBytes();
        rerunBtn.type = rerunCb;

        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = new TdApi.InlineKeyboardButton[][]{{deleteBtn, rerunBtn}};
        return keyboard;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
