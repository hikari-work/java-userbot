package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.manager.CommandRegistry;
import com.yann.demosping.service.ModuleStateService;
import com.yann.demosping.utils.ArgsParser;
import it.tdlight.client.SimpleTelegramClient;
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
    private final SimpleTelegramClient client;
    private final CommandRegistry commandRegistry;

    @UserBotCommand(commands = {"filter", "filters"}, description = "")
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
            moduleStateService.saveFilter(sourceChatId, filterTrigger, savedValue);
            client.send(
                    new TdApi.ParseTextEntities("<i>Trigger </i><code>" + filterTrigger +"</code><i> sudah di set</i>", new TdApi.TextParseModeHTML()),
                    resultText -> {
                        if (resultText.isError()) return;
                        client.send(
                                new TdApi.EditMessageText(
                                        currentChatId,
                                        message.message.id,
                                        null,
                                        new TdApi.InputMessageText(resultText.get(), new TdApi.LinkPreviewOptions(), false)
                                )
                        );
                    }
            );
        } else {
            sendDescription(currentChatId, message.message.id);
        }
    }

    @UserBotCommand(commands = {"delfilter"}, description = "")
    public void deleteFilterHandler(TdApi.UpdateNewMessage message, String args) {
        long messageId = message.message.id;
        long chatId = message.message.chatId;
        Map<Object, Object> allFilters = moduleStateService.getAllFilters(chatId);
        if (allFilters.containsKey(args.trim().toLowerCase())) {
            moduleStateService.deleteFilter(chatId, args.toLowerCase());
            client.send(
                    new TdApi.ParseTextEntities("DONE", new TdApi.TextParseModeHTML()),
                    resultText -> {
                        if (resultText.isError()) return;
                        client.send(
                                new TdApi.EditMessageText(
                                        chatId,
                                        messageId,
                                        null,
                                        new TdApi.InputMessageText(resultText.get(), new TdApi.LinkPreviewOptions(), false)
                                )
                        );
                    }
            );
        } else {
            sendDescription(chatId, messageId);
        }
    }

    @UserBotCommand(commands = {"listfilter"}, description = "")
    public void getAllFilters(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;
        Map<Object, Object> allFilters = moduleStateService.getAllFilters(chatId);
        if (allFilters == null ||allFilters.isEmpty()) {
            client.send(
                    new TdApi.ParseTextEntities(
                            "Empty Filter", new TdApi.TextParseModeHTML()
                    ), format -> {
                        if (format.isError()) return;
                        client.send(
                                new TdApi.EditMessageText(
                                        chatId,
                                        messageId,
                                        null,
                                        new TdApi.InputMessageText(format.get(), new TdApi.LinkPreviewOptions(), false)
                                )
                        );
                    }
            );
        } else {
            sendListFilter(allFilters, chatId, messageId);
        }
    }

    private void sendDescription(Long chatId, Long messageId) {
        String text = commandRegistry.getCommand("addfilter").command().description();
        client.send(
                new TdApi.ParseTextEntities(text, new TdApi.TextParseModeHTML()), textFormatted -> {
                    if (textFormatted.isError()) return;
                    client.send(
                            new TdApi.EditMessageText(
                                    chatId,
                                    messageId,
                                    null,
                                    new TdApi.InputMessageText(textFormatted.get(), new TdApi.LinkPreviewOptions(), false)
                            )
                    );
                }
        );
    }
    private void sendListFilter(Map<Object, Object> filters, Long chatId, Long messageId) {
        StringBuilder sb =  new StringBuilder("<b>List Known Filter In This Chat :\n");
        for (Map.Entry<Object, Object> map : filters.entrySet()) {
            sb.append("<code>").append(map.getKey().toString()).append("</code>\n");
        }
        client.send(
                new TdApi.ParseTextEntities(sb.toString(), new TdApi.TextParseModeHTML()), formatted -> {
                    if (formatted.isError()) return;
                    client.send(
                            new TdApi.EditMessageText(
                                    chatId,
                                    messageId,
                                    null,
                                    new TdApi.InputMessageText(
                                            formatted.get(), new TdApi.LinkPreviewOptions(), false
                                    )
                            )
                    );
                }
        );

    }
}
