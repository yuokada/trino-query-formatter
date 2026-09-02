package io.github.yuokada.subcommand.util;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * Utility for processing directory entries in parallel while preserving input order.
 */
public final class DirectoryProcessor {

    private DirectoryProcessor() {
    }

    /**
     * Processes items with bounded parallelism and emits results in the same order as inputs.
     *
     * @param items input items to process
     * @param parallelism requested parallelism; values below 1 are treated as 1
     * @param task processor called for each item
     * @param consumer ordered result consumer
     * @param failureMessage message used when parallel execution fails
     * @param <T> input item type
     * @param <R> result type
     * @throws IOException when processing is interrupted or a task fails
     */
    public static <T, R> void processOrdered(
        List<T> items,
        int parallelism,
        ThrowingFunction<T, R> task,
        ThrowingConsumer<R> consumer,
        String failureMessage) throws IOException {
        int effectiveParallelism = Math.min(items.size(), Math.max(1, parallelism));
        if (effectiveParallelism <= 1) {
            for (T item : items) {
                consumer.accept(task.apply(item));
            }
            return;
        }

        ForkJoinPool pool = new ForkJoinPool(effectiveParallelism);
        try {
            ArrayDeque<Future<R>> pending = new ArrayDeque<>();
            int nextItem = 0;
            while (nextItem < items.size() && pending.size() < effectiveParallelism) {
                T item = items.get(nextItem++);
                pending.add(pool.submit(() -> task.apply(item)));
            }
            while (!pending.isEmpty()) {
                consumer.accept(pending.remove().get());
                if (nextItem < items.size()) {
                    T item = items.get(nextItem++);
                    pending.add(pool.submit(() -> task.apply(item)));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(failureMessage + " interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException(failureMessage + " failed", e.getCause());
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Calculates effective parallelism for a directory workload.
     *
     * @param itemCount number of input items
     * @param override optional override, usually provided by tests
     * @return effective parallelism capped by item count and at least 1 when items exist
     */
    public static int parallelism(int itemCount, Integer override) {
        if (itemCount <= 1) {
            return itemCount;
        }
        int processors = override == null ? Runtime.getRuntime().availableProcessors() : override;
        return Math.min(itemCount, Math.max(1, processors));
    }

    /**
     * Function variant that can throw {@link IOException}.
     *
     * @param <T> input type
     * @param <R> result type
     */
    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        /**
         * Applies this function.
         *
         * @param input input value
         * @return function result
         * @throws IOException when processing fails
         */
        R apply(T input) throws IOException;
    }

    /**
     * Consumer variant that can throw {@link IOException}.
     *
     * @param <T> input type
     */
    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        /**
         * Consumes a value.
         *
         * @param input input value
         * @throws IOException when consuming fails
         */
        void accept(T input) throws IOException;
    }
}
