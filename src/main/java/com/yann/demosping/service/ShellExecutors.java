package com.yann.demosping.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Component
public class ShellExecutors {

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B\\[[0-9;]*[a-zA-Z]");

    public CompletableFuture<String> execute(String command) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder stringBuilder = new StringBuilder();
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    String cleanLine = ANSI_ESCAPE.matcher(line).replaceAll("");
                    stringBuilder.append(cleanLine).append("\n");
                }
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return stringBuilder.toString();
        });
    }
}
