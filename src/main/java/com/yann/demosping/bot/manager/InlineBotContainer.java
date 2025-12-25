package com.yann.demosping.bot.manager;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

@Data
@AllArgsConstructor
public class InlineBotContainer {
    private Object bean;
    private Method method;
    private String command;
    private Pattern regexPattern;

    public boolean hasRegex() {
        return regexPattern != null;
    }
}