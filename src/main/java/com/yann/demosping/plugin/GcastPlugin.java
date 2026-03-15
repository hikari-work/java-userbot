package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.dto.GcastConfig;
import com.yann.demosping.service.EditMessage;
import com.yann.demosping.service.GcastService;
import com.yann.demosping.service.GcastStateService;
import it.tdlight.jni.TdApi;
import it.tdlight.client.SimpleTelegramClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.UUID;

@Slf4j
@Component
public class GcastPlugin {

    private final SimpleTelegramClient userBotClient;
    private final GcastStateService stateService;
    private final GcastService gcastService;
    private final EditMessage editMessage;

    @Value("${bot.id}")
    private long botId;

    public GcastPlugin(
            @Qualifier("userBotClient") SimpleTelegramClient userBotClient,
            GcastStateService stateService,
            GcastService gcastService,
            EditMessage editMessage) {
        this.userBotClient = userBotClient;
        this.stateService = stateService;
        this.gcastService = gcastService;
        this.editMessage = editMessage;
    }

    @UserBotCommand(commands = {"gcast"}, description = "Mulai group broadcast (reply ke pesan)", sudoOnly = true)
    public void handleGcast(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long msgId = message.message.id;

        if (message.message.replyTo == null) {
            editMessage.editMessage(chatId, msgId, "❌ Reply ke pesan yang ingin dibroadcast!").subscribe();
            return;
        }

        if (!(message.message.replyTo instanceof TdApi.MessageReplyToMessage replyTo)) {
            editMessage.editMessage(chatId, msgId, "❌ Reply ke pesan yang ingin dibroadcast!").subscribe();
            return;
        }

        long sourceChatId = replyTo.chatId != 0 ? replyTo.chatId : chatId;
        long sourceMessageId = replyTo.messageId;

        String sid = UUID.randomUUID().toString().substring(0, 8);

        GcastConfig config = new GcastConfig();
        config.sourceChatId = sourceChatId;
        config.sourceMessageId = sourceMessageId;
        config.controlChatId = chatId;
        config.step = "DELAY";

        editMessage.editMessage(chatId, msgId, "📢 Mempersiapkan GCast...").subscribe();

        TdApi.GetInlineQueryResults getResults = new TdApi.GetInlineQueryResults();
        getResults.botUserId = botId;
        getResults.chatId = chatId;
        getResults.query = "gcast_setup " + sid;
        getResults.offset = "";

        userBotClient.send(getResults, queryResult -> {
            if (queryResult.isError()) {
                log.error("GetInlineQueryResults failed: {}", queryResult.getError().message);
                return;
            }
            TdApi.InlineQueryResults results = queryResult.get();
            if (results.results.length == 0) {
                log.error("No inline results for gcast_setup");
                return;
            }
            String resultId = extractResultId(results.results[0]);
            if (resultId == null) return;

            TdApi.SendInlineQueryResultMessage sendInline = new TdApi.SendInlineQueryResultMessage();
            sendInline.chatId = chatId;
            sendInline.queryId = results.inlineQueryId;
            sendInline.resultId = resultId;
            sendInline.hideViaBot = false;

            userBotClient.send(sendInline, sendResult -> {
                if (sendResult.isError()) {
                    log.error("SendInlineQueryResultMessage failed: {}", sendResult.getError().message);
                    return;
                }
                stateService.saveSession(sid, config).subscribe();
                log.info("GCast session created via inline: sid={}", sid);
            });
        });
    }

    @UserBotCommand(commands = {"addwl"}, description = "Tambahkan chat ini ke whitelist gcast", sudoOnly = true)
    public void handleAddWhitelist(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long msgId = message.message.id;
        stateService.addWhitelist(chatId).subscribe();
        editMessage.editMessage(chatId, msgId, "✅ Chat ditambahkan ke whitelist").subscribe();
    }

    @UserBotCommand(commands = {"addbl"}, description = "Tambahkan chat ini ke blacklist gcast", sudoOnly = true)
    public void handleAddBlacklist(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long msgId = message.message.id;
        stateService.addBlacklist(chatId).subscribe();
        editMessage.editMessage(chatId, msgId, "✅ Chat ditambahkan ke blacklist").subscribe();
    }

    @UserBotCommand(commands = {"addlabel"}, description = "Tambahkan chat ini ke label gcast. Gunakan: ,addlabel <nama>", sudoOnly = true)
    public void handleAddLabel(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long msgId = message.message.id;

        if (args == null || args.isBlank()) {
            editMessage.editMessage(chatId, msgId, "❌ Gunakan: ,addlabel <nama_label>").subscribe();
            return;
        }

        String labelName = args.trim();
        stateService.addLabel(labelName, chatId).subscribe();
        editMessage.editMessage(chatId, msgId, "✅ Chat ditambahkan ke label: " + labelName).subscribe();
    }

    @UserBotCommand(commands = {"cancelgcast"}, description = "Batalkan broadcast yang sedang berjalan. Gunakan: ,cancelgcast <sessionId>", sudoOnly = true)
    public void handleCancelGcast(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long msgId = message.message.id;

        if (args == null || args.isBlank()) {
            editMessage.editMessage(chatId, msgId, "❌ Gunakan: ,cancelgcast <sessionId>").subscribe();
            return;
        }

        String sessionId = args.trim();
        gcastService.cancelBroadcast(sessionId);
        editMessage.editMessage(chatId, msgId, "⏹ Membatalkan broadcast: " + sessionId).subscribe();
    }

    private String extractResultId(TdApi.InlineQueryResult result) {
        try {
            Field f = result.getClass().getField("id");
            return (String) f.get(result);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("No id field on {}", result.getClass().getSimpleName(), e);
            return null;
        }
    }
}
