package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.bot.inline.EvalInlineHandler;
import com.yann.demosping.service.*;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Slf4j
@Component
public class EvalPlugin {

    private final EvalService evalService;
    private final ExecResultCache resultCache;
    private final ExecMessageStore messageStore;
    private final GetInlineResults getInlineResults;
    private final SimpleTelegramClient userBotClient;

    @Value("${bot.id}")
    private long botId;

    @Value("${message.prefix}")
    private String prefix;

    public EvalPlugin(EvalService evalService,
                      ExecResultCache resultCache,
                      ExecMessageStore messageStore,
                      GetInlineResults getInlineResults,
                      @Qualifier("userBotClient") SimpleTelegramClient userBotClient) {
        this.evalService = evalService;
        this.resultCache = resultCache;
        this.messageStore = messageStore;
        this.getInlineResults = getInlineResults;
        this.userBotClient = userBotClient;
    }

    @Async
    @UserBotCommand(commands = "eval", description = "Evaluate Java/TDLib expression via JShell", sudoOnly = true)
    public void eval(TdApi.UpdateNewMessage message, String code) {
        if (code.isBlank()) return;

        long chatId = message.message.chatId;
        long cmdMsgId = message.message.id;

        // Fetch reply message if present, then evaluate
        if (message.message.replyTo instanceof TdApi.MessageReplyToMessage replyTo) {
            long replyMsgId = replyTo.messageId;
            long replyChatId = replyTo.chatId != 0 ? replyTo.chatId : chatId;
            userBotClient.send(new TdApi.GetMessage(replyChatId, replyMsgId), resp -> {
                TdApi.Message replyMsg = resp.isError() ? null : resp.get();
                doEval(message, chatId, cmdMsgId, replyMsg, code);
            });
        } else {
            doEval(message, chatId, cmdMsgId, null, code);
        }
    }

    private void doEval(TdApi.UpdateNewMessage message, long chatId, long cmdMsgId,
                        TdApi.Message reply, String code) {

        EvalContext.c = userBotClient;
        EvalContext.message = message;
        EvalContext.reply = reply;
        EvalContext.chatId = chatId;
        EvalContext.msgId = cmdMsgId;

        EvalContext.Snapshot snapshot = EvalContext.snapshot();
        String evalId = UUID.randomUUID().toString().replace("-", "");

        log.info("Evaluating code (evalId={}): {}", evalId, code);

        String output;
        try {
            output = evalService.evaluate(code);
        } catch (Exception e) {
            output = "Error: " + e.getMessage();
        }

        log.info("Eval result (evalId={}): {}", evalId, output);

        // Store result for inline bot handler, and code+snapshot for Re-run
        resultCache.put(evalId, output);
        evalService.storeEntry(evalId, code, snapshot);

        sendViaInlineBot(chatId, cmdMsgId, evalId, output);
    }

    /**
     * Called when the user edits a ,eval <code> message.
     * Deletes the old result message and sends a new one with the updated output.
     */
    public void onMessageEdit(TdApi.UpdateMessageContent update) {
        if (!(update.newContent instanceof TdApi.MessageText textContent)) return;

        String text = textContent.text.text;
        String evalPrefix = prefix + "eval ";
        if (!text.startsWith(evalPrefix)) return;

        String newCode = text.substring(evalPrefix.length()).trim();
        if (newCode.isBlank()) return;

        long cmdMsgId = update.messageId;
        long[] resultData = messageStore.getResultForCmd(cmdMsgId);
        if (resultData == null) return;

        long chatId = resultData[0];
        long oldMsgId = resultData[1];

        log.info("Eval message edited: newCode='{}' → deleting old result msgId={}", newCode, oldMsgId);

        // Restore original context snapshot so re-eval has same message/reply context
        String oldEvalId = messageStore.getCmdStr(cmdMsgId);
        EvalService.EvalEntry oldEntry = oldEvalId != null ? evalService.getEntry(oldEvalId) : null;

        userBotClient.send(new TdApi.DeleteMessages(chatId, new long[]{oldMsgId}, true), resp -> {
            if (resp.isError()) {
                log.warn("Failed to delete old eval result message {}: {}", oldMsgId, resp.getError().message);
            }
        });

        // Must run off the TDLib thread — EvalContext.send() blocks waiting for a TDLib callback,
        // which would deadlock if called from the TDLib thread itself.
        final long finalChatId = chatId;
        final EvalService.EvalEntry finalOldEntry = oldEntry;
        Mono.<Void>fromRunnable(() -> {
            EvalContext.c = userBotClient;
            if (finalOldEntry != null) {
                EvalContext.restore(finalOldEntry.snapshot());
            } else {
                EvalContext.chatId = finalChatId;
                EvalContext.msgId = cmdMsgId;
            }

            EvalContext.Snapshot snapshot = EvalContext.snapshot();
            String evalId = UUID.randomUUID().toString().replace("-", "");

            String output;
            try {
                output = evalService.evaluate(newCode);
            } catch (Exception e) {
                output = "Error: " + e.getMessage();
            }

            resultCache.put(evalId, output);
            evalService.storeEntry(evalId, newCode, snapshot);
            sendViaInlineBot(finalChatId, cmdMsgId, evalId, output);
        }).subscribeOn(Schedulers.boundedElastic())
          .subscribe(null, ex -> log.error("Eval re-run failed for code: {}", newCode, ex));
    }

    private void sendViaInlineBot(long chatId, long cmdMsgId, String evalId, String output) {
        getInlineResults.inlineQuery(chatId, botId, "eval " + evalId)
                .subscribe(results -> {
                    if (results == null || results.results == null || results.results.length == 0) {
                        log.warn("No inline results for eval query evalId={}", evalId);
                        return;
                    }

                    String resultId = extractResultId(results.results[0]);
                    if (resultId == null) return;

                    TdApi.SendInlineQueryResultMessage send = new TdApi.SendInlineQueryResultMessage();
                    send.chatId = chatId;
                    send.queryId = results.inlineQueryId;
                    send.resultId = resultId;
                    send.hideViaBot = false;
                    send.replyTo = new TdApi.InputMessageReplyToMessage(cmdMsgId, null, 0);

                    userBotClient.send(send, resp -> {
                        if (resp.isError()) {
                            log.error("SendInlineQueryResultMessage (eval) failed: {}", resp.getError().message);
                        } else {
                            TdApi.Message sentMsg = resp.get();
                            messageStore.storeResult(resultId, chatId, sentMsg.id);
                            messageStore.trackCmd(cmdMsgId, chatId, sentMsg.id, resultId, evalId);
                            log.info("Eval result sent: evalId={} msgId={} resultId={}", evalId, sentMsg.id, resultId);
                        }
                    });
                }, ex -> log.error("GetInlineQueryResults failed for eval evalId={}", evalId, ex));
    }

    private String extractResultId(TdApi.InlineQueryResult result) {
        try {
            return (String) result.getClass().getField("id").get(result);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("No id field on {}", result.getClass().getSimpleName(), e);
            return null;
        }
    }
}
