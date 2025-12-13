package com.yann.demosping.manager;

import com.yann.demosping.annotations.UserBotCommand;

import java.lang.reflect.Method;

public record CommandContainer(
        Object bean,
        Method method,
        UserBotCommand command) {}
