package com.yann.demosping.annotations;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserBotCommand {

    String[] commands();
    String description();
    boolean sudoOnly() default false;
}
