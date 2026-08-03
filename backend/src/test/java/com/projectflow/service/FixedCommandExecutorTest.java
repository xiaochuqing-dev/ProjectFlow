package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class FixedCommandExecutorTest {
    @Test
    void drainsHighVolumeOutputWithoutPipeDeadlockOnAnySupportedPlatform() {
        String executable = Path.of(
            System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java"
        ).toString();
        String classpath = System.getProperty("java.class.path");

        LocalCommandExecutor.CommandResult result = new FixedCommandExecutor().execute(
            Path.of(System.getProperty("java.io.tmpdir")),
            List.of(executable, "-cp", classpath, OutputProducer.class.getName()),
            Duration.ofSeconds(20)
        );

        assertThat(result.timedOut()).isFalse();
        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).hasSize(100_000).startsWith("project-history-output-");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static final class OutputProducer {
        private OutputProducer() {
        }

        public static void main(String[] args) {
            String value = "project-history-output-" + "x".repeat(4_096);
            for (int index = 0; index < 100; index++) System.out.print(value);
        }
    }
}
