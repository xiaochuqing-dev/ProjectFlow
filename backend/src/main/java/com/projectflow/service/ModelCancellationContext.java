package com.projectflow.service;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** 将持久化 Job 的取消状态传入网关，确保恢复请求前也能停止。 */
public final class ModelCancellationContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private ModelCancellationContext() {
    }

    public static Scope bind(BooleanSupplier cancellationRequested) {
        return bind(cancellationRequested, () -> { });
    }

    public static Scope bind(BooleanSupplier cancellationRequested, Runnable heartbeat) {
        State previous = CURRENT.get();
        CURRENT.set(new State(
            cancellationRequested == null ? () -> false : cancellationRequested,
            heartbeat == null ? () -> { } : heartbeat
        ));
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    public static void throwIfCancelled() {
        State state = CURRENT.get();
        if (state != null && state.cancellationRequested().getAsBoolean()) {
            throw new CancellationException("模型任务已取消，不再发送后续请求");
        }
    }

    public static void heartbeat() {
        State state = CURRENT.get();
        if (state != null) state.heartbeat().run();
    }

    private record State(BooleanSupplier cancellationRequested, Runnable heartbeat) {
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
