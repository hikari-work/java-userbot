package com.yann.demosping.bot.manager;

import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.regex.Matcher;

@Slf4j
@Component
@RequiredArgsConstructor
public class InlineBotDispatcher {

    private final InlineBotRegistry registry;

    /**
     * Dispatch inline query to appropriate handler
     */
    public void dispatch(TdApi.UpdateNewInlineQuery update) {
        String query = update.query.trim();

        log.debug("Dispatching inline query from user {}: '{}'",
                update.senderUserId, query);

        if (query.isEmpty()) {
            handleEmptyQuery(update);
            return;
        }

        String command = extractCommand(query);

        InlineBotContainer container = registry.getHandler(command);

        if (container != null) {
            if (container.hasRegex()) {
                Matcher matcher = container.getRegexPattern().matcher(query);
                if (!matcher.matches()) {
                    log.debug("Query '{}' doesn't match regex for command '{}'",
                            query, command);
                    handleNoMatch(update);
                    return;
                }
            }

            executeHandler(container, update);
            return;
        }

        for (InlineBotContainer handler : registry.getInlineContainerMap().values()) {
            if (handler.hasRegex()) {
                Matcher matcher = handler.getRegexPattern().matcher(query);
                if (matcher.matches()) {
                    executeHandler(handler, update);
                    return;
                }
            }
        }

        handleNoMatch(update);
    }

    /**
     * Execute the handler method
     */
    private void executeHandler(InlineBotContainer container, TdApi.UpdateNewInlineQuery update) {
        try {
            log.info("Executing inline query handler: {} for command '{}'",
                    container.getMethod().getName(), container.getCommand());

            container.getMethod().setAccessible(true);
            container.getMethod().invoke(container.getBean(), update);

        } catch (IllegalAccessException e) {
            log.error("Cannot access inline query handler method: {}",
                    container.getMethod().getName(), e);
        } catch (InvocationTargetException e) {
            log.error("Error executing inline query handler: {}",
                    container.getMethod().getName(), e.getTargetException());
        } catch (Exception e) {
            log.error("Unexpected error in inline query handler: {}",
                    container.getMethod().getName(), e);
        }
    }

    /**
     * Extract command from query (first word)
     */
    private String extractCommand(String query) {
        int spaceIndex = query.indexOf(' ');
        if (spaceIndex > 0) {
            return query.substring(0, spaceIndex).toLowerCase();
        }
        return query.toLowerCase();
    }

    /**
     * Handle empty query (show default results)
     */
    private void handleEmptyQuery(TdApi.UpdateNewInlineQuery update) {
        log.debug("Empty inline query from user {}", update.senderUserId);

        // Check if there's a handler for empty queries
        InlineBotContainer emptyHandler = registry.getHandler("");
        if (emptyHandler != null) {
            executeHandler(emptyHandler, update);
        } else {
            log.debug("No handler registered for empty inline queries");
        }
    }

    /**
     * Handle no matching handler found
     */
    private void handleNoMatch(TdApi.UpdateNewInlineQuery update) {
        log.debug("No handler found for inline query: '{}'", update.query);
    }

    /**
     * Get all registered commands (for help or debugging)
     */
    public String[] getRegisteredCommands() {
        return registry.getInlineContainerMap().keySet()
                .stream()
                .sorted()
                .toArray(String[]::new);
    }
}