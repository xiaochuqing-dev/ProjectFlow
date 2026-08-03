package com.projectflow.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

@Component
public class FixedCommandExecutor implements LocalCommandExecutor {
    private static final int MAX_OUTPUT_CHARS = 100_000;
    private static final int MAX_CAPTURE_BYTES = MAX_OUTPUT_CHARS * 4;

    @Override
    public CommandResult execute(Path directory, List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readOutput(process));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                awaitOutput(output);
                return new CommandResult(-1, "", true);
            }
            return new CommandResult(process.exitValue(), awaitOutput(output), false);
        } catch (IOException exception) {
            return new CommandResult(-1, "", false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "", false);
        }
    }

    private static String readOutput(Process process) {
        try (InputStream input = process.getInputStream(); ByteArrayOutputStream captured = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int remaining = MAX_CAPTURE_BYTES - captured.size();
                if (remaining > 0) captured.write(buffer, 0, Math.min(read, remaining));
            }
            String output = captured.toString(StandardCharsets.UTF_8);
            return output.length() <= MAX_OUTPUT_CHARS ? output : output.substring(0, MAX_OUTPUT_CHARS);
        } catch (IOException exception) {
            return "";
        }
    }

    private static String awaitOutput(CompletableFuture<String> output) throws InterruptedException {
        try {
            return output.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            output.cancel(true);
            return "";
        }
    }
}
