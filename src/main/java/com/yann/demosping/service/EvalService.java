package com.yann.demosping.service;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JShell-based eval service.
 *
 * Each eval call creates a fresh JShell instance with local execution (same JVM),
 * pre-loaded imports and context variables from EvalContext.
 *
 * Snapshots are stored by evalId so Re-run can restore the original context.
 */
@Slf4j
@Component
public class EvalService {

    public record EvalEntry(String code, EvalContext.Snapshot snapshot) {}

    /** evalId → (code + snapshot) stored for Re-run */
    private final ConcurrentHashMap<String, EvalEntry> entries = new ConcurrentHashMap<>();

    private static final String INIT = """
            import it.tdlight.jni.TdApi;
            import it.tdlight.client.SimpleTelegramClient;
            import com.yann.demosping.service.EvalContext;
            import java.util.*;
            import java.util.concurrent.*;
            SimpleTelegramClient c = EvalContext.c;
            it.tdlight.jni.TdApi.UpdateNewMessage message = EvalContext.message;
            it.tdlight.jni.TdApi.Message reply = EvalContext.reply;
            long chatId = EvalContext.chatId;
            long msgId  = EvalContext.msgId;
            """;

    /**
     * Evaluate a snippet. EvalContext must be set before calling this.
     * @return human-readable result string
     */
    public String evaluate(String code) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = System.out;
        System.setOut(new PrintStream(baos));

        try (JShell jshell = JShell.builder().executionEngine("local").build()) {
            // Load init lines one by one (JShell processes complete statements)
            for (String line : INIT.split("\n")) {
                if (!line.isBlank()) {
                    List<SnippetEvent> events = jshell.eval(line.trim());
                    for (SnippetEvent e : events) {
                        if (e.status() == Snippet.Status.REJECTED) {
                            log.warn("Init rejected '{}': {}", line.trim(),
                                    jshell.diagnostics(e.snippet())
                                            .map(d -> d.getMessage(Locale.ENGLISH))
                                            .toList());
                        }
                    }
                }
            }

            // Evaluate user code
            List<SnippetEvent> events = jshell.eval(code);
            System.out.flush();
            String stdout = baos.toString().trim();

            StringBuilder result = new StringBuilder();
            for (SnippetEvent e : events) {
                if (e.exception() != null) {
                    Throwable ex = e.exception();
                    result.append("Exception: ").append(ex.getClass().getSimpleName())
                            .append(": ").append(ex.getMessage());
                } else if (e.status() == Snippet.Status.REJECTED) {
                    jshell.diagnostics(e.snippet())
                            .map(d -> "Error: " + d.getMessage(Locale.ENGLISH))
                            .forEach(msg -> result.append(msg).append("\n"));
                } else if (e.value() != null && !e.value().equals("null")) {
                    result.append(e.value());
                }
            }

            String value = result.toString().trim();
            if (!stdout.isEmpty() && !value.isEmpty()) return stdout + "\n" + value;
            if (!stdout.isEmpty()) return stdout;
            if (!value.isEmpty()) return value;
            return "(no output)";

        } catch (Exception ex) {
            log.error("JShell evaluation failed", ex);
            return "JShell error: " + ex.getMessage();
        } finally {
            System.setOut(oldOut);
        }
    }

    public void storeEntry(String evalId, String code, EvalContext.Snapshot snapshot) {
        entries.put(evalId, new EvalEntry(code, snapshot));
    }

    public EvalEntry getEntry(String evalId) {
        return entries.get(evalId);
    }

    public void removeEntry(String evalId) {
        entries.remove(evalId);
    }
}
