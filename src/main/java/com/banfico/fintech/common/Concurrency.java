package com.banfico.fintech.common;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Fans a list out across virtual threads and joins the results back in the original order.
 * Used to parallelize per-account sandbox calls (balances, transactions) instead of fetching
 * them one at a time.
 */
public final class Concurrency {

    private Concurrency() {
    }

    public static <T, R> List<R> mapConcurrently(List<T> items, Function<T, R> mapper) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> futures = items.stream()
                    .map(item -> executor.submit(() -> mapper.apply(item)))
                    .toList();
            return futures.stream().map(Concurrency::join).toList();
        }
    }

    private static <R> R join(Future<R> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }
}
