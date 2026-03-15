package com.yann.demosping.bot.inline;

import com.yann.demosping.bot.manager.InlineQuery;
import com.yann.demosping.service.ExecResultCache;
import com.yann.demosping.service.OutputPaste;
import com.yann.demosping.utils.Keyboard;
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
    private final OutputPaste outputPaste;

    public EvalInlineHandler(ExecResultCache resultCache,
                             @Qualifier("botClient") SimpleTelegramClient client,
                             OutputPaste outputPaste) {
        this.resultCache = resultCache;
        this.client = client;
        this.outputPaste = outputPaste;
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
        if (output.length() > 100) {
            String preview = output.substring(0, 100) + "...";
            outputPaste.post(output).subscribe(
                    pasteUrl -> buildMessage(queryId, resultId, evalId, preview, pasteUrl),
                    err -> {
                        log.error("paste.rs upload failed for evalId={}", evalId, err);
                        buildMessage(queryId, resultId, evalId, preview, null);
                    }
            );
        } else {
            buildMessage(queryId, resultId, evalId, output, null);
        }
    }

    private void buildMessage(long queryId, String resultId, String evalId, String displayOutput, String pasteUrl) {
        String html = "<b>Eval output:</b>\n<pre>" + escapeHtml(displayOutput) + "</pre>";

        client.send(new TdApi.ParseTextEntities(html, new TdApi.TextParseModeHTML()), parseResult -> {
            TdApi.FormattedText formattedText = parseResult.isError()
                    ? new TdApi.FormattedText(html, new TdApi.TextEntity[0])
                    : parseResult.get();

            TdApi.ReplyMarkupInlineKeyboard keyboard = pasteUrl != null
                    ? buildPasteKeyboard(resultId, pasteUrl)
                    : buildKeyboard(resultId, evalId);

            TdApi.InputMessageText inputMessage = new TdApi.InputMessageText();
            inputMessage.text = formattedText;
            inputMessage.clearDraft = true;

            TdApi.InputInlineQueryResultArticle article = new TdApi.InputInlineQueryResultArticle();
            article.id = resultId;
            article.title = "Eval result";
            article.description = displayOutput.lines().findFirst().orElse("(empty)");
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
     * Re-run: "reeval:<resultId>:<evalId>"
     */
    public static TdApi.ReplyMarkupInlineKeyboard buildKeyboard(String resultId, String evalId) {
        return Keyboard.of(new TdApi.InlineKeyboardButton[]{
                Keyboard.callbackBtn("🗑 Delete", "del:" + resultId),
                Keyboard.callbackBtn("🔄 Re-run", "reeval:" + resultId + ":" + evalId)
        });
    }

    private static TdApi.ReplyMarkupInlineKeyboard buildPasteKeyboard(String resultId, String pasteUrl) {
        return Keyboard.of(new TdApi.InlineKeyboardButton[]{
                Keyboard.callbackBtn("🗑 Delete", "del:" + resultId),
                Keyboard.urlBtn("📋 Full output", pasteUrl)
        });
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
