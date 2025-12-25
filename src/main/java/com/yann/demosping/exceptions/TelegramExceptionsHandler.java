package com.yann.demosping.exceptions;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TelegramExceptionsHandler {

    Class<? extends Throwable>[] value();
    String errorMessage() default "";
    boolean editMessage() default true;
    boolean deleMessage() default false;
}
