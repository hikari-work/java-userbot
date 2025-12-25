package com.yann.demosping.bot.manager;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface InlineQuery {

    String commands() default "";
    String regex() default "";
}
