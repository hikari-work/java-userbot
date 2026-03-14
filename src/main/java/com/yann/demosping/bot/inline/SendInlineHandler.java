package com.yann.demosping.bot.inline;

import com.yann.demosping.bot.manager.InlineQuery;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bot-side inline handler for the userbot's ,e / ,inline command.
 *
 * Receives queries prefixed with "send " and returns an article result
 * containing the message text and optional inline keyboard.
 *
 * Button row syntax (append after a newline in the message text):
 *   [Label|https://url]                        → URL button
 *   [Label|callback:your_data]                 → Callback button
 *   [Label|switch:query text]                  → Switch inline (current chat)
 *   [Label|switch_pm:query text]               → Switch inline (choose chat)
 *
 * Multiple buttons per row: separate with space between ] and [
 *   [Button1|https://a.com] [Button2|callback:b]
 *
 * Multiple rows: one row per line
 */
@Slf4j
@Component
public class SendInlineHandler {

    private final SimpleTelegramClient client;

    public SendInlineHandler(@Qualifier("botClient") SimpleTelegramClient client) {
        this.client = client;
    }

    @InlineQuery(commands = "send")
    public void send(TdApi.UpdateNewInlineQuery query) {
        String raw = query.query.trim();

        if (raw.startsWith("send ")) {
            raw = raw.substring(5);
        } else if (raw.equals("send")) {
            return;
        }

        if (raw.isBlank()) return;

        ParsedContent content = parse(raw);

        TdApi.InputMessageText inputMessage = new TdApi.InputMessageText();
        inputMessage.text = new TdApi.FormattedText(content.text(), new TdApi.TextEntity[0]);
        inputMessage.clearDraft = true;

        TdApi.InputInlineQueryResultArticle article = new TdApi.InputInlineQueryResultArticle();
        article.id = "send_" + System.currentTimeMillis();
        article.title = truncate(content.text(), 60);
        article.description = content.text().length() > 60 ? content.text().substring(60) : "";
        article.inputMessageContent = inputMessage;

        if (content.keyboard() != null) {
            article.replyMarkup = content.keyboard();
        }

        TdApi.AnswerInlineQuery answer = new TdApi.AnswerInlineQuery();
        answer.inlineQueryId = query.id;
        answer.results = new TdApi.InputInlineQueryResult[]{article};
        answer.isPersonal = true;
        answer.cacheTime = 0;

        client.send(answer, resp -> {
            if (resp.isError()) {
                log.error("AnswerInlineQuery failed: {} - {}",
                        resp.getError().code, resp.getError().message);
            }
        });
    }

    /**
     * Parse raw text into message body + optional inline keyboard.
     *
     * Lines starting with '[' and ending with ']' are treated as button rows.
     * All other lines form the message text.
     */
    private ParsedContent parse(String raw) {
        String[] lines = raw.split("\n");
        StringBuilder textBuilder = new StringBuilder();
        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // Strip outer brackets and split on "] [" for multiple buttons in a row
                String inner = trimmed.substring(1, trimmed.length() - 1);
                String[] btnDefs = inner.split("\\] \\[");
                List<TdApi.InlineKeyboardButton> row = new ArrayList<>();

                for (String btnDef : btnDefs) {
                    String[] parts = btnDef.split("\\|", 2);
                    if (parts.length == 2) {
                        String label = parts[0].trim();
                        String value = parts[1].trim();
                        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
                        btn.text = label;
                        btn.type = resolveButtonType(value);
                        row.add(btn);
                    }
                }

                if (!row.isEmpty()) {
                    rows.add(row.toArray(new TdApi.InlineKeyboardButton[0]));
                }
            } else {
                if (!textBuilder.isEmpty()) textBuilder.append("\n");
                textBuilder.append(line);
            }
        }

        TdApi.ReplyMarkupInlineKeyboard keyboard = null;
        if (!rows.isEmpty()) {
            keyboard = new TdApi.ReplyMarkupInlineKeyboard();
            keyboard.rows = rows.toArray(new TdApi.InlineKeyboardButton[0][]);
        }

        return new ParsedContent(textBuilder.toString(), keyboard);
    }

    /**
     * Resolve button type from value string.
     *
     * callback:data      → CallbackButton
     * switch:query       → SwitchInline (current chat)
     * switch_pm:query    → SwitchInline (chosen chat / PM)
     * anything else      → URL button
     */
    private TdApi.InlineKeyboardButtonType resolveButtonType(String value) {
        if (value.startsWith("callback:")) {
            TdApi.InlineKeyboardButtonTypeCallback cb = new TdApi.InlineKeyboardButtonTypeCallback();
            cb.data = value.substring(9).getBytes();
            return cb;
        } else if (value.startsWith("switch_pm:")) {
            TdApi.InlineKeyboardButtonTypeSwitchInline sw = new TdApi.InlineKeyboardButtonTypeSwitchInline();
            sw.query = value.substring(10);
            sw.targetChat = new TdApi.TargetChatChosen();
            return sw;
        } else if (value.startsWith("switch:")) {
            TdApi.InlineKeyboardButtonTypeSwitchInline sw = new TdApi.InlineKeyboardButtonTypeSwitchInline();
            sw.query = value.substring(7);
            sw.targetChat = new TdApi.TargetChatCurrent();
            return sw;
        } else {
            TdApi.InlineKeyboardButtonTypeUrl url = new TdApi.InlineKeyboardButtonTypeUrl();
            url.url = value;
            return url;
        }
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private record ParsedContent(String text, TdApi.ReplyMarkupInlineKeyboard keyboard) {}
}
