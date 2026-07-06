package com.projectflow.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

@Component
public class FixedCommandExecutor implements LocalCommandExecutor {
    private static final int MAX_OUTPUT_CHARS = 100_000;

    @Override
    public CommandResult execute(Path directory, List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(-1, "", true);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (output.length() > MAX_OUTPUT_CHARS) {
                output = output.substring(0, MAX_OUTPUT_CHARS);
            }
            return new CommandResult(process.exitValue(), output, false);
        } catch (IOException exception) {
            return new CommandResult(-1, "", false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "", false);
        }
    }
}
