package com.projectflow.service;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** 将持久化 Job 的取消状态传入网关，确保恢复请求前也能停止。 */
public final class ModelCancellationContext {
    private static final ThreadLocal<BooleanSupplier> CURRENT = new ThreadLocal<>();

    private ModelCancellationContext() {
    }

    public static Scope bind(BooleanSupplier cancellationRequested) {
        BooleanSupplier previous = CURRENT.get();
        CURRENT.set(cancellationRequested);
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    public static void throwIfCancelled() {
        BooleanSupplier supplier = CURRENT.get();
        if (supplier != null && supplier.getAsBoolean()) {
            throw new CancellationException("模型任务已取消，不再发送后续请求");
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
