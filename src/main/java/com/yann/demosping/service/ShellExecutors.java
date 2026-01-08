package com.yann.demosping.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class ShellExecutors {

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B\\[[0-9;]*[a-zA-Z]");

    public CompletableFuture<String> execute(String command) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder stringBuilder = new StringBuilder();
            Process process = null;

            try {
                ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        String cleanLine = ANSI_ESCAPE.matcher(line).replaceAll("");
                        stringBuilder.append(cleanLine).append("\n");
                    }
                }

                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return "Error: Command timed out (30s).";
                }

            } catch (Exception e) {
                return "Error: " + e.getMessage();
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroy();
                }
            }
            return stringBuilder.toString();
        });
    }
}