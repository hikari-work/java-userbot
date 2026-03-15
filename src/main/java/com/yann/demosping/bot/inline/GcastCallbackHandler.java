package com.yann.demosping.bot.inline;

import com.yann.demosping.dto.GcastConfig;
import com.yann.demosping.service.ChatFolderCache;
import com.yann.demosping.service.GcastService;
import com.yann.demosping.service.GcastStateService;
import com.yann.demosping.utils.Keyboard;
import it.tdlight.jni.TdApi;
import it.tdlight.client.SimpleTelegramClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Component
public class GcastCallbackHandler {

    private final SimpleTelegramClient botClient;
    private final GcastStateService stateService;
    private final GcastService gcastService;
    private final ChatFolderCache chatFolderCache;

    public GcastCallbackHandler(
            @Qualifier("botClient") SimpleTelegramClient botClient,
            @Qualifier("userBotClient") SimpleTelegramClient userBotClient,
            GcastStateService stateService,
            GcastService gcastService,
            ChatFolderCache chatFolderCache) {
        this.botClient = botClient;
        this.stateService = stateService;
        this.gcastService = gcastService;
        this.chatFolderCache = chatFolderCache;
    }

    private void answer(long queryId, String text) {
        botClient.send(new TdApi.AnswerCallbackQuery(queryId, text, false, "", 0), r -> {});
    }

    public void editConfig(long chatId, long messageId, String html, TdApi.ReplyMarkupInlineKeyboard keyboard) {
        botClient.send(new TdApi.ParseTextEntities(html, new TdApi.TextParseModeHTML()), parseResult -> {
            TdApi.FormattedText ft = parseResult.isError()
                    ? new TdApi.FormattedText(html, new TdApi.TextEntity[0])
                    : parseResult.get();
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = ft;
            TdApi.EditMessageText editReq = new TdApi.EditMessageText();
            editReq.chatId = chatId;
            editReq.messageId = messageId;
            editReq.inputMessageContent = content;
            editReq.replyMarkup = keyboard;
            botClient.send(editReq, r -> {
                if (r.isError()) {
                    log.warn("Failed to edit config message chatId={} messageId={}: {}",
                            chatId, messageId, r.getError().message);
                }
            });
        });
    }

    public void editConfigInline(String inlineMessageId, String html, TdApi.ReplyMarkupInlineKeyboard keyboard) {
        botClient.send(new TdApi.ParseTextEntities(html, new TdApi.TextParseModeHTML()), parseResult -> {
            TdApi.FormattedText ft = parseResult.isError()
                    ? new TdApi.FormattedText(html, new TdApi.TextEntity[0])
                    : parseResult.get();
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = ft;
            botClient.send(new TdApi.EditInlineMessageText(inlineMessageId, keyboard, content), r -> {
                if (r.isError() && !r.getError().message.equals("MESSAGE_NOT_MODIFIED")) {
                    log.warn("EditInlineMessageText failed inlineMessageId={}: {}", inlineMessageId, r.getError().message);
                }
            });
        });
    }

    public void editConfig(GcastConfig config, String html, TdApi.ReplyMarkupInlineKeyboard keyboard) {
        if (config.controlInlineMessageId != null && !config.controlInlineMessageId.isBlank()) {
            editConfigInline(config.controlInlineMessageId, html, keyboard);
        } else {
            editConfig(config.controlChatId, config.controlMessageId, html, keyboard);
        }
    }

    private String formatDelay(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        if (ms < 3600000) return (ms / 60000) + "m";
        if (ms < 86400000) return (ms / 3600000) + "j";
        return (ms / 86400000) + "h";
    }

    private String formatFilters(Set<String> modes) {
        if (modes == null || modes.isEmpty()) return "-";
        List<String> labels = new ArrayList<>();
        if (modes.contains("mainChatList")) labels.add("Main Chat");
        if (modes.contains("archived")) labels.add("Arsip");
        if (modes.contains("folder")) labels.add("Folder");
        if (modes.contains("whitelist")) labels.add("Whitelist");
        if (modes.contains("blacklist")) labels.add("Blacklist");
        if (modes.contains("label")) labels.add("Label");
        if (modes.contains("supergroup")) labels.add("Supergroup");
        if (modes.contains("basicGroup")) labels.add("BasicGroup");
        if (modes.contains("channel")) labels.add("Channel");
        if (modes.contains("privateChat")) labels.add("Private Chat");
        return String.join(", ", labels);
    }

    public TdApi.ReplyMarkupInlineKeyboard buildDelayKeyboard(String sid) {
        String p = "gc:d:" + sid + ":";
        return Keyboard.of(
                new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("1s", p + 1000), Keyboard.callbackBtn("2s", p + 2000), Keyboard.callbackBtn("5s", p + 5000),
                        Keyboard.callbackBtn("10s", p + 10000), Keyboard.callbackBtn("20s", p + 20000), Keyboard.callbackBtn("30s", p + 30000)},
                new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("1m", p + 60000), Keyboard.callbackBtn("2m", p + 120000), Keyboard.callbackBtn("5m", p + 300000),
                        Keyboard.callbackBtn("10m", p + 600000), Keyboard.callbackBtn("15m", p + 900000), Keyboard.callbackBtn("30m", p + 1800000)},
                new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("60m", p + 3600000), Keyboard.callbackBtn("120m", p + 7200000), Keyboard.callbackBtn("150m", p + 9000000),
                        Keyboard.callbackBtn("240m", p + 14400000), Keyboard.callbackBtn("12h", p + 43200000), Keyboard.callbackBtn("24h", p + 86400000)},
                new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("❌ Batal", "gc:ca:" + sid)}
        );
    }

    public TdApi.ReplyMarkupInlineKeyboard buildFilterKeyboard(String sid, Set<String> selected) {
        if (selected == null) selected = new HashSet<>();
        String check = "✅ ";
        String empty = "☐ ";

        boolean mainSel = selected.contains("mainChatList");
        boolean archSel = selected.contains("archived");
        boolean folderSel = selected.contains("folder");
        boolean wlSel = selected.contains("whitelist");
        boolean blSel = selected.contains("blacklist");
        boolean labelSel = selected.contains("label");
        boolean sgSel = selected.contains("supergroup");
        boolean bgSel = selected.contains("basicGroup");
        boolean chSel = selected.contains("channel");
        boolean pcSel = selected.contains("privateChat");

        String folderLabel = (folderSel ? check : empty) + "Folder ▶";
        String labelLabel = (labelSel ? check : empty) + "Label ▶";

        return Keyboard.of(
                new TdApi.InlineKeyboardButton[]{
                        Keyboard.callbackBtn((mainSel ? check : empty) + "Main Chat", "gc:f:" + sid + ":mainChatList"),
                        Keyboard.callbackBtn((archSel ? check : empty) + "Archived", "gc:f:" + sid + ":archived"),
                        Keyboard.callbackBtn(folderLabel, "gc:f:" + sid + ":folder")
                },
                new TdApi.InlineKeyboardButton[]{
                        Keyboard.callbackBtn((wlSel ? check : empty) + "Whitelist", "gc:f:" + sid + ":whitelist"),
                        Keyboard.callbackBtn((blSel ? check : empty) + "Blacklist", "gc:f:" + sid + ":blacklist"),
                        Keyboard.callbackBtn(labelLabel, "gc:f:" + sid + ":label")
                },
                new TdApi.InlineKeyboardButton[]{
                        Keyboard.callbackBtn((sgSel ? check : empty) + "Supergroup", "gc:f:" + sid + ":supergroup"),
                        Keyboard.callbackBtn((bgSel ? check : empty) + "BasicGroup", "gc:f:" + sid + ":basicGroup"),
                        Keyboard.callbackBtn((chSel ? check : empty) + "Channel", "gc:f:" + sid + ":channel")
                },
                new TdApi.InlineKeyboardButton[]{
                        Keyboard.callbackBtn((pcSel ? check : empty) + "Private Chat", "gc:f:" + sid + ":privateChat")
                },
                new TdApi.InlineKeyboardButton[]{
                        Keyboard.callbackBtn("✅ Selesai", "gc:fd:" + sid),
                        Keyboard.callbackBtn("❌ Batal", "gc:ca:" + sid)
                }
        );
    }

    private TdApi.ReplyMarkupInlineKeyboard buildScheduleKeyboard(String sid) {
        return Keyboard.of(new TdApi.InlineKeyboardButton[]{
                Keyboard.callbackBtn("▶ Sekali", "gc:ro:" + sid),
                Keyboard.callbackBtn("🕐 Terjadwal", "gc:rs:" + sid),
                Keyboard.callbackBtn("❌ Batal", "gc:ca:" + sid)
        });
    }

    public TdApi.ReplyMarkupInlineKeyboard buildSendModeKeyboard(String sid, GcastConfig config) {
        String fwdLabel  = (!config.sendViaBot && !config.noForwardHeader ? "✅ " : "") + "📨 Forward";
        String copyLabel = (!config.sendViaBot &&  config.noForwardHeader ? "✅ " : "") + "📋 Copy";
        String botLabel  = ( config.sendViaBot                            ? "✅ " : "") + "🤖 Via Bot";
        return Keyboard.of(
                new TdApi.InlineKeyboardButton[]{
                        Keyboard.callbackBtn(fwdLabel,  "gc:sm:" + sid + ":fwd"),
                        Keyboard.callbackBtn(copyLabel, "gc:sm:" + sid + ":copy"),
                        Keyboard.callbackBtn(botLabel,  "gc:sm:" + sid + ":bot")
                },
                new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("❌ Batal", "gc:ca:" + sid)}
        );
    }

    private TdApi.ReplyMarkupInlineKeyboard buildCancelKeyboard(String sid) {
        return Keyboard.of(new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("❌ Batal", "gc:ca:" + sid)});
    }

    public TdApi.ReplyMarkupInlineKeyboard buildStopKeyboard(String sid) {
        return Keyboard.of(new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("⏹ Stop", "gc:stop:" + sid)});
    }

    private TdApi.ReplyMarkupInlineKeyboard buildIntervalKeyboard(String sid) {
        String p = "gc:ri:" + sid + ":";
        return Keyboard.of(
                new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("1s", p + 1000), Keyboard.callbackBtn("2s", p + 2000), Keyboard.callbackBtn("5s", p + 5000),
                        Keyboard.callbackBtn("10s", p + 10000), Keyboard.callbackBtn("20s", p + 20000), Keyboard.callbackBtn("30s", p + 30000)},
                new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("1m", p + 60000), Keyboard.callbackBtn("2m", p + 120000), Keyboard.callbackBtn("5m", p + 300000),
                        Keyboard.callbackBtn("10m", p + 600000), Keyboard.callbackBtn("15m", p + 900000), Keyboard.callbackBtn("30m", p + 1800000)},
                new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("60m", p + 3600000), Keyboard.callbackBtn("120m", p + 7200000), Keyboard.callbackBtn("150m", p + 9000000),
                        Keyboard.callbackBtn("240m", p + 14400000), Keyboard.callbackBtn("12h", p + 43200000), Keyboard.callbackBtn("24h", p + 86400000)},
                new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("❌ Batal", "gc:ca:" + sid)}
        );
    }

    /** Extract session ID from payload (second field after "gc:X:"). */
    private String extractSid(String payload) {
        int firstColon = payload.indexOf(':');
        if (firstColon < 0) return null;
        int secondColon = payload.indexOf(':', firstColon + 1);
        if (secondColon < 0) return null;
        String afterPrefix = payload.substring(secondColon + 1);
        String prefix = payload.substring(0, secondColon + 1);
        if (prefix.equals("gc:fd:") || prefix.equals("gc:fsub:") || prefix.equals("gc:lsub:")
                || prefix.equals("gc:back:") || prefix.equals("gc:ro:") || prefix.equals("gc:rs:")
                || prefix.equals("gc:stop:") || prefix.equals("gc:ca:")) {
            return afterPrefix;
        }
        int thirdColon = afterPrefix.indexOf(':');
        if (thirdColon < 0) return afterPrefix;
        return afterPrefix.substring(0, thirdColon);
    }

    public void handle(TdApi.UpdateNewCallbackQuery query, String payload) {
        try {
            String sid = extractSid(payload);
            if (sid != null) {
                stateService.getSession(sid).subscribe(cfg -> {
                    if (cfg != null && cfg.controlMessageId != query.messageId) {
                        cfg.controlChatId = query.chatId;
                        cfg.controlMessageId = query.messageId;
                        stateService.saveSession(sid, cfg).subscribe();
                    }
                });
            }
            dispatchPayload(query.id, payload);
        } catch (Exception e) {
            log.error("Error handling gcast callback payload='{}': {}", payload, e.getMessage(), e);
        }
    }

    public void handleInline(TdApi.UpdateNewInlineCallbackQuery callbackQuery, String payload) {
        try {
            String sid = extractSid(payload);
            if (sid != null) {
                stateService.getSession(sid).subscribe(cfg -> {
                    if (cfg != null && !callbackQuery.inlineMessageId.equals(cfg.controlInlineMessageId)) {
                        cfg.controlInlineMessageId = callbackQuery.inlineMessageId;
                        stateService.saveSession(sid, cfg).subscribe();
                    }
                });
            }
            dispatchPayload(callbackQuery.id, payload);
        } catch (Exception e) {
            log.error("Error handling gcast inline callback payload='{}': {}", payload, e.getMessage(), e);
        }
    }

    private void dispatchPayload(long callbackQueryId, String payload) {
        if (payload.startsWith("gc:d:")) handleDelay(callbackQueryId, payload);
        else if (payload.startsWith("gc:ri:")) handleRunInterval(callbackQueryId, payload);
        else if (payload.startsWith("gc:fd:")) handleFilterDone(callbackQueryId, payload);
        else if (payload.startsWith("gc:sm:")) handleSendMode(callbackQueryId, payload);
        else if (payload.startsWith("gc:fsub:")) handleFolderSubmenu(callbackQueryId, payload);
        else if (payload.startsWith("gc:lsub:")) handleLabelSubmenu(callbackQueryId, payload);
        else if (payload.startsWith("gc:fl:")) handleFolderSelect(callbackQueryId, payload);
        else if (payload.startsWith("gc:lb:")) handleLabelSelect(callbackQueryId, payload);
        else if (payload.startsWith("gc:back:")) handleBackToFilter(callbackQueryId, payload);
        else if (payload.startsWith("gc:f:")) handleFilterToggle(callbackQueryId, payload);
        else if (payload.startsWith("gc:ro:")) handleRunOnce(callbackQueryId, payload);
        else if (payload.startsWith("gc:rs:")) handleRunScheduled(callbackQueryId, payload);
        else if (payload.startsWith("gc:stop:")) handleStop(callbackQueryId, payload);
        else if (payload.startsWith("gc:ca:")) handleCancel(callbackQueryId, payload);
        else log.warn("Unknown gcast callback payload: {}", payload);
    }

    private void withSession(String sid, long callbackQueryId, java.util.function.Consumer<GcastConfig> action) {
        stateService.getSession(sid)
                .switchIfEmpty(Mono.defer(() -> {
                    answer(callbackQueryId, "Sesi tidak ditemukan.");
                    return Mono.<GcastConfig>empty();
                }))
                .subscribe(action::accept,
                        ex -> log.error("Error loading session sid={}: {}", sid, ex.getMessage(), ex));
    }

    private void handleDelay(long callbackQueryId, String payload) {
        String rest = payload.substring("gc:d:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        long delayMs;
        try {
            delayMs = Long.parseLong(rest.substring(lastColon + 1));
        } catch (NumberFormatException e) {
            answer(callbackQueryId, "Invalid delay value");
            return;
        }

        withSession(sid, callbackQueryId, config -> {
            config.delayMs = delayMs;
            config.step = "FILTER";
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "Jeda dipilih!");
            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(delayMs) +
                    "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
            editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
        });
    }

    private void handleFilterToggle(long callbackQueryId, String payload) {
        String rest = payload.substring("gc:f:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        String mode = rest.substring(lastColon + 1);

        if ("folder".equals(mode)) { handleFolderSubmenu(callbackQueryId, "gc:fsub:" + sid); return; }
        if ("label".equals(mode)) { handleLabelSubmenu(callbackQueryId, "gc:lsub:" + sid); return; }

        withSession(sid, callbackQueryId, config -> {
            if (config.filterModes == null) config.filterModes = new HashSet<>();
            if (config.filterModes.contains(mode)) config.filterModes.remove(mode);
            else config.filterModes.add(mode);
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "Filter diperbarui!");
            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                    "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
            editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
        });
    }

    private void handleFilterDone(long callbackQueryId, String payload) {
        String sid = payload.substring("gc:fd:".length());
        withSession(sid, callbackQueryId, config -> {
            if (config.filterModes == null || config.filterModes.isEmpty()) {
                answer(callbackQueryId, "Pilih setidaknya 1 filter!");
                return;
            }
            config.step = "SEND_MODE";
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "Filter dikonfirmasi!");
            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                    " | Filter: " + formatFilters(config.filterModes) +
                    "\n\nLangkah 3: Pilih mode pengiriman";
            editConfig(config, html, buildSendModeKeyboard(sid, config));
        });
    }

    private void handleSendMode(long callbackQueryId, String payload) {
        String rest = payload.substring("gc:sm:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        String mode = rest.substring(lastColon + 1);

        withSession(sid, callbackQueryId, config -> {
            switch (mode) {
                case "fwd"  -> { config.sendViaBot = false; config.noForwardHeader = false; }
                case "copy" -> { config.sendViaBot = false; config.noForwardHeader = true;  }
                case "bot"  -> { config.sendViaBot = true;  config.noForwardHeader = false; }
                default -> { answer(callbackQueryId, "Mode tidak dikenal."); return; }
            }
            config.step = "SCHEDULE";
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "Mode pengiriman dipilih!");
            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                    " | Filter: " + formatFilters(config.filterModes) +
                    "\n\nLangkah 4: Jadwal pengiriman";
            editConfig(config, html, buildScheduleKeyboard(sid));
        });
    }

    private void handleFolderSubmenu(long callbackQueryId, String payload) {
        String sid = payload.substring("gc:fsub:".length());
        withSession(sid, callbackQueryId, config -> {
            Map<Integer, String> folders = chatFolderCache.getFolders();
            if (folders.isEmpty()) {
                answer(callbackQueryId, "Belum ada folder. Buat folder di Telegram terlebih dahulu.");
                return;
            }
            config.step = "FILTER_FOLDER";
            stateService.saveSession(sid, config).subscribe();

            List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : folders.entrySet()) {
                rows.add(new TdApi.InlineKeyboardButton[]{
                        Keyboard.callbackBtn(entry.getValue(), "gc:fl:" + sid + ":" + entry.getKey())
                });
            }
            rows.add(new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("◀ Kembali", "gc:back:" + sid)});

            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) + "\n\nPilih folder:";
            editConfig(config, html, Keyboard.ofRows(rows));
            answer(callbackQueryId, "");
        });
    }

    private void handleLabelSubmenu(long callbackQueryId, String payload) {
        String sid = payload.substring("gc:lsub:".length());
        withSession(sid, callbackQueryId, config ->
                stateService.getLabelNames()
                        .subscribe(labelNames -> {
                            if (labelNames == null || labelNames.isEmpty()) {
                                answer(callbackQueryId, "Belum ada label. Gunakan ,addlabel <nama>");
                                return;
                            }
                            config.step = "FILTER_LABEL";
                            stateService.saveSession(sid, config).subscribe();

                            List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
                            for (String name : labelNames) {
                                rows.add(new TdApi.InlineKeyboardButton[]{
                                        Keyboard.callbackBtn(name, "gc:lb:" + sid + ":" + name)
                                });
                            }
                            rows.add(new TdApi.InlineKeyboardButton[]{Keyboard.callbackBtn("◀ Kembali", "gc:back:" + sid)});

                            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) + "\n\nPilih label:";
                            editConfig(config, html, Keyboard.ofRows(rows));
                            answer(callbackQueryId, "");
                        })
        );
    }

    private void handleFolderSelect(long callbackQueryId, String payload) {
        String rest = payload.substring("gc:fl:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        int folderId;
        try {
            folderId = Integer.parseInt(rest.substring(lastColon + 1));
        } catch (NumberFormatException e) {
            answer(callbackQueryId, "Invalid folder id");
            return;
        }

        withSession(sid, callbackQueryId, config -> {
            if (config.filterModes == null) config.filterModes = new HashSet<>();
            config.filterModes.add("folder");
            config.folderId = folderId;
            config.folderName = chatFolderCache.getFolders().getOrDefault(folderId, "Folder " + folderId);
            config.step = "FILTER";
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "Folder dipilih!");
            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                    "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
            editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
        });
    }

    private void handleLabelSelect(long callbackQueryId, String payload) {
        String rest = payload.substring("gc:lb:".length());
        int firstColon = rest.indexOf(':');
        if (firstColon < 0) return;
        String sid = rest.substring(0, firstColon);
        String labelName = rest.substring(firstColon + 1);

        withSession(sid, callbackQueryId, config -> {
            if (config.filterModes == null) config.filterModes = new HashSet<>();
            config.filterModes.add("label");
            config.labelName = labelName;
            config.step = "FILTER";
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "Label dipilih!");
            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                    "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
            editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
        });
    }

    private void handleBackToFilter(long callbackQueryId, String payload) {
        String sid = payload.substring("gc:back:".length());
        withSession(sid, callbackQueryId, config -> {
            config.step = "FILTER";
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "");
            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                    "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
            editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
        });
    }

    private void handleRunOnce(long callbackQueryId, String payload) {
        String sid = payload.substring("gc:ro:".length());
        withSession(sid, callbackQueryId, config -> {
            config.scheduled = false;
            config.step = "RUNNING";
            config.status = "running";
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "Memulai broadcast...");

            String startHtml = "📢 <b>Broadcast berjalan</b>\nSession: " + sid +
                    "\nJeda: " + formatDelay(config.delayMs) +
                    " | Filter: " + formatFilters(config.filterModes) +
                    "\nMemulai broadcast...";
            editConfig(config, startHtml, buildStopKeyboard(sid));

            gcastService.resolveChatIds(config).subscribe(chatIds -> {
                config.totalChats = chatIds.size();
                stateService.saveSession(sid, config).subscribe();
                gcastService.executeBroadcast(sid, chatIds, config, progress -> {
                    stateService.getSession(sid).subscribe(latest -> {
                        if (latest == null) return;
                        if (progress.startsWith("DONE:")) {
                            String counts = progress.substring("DONE:".length());
                            editConfig(latest, "✅ <b>Broadcast selesai!</b>\nSession: " + sid + "\nTerkirim: " + counts, Keyboard.empty());
                            stateService.removeRunningSession(sid).subscribe();
                        } else if ("CANCELLED".equals(progress)) {
                            editConfig(latest, "⏹ <b>Broadcast dibatalkan</b>\nSession: " + sid, Keyboard.empty());
                            stateService.removeRunningSession(sid).subscribe();
                        } else {
                            editConfig(latest, "📢 <b>Broadcast berjalan</b>\nSession: " + sid +
                                    "\nJeda: " + formatDelay(latest.delayMs) +
                                    " | Filter: " + formatFilters(latest.filterModes) +
                                    "\nProgress: " + progress + " chat", buildStopKeyboard(sid));
                        }
                    });
                });
            });
        });
    }

    private void handleRunScheduled(long callbackQueryId, String payload) {
        String sid = payload.substring("gc:rs:".length());
        withSession(sid, callbackQueryId, config -> {
            config.scheduled = true;
            config.step = "AWAITING_INTERVAL";
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "Pilih interval pengulangan");
            String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                    " | Filter: " + formatFilters(config.filterModes) +
                    "\n\nLangkah 4: Pilih interval pengulangan broadcast";
            editConfig(config, html, buildIntervalKeyboard(sid));
        });
    }

    private void handleRunInterval(long callbackQueryId, String payload) {
        String rest = payload.substring("gc:ri:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        long intervalMs;
        try {
            intervalMs = Long.parseLong(rest.substring(lastColon + 1));
        } catch (NumberFormatException e) {
            answer(callbackQueryId, "Invalid interval");
            return;
        }

        withSession(sid, callbackQueryId, config -> {
            config.intervalMs = intervalMs;
            config.step = "RUNNING";
            config.status = "running";
            stateService.saveSession(sid, config).subscribe();
            answer(callbackQueryId, "Broadcast terjadwal dimulai!");

            String scheduledHtml = "📢 <b>GCast Terjadwal</b>\nSession: " + sid +
                    "\nJeda: " + formatDelay(config.delayMs) +
                    " | Filter: " + formatFilters(config.filterModes) +
                    "\nInterval: " + formatDelay(intervalMs) +
                    "\nBroadcast akan berjalan otomatis.";
            editConfig(config, scheduledHtml, buildStopKeyboard(sid));

            final long finalIntervalMs = intervalMs;
            gcastService.scheduleRecurring(sid, config, progress -> {
                stateService.getSession(sid).subscribe(latest -> {
                    if (latest == null) return;
                    if (progress.startsWith("DONE:")) {
                        editConfig(latest, "✅ <b>Broadcast terjadwal selesai!</b>\nSession: " + sid +
                                "\nInterval: " + formatDelay(finalIntervalMs) +
                                "\nTerkirim: " + progress.substring("DONE:".length()), buildStopKeyboard(sid));
                    } else if ("CANCELLED".equals(progress)) {
                        editConfig(latest, "⏹ <b>Broadcast terjadwal dibatalkan</b>\nSession: " + sid, Keyboard.empty());
                    } else {
                        editConfig(latest, "📢 <b>Broadcast terjadwal berjalan</b>\nSession: " + sid +
                                "\nInterval: " + formatDelay(finalIntervalMs) +
                                "\nProgress: " + progress + " chat", buildStopKeyboard(sid));
                    }
                });
            });
        });
    }

    private void handleStop(long callbackQueryId, String payload) {
        String sid = payload.substring("gc:stop:".length());
        gcastService.cancelBroadcast(sid);
        answer(callbackQueryId, "Menghentikan broadcast...");
        stateService.getSession(sid).subscribe(config -> {
            if (config != null) editConfig(config, "⏹ <b>Menghentikan broadcast...</b>\nSession: " + sid, Keyboard.empty());
        });
    }

    private void handleCancel(long callbackQueryId, String payload) {
        String sid = payload.substring("gc:ca:".length());
        withSession(sid, callbackQueryId, config -> {
            if ("running".equals(config.status)) gcastService.cancelBroadcast(sid);
            stateService.clearAwaitingCron(config.controlChatId).subscribe();
            stateService.deleteSession(sid).subscribe();
            answer(callbackQueryId, "Dibatalkan");
            editConfig(config, "❌ <b>GCast dibatalkan</b>\nSession: " + sid, Keyboard.empty());
        });
    }
}
