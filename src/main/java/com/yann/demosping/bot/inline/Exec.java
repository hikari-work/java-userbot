package com.yann.demosping.bot.inline;

import com.yann.demosping.bot.manager.InlineQuery;
import com.yann.demosping.service.ExecResultCache;
import com.yann.demosping.service.OutputPaste;
import com.yann.demosping.service.ShellExecutors;
import com.yann.demosping.utils.Keyboard;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Exec {

    private final ShellExecutors shellExecutors;
    private final ExecResultCache execResultCache;
    private final SimpleTelegramClient client;
    private final OutputPaste outputPaste;

    public Exec(ShellExecutors shellExecutors,
                ExecResultCache execResultCache,
                @Qualifier("botClient") SimpleTelegramClient client,
                OutputPaste outputPaste) {
        this.shellExecutors = shellExecutors;
        this.execResultCache = execResultCache;
        this.client = client;
        this.outputPaste = outputPaste;
    }

    @InlineQuery(commands = "exec", regex = "^exec.*")
    public void exec(TdApi.UpdateNewInlineQuery query) {
        String cmd = query.query.replaceFirst("^exec\\s+", "");
        if (cmd.trim().isEmpty()) return;

        String resultId = "exec_" + System.currentTimeMillis();
        String cached = execResultCache.getAndRemove(cmd);

        if (cached != null) {
            buildAndAnswer(query.id, resultId, cmd, cached);
        } else {
            shellExecutors.execute(cmd)
                    .subscribe(
                            result -> buildAndAnswer(query.id, resultId, cmd, result),
                            ex -> log.error("Exec inline error for cmd: {}", cmd, ex)
                    );
        }
    }

    private void buildAndAnswer(long queryId, String resultId, String cmd, String output) {
        if (output == null || output.isEmpty()) output = "No output";
        final String finalOutput = output;

        if (output.length() > 100) {
            String preview = output.substring(0, 100) + "...";
            outputPaste.post(output).subscribe(
                    pasteUrl -> buildMessage(queryId, resultId, cmd, preview, pasteUrl),
                    err -> {
                        log.error("paste.rs upload failed for cmd: {}", cmd, err);
                        buildMessage(queryId, resultId, cmd, preview, null);
                    }
            );
        } else {
            buildMessage(queryId, resultId, cmd, finalOutput, null);
        }
    }

    private void buildMessage(long queryId, String resultId, String cmd, String displayOutput, String pasteUrl) {
        String html = "<b>Input:</b>\n<pre>" + escapeHtml(cmd) + "</pre>\n\n" +
                "<b>Output:</b>\n<pre>" + escapeHtml(displayOutput) + "</pre>";

        client.send(new TdApi.ParseTextEntities(html, new TdApi.TextParseModeHTML()), parseResult -> {
            TdApi.FormattedText formattedText = parseResult.isError()
                    ? new TdApi.FormattedText(html, new TdApi.TextEntity[0])
                    : parseResult.get();

            TdApi.ReplyMarkupInlineKeyboard keyboard = pasteUrl != null
                    ? buildPasteKeyboard(resultId, pasteUrl)
                    : buildKeyboard(cmd, resultId);

            TdApi.InputMessageText inputMessage = new TdApi.InputMessageText();
            inputMessage.text = formattedText;
            inputMessage.clearDraft = true;

            TdApi.InputInlineQueryResultArticle article = new TdApi.InputInlineQueryResultArticle();
            article.id = resultId;
            article.title = "Exec: " + cmd;
            article.description = "Output ready";
            article.inputMessageContent = inputMessage;
            article.replyMarkup = keyboard;

            TdApi.AnswerInlineQuery answer = new TdApi.AnswerInlineQuery();
            answer.inlineQueryId = queryId;
            answer.results = new TdApi.InputInlineQueryResult[]{article};
            answer.isPersonal = true;
            answer.cacheTime = 0;

            client.send(answer, resp -> {
                if (resp.isError()) {
                    log.error("AnswerInlineQuery failed: {} - {}",
                            resp.getError().code, resp.getError().message);
                }
            });
        });
    }

    /**
     * Delete payload: "del:<resultId>"
     * Re-run payload: "exec:<resultId>:<cmd>"
     */
    public static TdApi.ReplyMarkupInlineKeyboard buildKeyboard(String cmd, String resultId) {
        return Keyboard.of(new TdApi.InlineKeyboardButton[]{
                Keyboard.callbackBtn("🗑 Delete", "del:" + resultId),
                Keyboard.callbackBtn("🔄 Re-run", "exec:" + resultId + ":" + cmd)
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
