package com.projectflow.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface LocalCommandExecutor {
    CommandResult execute(Path directory, List<String> command, Duration timeout);

    record CommandResult(int exitCode, String output, boolean timedOut) {
    }
}
