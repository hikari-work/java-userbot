package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.manager.CommandRegistry;
import com.yann.demosping.service.ModuleStateService;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.service.EditMessage;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class Filter {

    private final ModuleStateService moduleStateService;
    private final CommandRegistry commandRegistry;
    private final EditMessage editMessage;
    private final GlobalTelegramExceptionHandler globalTelegramExceptionHandler;

    @UserBotCommand(commands = {"filter", "filters"}, description = "", sudoOnly = true)
    public void sendFilterMessage(TdApi.UpdateNewMessage message, String args) {
        sendDescription(message.message.chatId, message.message.id);
    }

    @UserBotCommand(commands = {"addfilter", "save"}, description = """
            <b>🏷 Filter Module</b>

            Simpan pesan (teks/media) agar bot membalas otomatis ketika kata kunci diketik.

            <b>Commands:</b>
            • <code>{prefix}addfilter </code>
            └ Simpan filter (Wajib Reply ke pesan target).
            └ Tambahkan argumen -trig &lt;keyword&gt;

            • <code>{prefix}delfilter &lt;keyword&gt;</code>
            └ Hapus filter yang sudah ada.

            • <code>{prefix}filters</code>
            └ Lihat daftar filter di chat ini.

            <b>Note:</b>
            <i>Reply ke pesan (Foto/Video/Stiker/Text) lalu ketik command save untuk menyimpannya.</i>
            """)
    public void filterHandler(TdApi.UpdateNewMessage message, String args) {
        long currentChatId = message.message.chatId;
        Map<String, String> param = ArgsParser.parse(args.toLowerCase().trim());
        String filterTrigger = param.get("trig");
        log.info("Trigger is {}", filterTrigger);
        if (filterTrigger == null || filterTrigger.isEmpty()) {
            sendDescription(currentChatId, message.message.id);
            return;
        }
        if (message.message.replyTo instanceof TdApi.MessageReplyToMessage messageReplyToMessage) {
            long sourceMsgId = messageReplyToMessage.messageId;
            long sourceChatId = messageReplyToMessage.chatId;
            if (sourceChatId == 0) sourceChatId = currentChatId;
            String savedValue = sourceChatId + ":" + sourceMsgId;
            final String trigger = filterTrigger;
            moduleStateService.saveFilter(sourceChatId, trigger, savedValue)
                    .then(editMessage.editMessage(currentChatId, message.message.id,
                            "<i>Trigger </i><code>" + trigger + "</code><i> has been set</i>"))
                    .doOnError(globalTelegramExceptionHandler::handle)
                    .subscribe();
        } else {
            sendDescription(currentChatId, message.message.id);
        }
    }

    @UserBotCommand(commands = {"delfilter"}, description = "")
    public void deleteFilterHandler(TdApi.UpdateNewMessage message, String args) {
        long messageId = message.message.id;
        long chatId = message.message.chatId;
        final String trigger = args.trim().toLowerCase();
        moduleStateService.getAllFilters(chatId)
                .subscribe(allFilters -> {
                    if (allFilters.containsKey(trigger)) {
                        moduleStateService.deleteFilter(chatId, trigger)
                                .then(editMessage.editMessage(chatId, messageId, "DONE"))
                                .doOnError(globalTelegramExceptionHandler::handle)
                                .subscribe();
                    } else {
                        sendDescription(chatId, messageId);
                    }
                });
    }

    @UserBotCommand(commands = {"listfilter"}, description = "")
    public void getAllFilters(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;
        moduleStateService.getAllFilters(chatId)
                .subscribe(allFilters -> {
                    if (allFilters == null || allFilters.isEmpty()) {
                        editMessage.editMessage(chatId, messageId, "Empty Filter")
                                .doOnError(globalTelegramExceptionHandler::handle)
                                .subscribe();
                    } else {
                        sendListFilter(allFilters, chatId, messageId);
                    }
                });
    }

    private void sendDescription(Long chatId, Long messageId) {
        String text = commandRegistry.getCommand("addfilter").command().description();
        editMessage.editMessage(chatId, messageId, text)
                .doOnError(globalTelegramExceptionHandler::handle)
                .subscribe();
    }

    private void sendListFilter(Map<Object, Object> filters, Long chatId, Long messageId) {
        StringBuilder sb = new StringBuilder("<b>List Known Filters In This Chat:</b>\n");
        for (Map.Entry<Object, Object> map : filters.entrySet()) {
            sb.append("<code>").append(map.getKey().toString()).append("</code>\n");
        }
        editMessage.editMessage(chatId, messageId, sb.toString())
                .doOnError(globalTelegramExceptionHandler::handle)
                .subscribe();
    }
}
