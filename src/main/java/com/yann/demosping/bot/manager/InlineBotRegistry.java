package com.yann.demosping.bot.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Component
public class InlineBotRegistry implements ApplicationListener<ContextRefreshedEvent> {

    @Getter
    private final Map<String, InlineBotContainer> inlineContainerMap = new HashMap<>();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        String[] beanNames = context.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> target = AopUtils.getTargetClass(bean);

            for (Method method : target.getDeclaredMethods()) {
                if (method.isAnnotationPresent(InlineQuery.class)) {
                    InlineQuery query = method.getAnnotation(InlineQuery.class);
                    if (query == null) continue;

                    String command = query.commands().trim();
                    String regex = query.regex().trim();

                    if (command.isEmpty()) {
                        log.warn("Inline query command is empty for method: {}.{}",
                                target.getSimpleName(), method.getName());
                        continue;
                    }

                    if (inlineContainerMap.containsKey(command)) {
                        log.error("Duplicate inline query command found: '{}' in {}.{}",
                                command, target.getSimpleName(), method.getName());
                        throw new IllegalStateException(
                                "Duplicate inline query command: " + command);
                    }

                    if (!isValidMethodSignature(method)) {
                        log.error("Invalid method signature for inline query '{}' in {}.{}. " +
                                        "Expected: void methodName(TdApi.UpdateNewInlineQuery)",
                                command, target.getSimpleName(), method.getName());
                        throw new IllegalStateException(
                                "Invalid method signature for inline query: " + command);
                    }

                    Pattern regexPattern = null;
                    if (!regex.isEmpty()) {
                        try {
                            regexPattern = Pattern.compile(regex);
                            log.debug("Regex pattern compiled for command '{}': {}",
                                    command, regex);
                        } catch (PatternSyntaxException e) {
                            log.error("Invalid regex pattern for command '{}': {}",
                                    command, regex, e);
                            throw new IllegalStateException(
                                    "Invalid regex pattern for inline query: " + command, e);
                        }
                    }

                    InlineBotContainer container = new InlineBotContainer(
                            bean, method, command, regexPattern);
                    inlineContainerMap.put(command, container);

                    log.info("Registered inline query: '{}' -> {}.{}{}",
                            command,
                            target.getSimpleName(),
                            method.getName(),
                            regexPattern != null ? " with regex: " + regex : "");
                }
            }
        }

        log.info("Total inline query handlers registered: {}", inlineContainerMap.size());
    }

    /**
     * Validate method signature: void methodName(TdApi.UpdateNewInlineQuery)
     */
    private boolean isValidMethodSignature(Method method) {
        // Check return type
        if (!method.getReturnType().equals(void.class) &&
                !method.getReturnType().equals(Void.class)) {
            return false;
        }

        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != 1) {
            return false;
        }

        Class<?> paramType = paramTypes[0];
        return paramType.getCanonicalName().endsWith("TdApi.UpdateNewInlineQuery");
    }

    /**
     * Get handler by command
     */
    public InlineBotContainer getHandler(String command) {
        return inlineContainerMap.get(command);
    }

    /**
     * Check if command exists
     */
    public boolean hasCommand(String command) {
        return inlineContainerMap.containsKey(command);
    }
}