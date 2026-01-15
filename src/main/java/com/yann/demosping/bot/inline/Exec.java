package com.yann.demosping.bot.inline;

import com.yann.demosping.bot.manager.InlineQuery;
import com.yann.demosping.service.OutputPaste;
import com.yann.demosping.service.ShellExecutors;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Exec {

    private final ShellExecutors shellExecutors;
    private final OutputPaste outputPaste;
    private final SimpleTelegramClient client;

    public Exec(ShellExecutors shellExecutors,
                OutputPaste outputPaste,
                @Qualifier("botClient") SimpleTelegramClient client) {
        this.shellExecutors = shellExecutors;
        this.outputPaste = outputPaste;
        this.client = client;
    }

    @InlineQuery(regex = "^exec.*")
    public void exec(TdApi.UpdateNewInlineQuery query) {
        String cmd = query.query.replaceFirst("^exec\\s+", "");
        log.info("Exec: {}", cmd);

        if (cmd.trim().isEmpty()) {
            return;
        }

        // LANGSUNG jawab dengan placeholder
        sendPlaceholderAnswer(query.id, cmd);

        // Eksekusi command di background
        shellExecutors.execute(cmd).thenAccept(result -> {
            // Ini akan dihandle di ExecPlugin untuk update pesan
            log.info("Command executed: {}", cmd);
        }).exceptionally(ex -> {
            log.error("Exec error", ex);
            return null;
        });
    }

    private void sendPlaceholderAnswer(long queryId, String cmd) {
        String textContent = "<b>Input:</b>\n" +
                "<pre>" + cmd + "</pre>\n\n" +
                "<b>Status:</b>\n" +
                "<i>⏳ Executing command...</i>";

        TdApi.InputMessageText inputMessage = new TdApi.InputMessageText();
        inputMessage.text = new TdApi.FormattedText(textContent, new TdApi.TextEntity[0]);
        inputMessage.clearDraft = true;

        TdApi.InputInlineQueryResultArticle article = new TdApi.InputInlineQueryResultArticle();
        article.id = String.valueOf(System.currentTimeMillis());
        article.title = "Exec: " + cmd;
        article.description = "Tap to execute";
        article.inputMessageContent = inputMessage;

        TdApi.InputInlineQueryResult[] results = new TdApi.InputInlineQueryResult[]{ article };

        TdApi.AnswerInlineQuery answer = new TdApi.AnswerInlineQuery();
        answer.inlineQueryId = queryId;
        answer.results = results;
        answer.isPersonal = true;
        answer.cacheTime = 0;

        client.send(answer, response -> {
            if (response.isError()) {
                log.error("Gagal menjawab inline query: {} - {}",
                        response.getError().code, response.getError().message);
            } else {
                log.info("Inline query answered with placeholder");
            }
        });
    }
}