package com.yann.demosping.manager;

import com.yann.demosping.annotations.UserBotCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class CommandRegistry implements ApplicationListener<ContextRefreshedEvent> {

    private final Map<String, CommandContainer> commandMap = new HashMap<>();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : targetClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(UserBotCommand.class)) {
                    UserBotCommand command = method.getAnnotation(UserBotCommand.class);
                    if (command == null) continue;
                    for (String cmdName : command.commands()) {
                        commandMap.put(cmdName, new CommandContainer(bean, method, command));
                    }
                }
            }
        }
    }

    public CommandContainer getCommand(String trigger) {
        return commandMap.get(trigger);
    }
}
