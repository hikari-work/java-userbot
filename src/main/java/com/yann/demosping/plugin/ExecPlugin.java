package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.bot.inline.Exec;
import com.yann.demosping.service.*;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecPlugin {

    private final ShellExecutors shellExecutors;
    private final GetInlineResults getInlineResults;
    private final ExecResultCache execResultCache;
    private final ExecMessageStore execMessageStore;
    private final SimpleTelegramClient userBotClient;

    @Value("${bot.id}")
    private long botId;

    @Value("${message.prefix}")
    private String prefix;

    public ExecPlugin(ShellExecutors shellExecutors,
                      GetInlineResults getInlineResults,
                      ExecResultCache execResultCache,
                      ExecMessageStore execMessageStore,
                      @Qualifier("userBotClient") SimpleTelegramClient userBotClient) {
        this.shellExecutors = shellExecutors;
        this.getInlineResults = getInlineResults;
        this.execResultCache = execResultCache;
        this.execMessageStore = execMessageStore;
        this.userBotClient = userBotClient;
    }

    @UserBotCommand(commands = "e", description = "Execute shell command", sudoOnly = true)
    public void exec(TdApi.UpdateNewMessage message, String args) {
        if (args.isBlank()) return;

        long chatId = message.message.chatId;
        long cmdMsgId = message.message.id;

        shellExecutors.execute(args)
                .thenAccept(output -> sendViaInlineBot(chatId, cmdMsgId, args, output))
                .exceptionally(ex -> {
                    log.error("Shell execution failed for cmd: {}", args, ex);
                    return null;
                });
    }

    /**
     * Called when the user edits a ,e <cmd> message.
     * Deletes the old result message and sends a new one with the updated output.
     */
    public void onMessageEdit(TdApi.UpdateMessageContent update) {
        if (!(update.newContent instanceof TdApi.MessageText textContent)) return;

        String text = textContent.text.text;
        String cmdPrefix = prefix + "e ";
        if (!text.startsWith(cmdPrefix)) return;

        String newCmd = text.substring(cmdPrefix.length()).trim();
        if (newCmd.isBlank()) return;

        long cmdMsgId = update.messageId;
        long[] resultData = execMessageStore.getResultForCmd(cmdMsgId);
        if (resultData == null) return;

        long chatId = resultData[0];
        long oldMsgId = resultData[1];

        log.info("Command message edited: cmd='{}' → deleting old result msgId={}", newCmd, oldMsgId);

        // Delete the old result message immediately
        userBotClient.send(new TdApi.DeleteMessages(chatId, new long[]{oldMsgId}, true), resp -> {
            if (resp.isError()) {
                log.warn("Failed to delete old result message {}: {}", oldMsgId, resp.getError().message);
            }
        });

        // Execute new command and send fresh result
        shellExecutors.execute(newCmd)
                .thenAccept(output -> sendViaInlineBot(chatId, cmdMsgId, newCmd, output))
                .exceptionally(ex -> {
                    log.error("Shell re-execution failed for cmd: {}", newCmd, ex);
                    return null;
                });
    }

    private void sendViaInlineBot(long chatId, long cmdMsgId, String cmd, String output) {
        execResultCache.put(cmd, truncate(output));

        getInlineResults.inlineQuery(chatId, botId, "exec " + cmd)
                .thenAccept(results -> {
                    if (results == null || results.results == null || results.results.length == 0) {
                        log.warn("No inline results returned for exec query: {}", cmd);
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
                            log.error("SendInlineQueryResultMessage failed: {}", resp.getError().message);
                        } else {
                            TdApi.Message sentMsg = resp.get();
                            execMessageStore.storeResult(resultId, chatId, sentMsg.id);
                            execMessageStore.trackCmd(cmdMsgId, chatId, sentMsg.id, resultId, cmd);
                            log.info("Exec result sent: cmd='{}' msgId={} resultId={}", cmd, sentMsg.id, resultId);
                        }
                    });
                })
                .exceptionally(ex -> {
                    log.error("GetInlineQueryResults failed for cmd: {}", cmd, ex);
                    return null;
                });
    }

    private String truncate(String output) {
        if (output == null || output.isEmpty()) return "No output";
        return output.length() > 1000 ? output.substring(0, 1000) + "\n...(truncated)" : output;
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
