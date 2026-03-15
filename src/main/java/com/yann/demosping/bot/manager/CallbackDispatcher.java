package com.yann.demosping.bot.manager;

import com.yann.demosping.bot.inline.Exec;
import com.yann.demosping.bot.inline.EvalInlineHandler;
import com.yann.demosping.bot.inline.GcastCallbackHandler;
import com.yann.demosping.service.EvalContext;
import com.yann.demosping.service.EvalService;
import com.yann.demosping.service.ExecMessageStore;
import com.yann.demosping.service.ShellExecutors;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class CallbackDispatcher {

    private final ApplicationContext ctx;
    private final SimpleTelegramClient userBotClient;
    private final ShellExecutors shellExecutors;
    private final EvalService evalService;
    private final ExecMessageStore execMessageStore;
    private final GcastCallbackHandler gcastCallbackHandler;

    public CallbackDispatcher(ApplicationContext ctx,
                              @Qualifier("userBotClient") SimpleTelegramClient userBotClient,
                              ShellExecutors shellExecutors,
                              EvalService evalService,
                              ExecMessageStore execMessageStore,
                              @Lazy GcastCallbackHandler gcastCallbackHandler) {
        this.ctx = ctx;
        this.userBotClient = userBotClient;
        this.shellExecutors = shellExecutors;
        this.evalService = evalService;
        this.execMessageStore = execMessageStore;
        this.gcastCallbackHandler = gcastCallbackHandler;
    }

    private SimpleTelegramClient botClient() {
        return ctx.getBean("botClient", SimpleTelegramClient.class);
    }

    /** Called for buttons on regular (non-via-bot) messages — userbot side. */
    public void dispatch(TdApi.UpdateNewCallbackQuery callbackQuery) {
        if (!(callbackQuery.payload instanceof TdApi.CallbackQueryPayloadData data)) return;
        String payload = new String(data.data);
        log.info("Received callback query: payload='{}' chatId={} messageId={}",
                payload, callbackQuery.chatId, callbackQuery.messageId);

        if (payload.startsWith("gc:")) {
            gcastCallbackHandler.handle(callbackQuery, payload);
        }
    }

    /** Called for buttons on bot-sent regular messages — bot side (e.g., gcast panel). */
    public void dispatchBotCallback(TdApi.UpdateNewCallbackQuery callbackQuery) {
        if (!(callbackQuery.payload instanceof TdApi.CallbackQueryPayloadData data)) return;
        String payload = new String(data.data);
        log.info("Received bot callback: payload='{}' chatId={} messageId={}",
                payload, callbackQuery.chatId, callbackQuery.messageId);

        if (payload.startsWith("gc:")) {
            gcastCallbackHandler.handle(callbackQuery, payload);
        }
    }

    /** Called for buttons on via-bot inline messages — bot side. */
    public void dispatchInline(TdApi.UpdateNewInlineCallbackQuery callbackQuery) {
        if (!(callbackQuery.payload instanceof TdApi.CallbackQueryPayloadData data)) return;

        String payload = new String(data.data);
        String inlineMessageId = callbackQuery.inlineMessageId;
        log.info("Received inline callback: payload='{}' inlineMessageId='{}'", payload, inlineMessageId);

        if (payload.startsWith("del:")) {
            String resultId = payload.substring(4);
            handleDelete(callbackQuery.id, resultId);
        } else if (payload.startsWith("exec:")) {
            String rest = payload.substring(5);
            int colonIdx = rest.indexOf(':');
            if (colonIdx < 0) return;
            String resultId = rest.substring(0, colonIdx);
            String cmd = rest.substring(colonIdx + 1);
            handleRerun(callbackQuery.id, inlineMessageId, resultId, cmd);
        } else if (payload.startsWith("reeval:")) {
            String rest = payload.substring(7);
            int colonIdx = rest.indexOf(':');
            if (colonIdx < 0) return;
            String resultId = rest.substring(0, colonIdx);
            String evalId = rest.substring(colonIdx + 1);
            handleReeval(callbackQuery.id, inlineMessageId, resultId, evalId);
        } else if (payload.startsWith("gc:")) {
            gcastCallbackHandler.handleInline(callbackQuery, payload);
        }
    }

    private void handleDelete(long callbackQueryId, String resultId) {
        botClient().send(new TdApi.AnswerCallbackQuery(callbackQueryId, "", false, "", 0), resp -> {});

        long[] msgData = execMessageStore.getByResultId(resultId);
        if (msgData == null) {
            log.warn("No message data found for resultId: {}", resultId);
            return;
        }

        long chatId = msgData[0];
        long msgId = msgData[1];

        userBotClient.send(new TdApi.DeleteMessages(chatId, new long[]{msgId}, true), resp -> {
            if (resp.isError()) {
                log.error("Failed to delete exec result message: {}", resp.getError().message);
            } else {
                execMessageStore.removeByResultId(resultId);
            }
        });
    }

    private void handleRerun(long callbackQueryId, String inlineMessageId, String resultId, String cmd) {
        botClient().send(new TdApi.AnswerCallbackQuery(callbackQueryId, "Re-running...", false, "", 0), resp -> {});

        shellExecutors.execute(cmd).subscribe(output -> {
            if (output == null || output.isEmpty()) output = "No output";
            String truncated = output.length() > 1000
                    ? output.substring(0, 1000) + "\n...(truncated)"
                    : output;

            String html = "<b>Input:</b>\n<pre>" + escapeHtml(cmd) + "</pre>\n\n" +
                    "<b>Output:</b>\n<pre>" + escapeHtml(truncated) + "</pre>";

            TdApi.ReplyMarkupInlineKeyboard keyboard = Exec.buildKeyboard(cmd, resultId);

            botClient().send(new TdApi.ParseTextEntities(html, new TdApi.TextParseModeHTML()), parseResult -> {
                TdApi.FormattedText formattedText = parseResult.isError()
                        ? new TdApi.FormattedText(html, new TdApi.TextEntity[0])
                        : parseResult.get();

                TdApi.InputMessageText content = new TdApi.InputMessageText();
                content.text = formattedText;

                botClient().send(new TdApi.EditInlineMessageText(inlineMessageId, keyboard, content), resp -> {
                    if (resp.isError() && !resp.getError().message.equals("MESSAGE_NOT_MODIFIED")) {
                        log.error("EditInlineMessageText failed: {}", resp.getError().message);
                    }
                });
            });
        }, ex -> log.error("Re-run failed for cmd: {}", cmd, ex));
    }

    private void handleReeval(long callbackQueryId, String inlineMessageId, String resultId, String evalId) {
        botClient().send(new TdApi.AnswerCallbackQuery(callbackQueryId, "Re-evaluating...", false, "", 0), resp -> {});

        EvalService.EvalEntry entry = evalService.getEntry(evalId);
        if (entry == null) {
            log.warn("No eval entry found for evalId={}", evalId);
            return;
        }

        // Must run off the TDLib thread — EvalContext.send() blocks waiting for a TDLib callback
        Mono.<Void>fromRunnable(() -> {
            EvalContext.c = userBotClient;
            EvalContext.restore(entry.snapshot());
            String output;
            try {
                output = evalService.evaluate(entry.code());
            } catch (Exception ex) {
                output = "Error: " + ex.getMessage();
            }

            String truncated = output.length() > 2000 ? output.substring(0, 2000) + "\n...(truncated)" : output;
            String html = "<b>Eval output:</b>\n<pre>" + escapeHtml(truncated) + "</pre>";

            TdApi.ReplyMarkupInlineKeyboard keyboard = EvalInlineHandler.buildKeyboard(resultId, evalId);

            botClient().send(new TdApi.ParseTextEntities(html, new TdApi.TextParseModeHTML()), parseResult -> {
                TdApi.FormattedText ft = parseResult.isError()
                        ? new TdApi.FormattedText(html, new TdApi.TextEntity[0])
                        : parseResult.get();

                TdApi.InputMessageText content = new TdApi.InputMessageText();
                content.text = ft;

                botClient().send(new TdApi.EditInlineMessageText(inlineMessageId, keyboard, content), resp -> {
                    if (resp.isError() && !resp.getError().message.equals("MESSAGE_NOT_MODIFIED")) {
                        log.error("EditInlineMessageText (reeval) failed: {}", resp.getError().message);
                    }
                });
            });
        }).subscribeOn(Schedulers.boundedElastic())
          .subscribe(null, ex -> log.error("Re-eval failed for evalId={}", evalId, ex));
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
