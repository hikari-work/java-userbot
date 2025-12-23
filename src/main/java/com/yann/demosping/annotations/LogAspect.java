package com.yann.demosping.annotations;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Aspect
public class LogAspect {

    @Around("@annotation(userBotCommand)")
    public Object logCommandExecution(ProceedingJoinPoint joinPoint, UserBotCommand userBotCommand) throws Throwable{
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        String primaryCommand = userBotCommand.commands().length > 0 ? userBotCommand.commands()[0] : "Unknown";
        log.info(">> Start Command Method : {} Args : {}", primaryCommand, methodName);
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            log.error("!! Error Command {} Error {}", primaryCommand, e.getMessage());
            throw e;
        }
        long timeTaken = System.currentTimeMillis() - startTime;
        log.info("<< End Command {} Time: {}ms", primaryCommand,timeTaken);
        return result;
    }
}
