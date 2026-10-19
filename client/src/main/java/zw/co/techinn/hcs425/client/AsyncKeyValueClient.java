package zw.co.techinn.hcs425.client;

import zw.co.techinn.hcs425.shared.Response;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Non-blocking facade used by the JavaFX application. */
public final class AsyncKeyValueClient implements AutoCloseable {
    private final KeyValueClient delegate;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "hcs425-client-io");
        thread.setDaemon(true);
        return thread;
    });

    public AsyncKeyValueClient(KeyValueClient delegate) { this.delegate = delegate; }
    public CompletableFuture<Response> put(String key, String value) { return submit(() -> delegate.put(key, value)); }
    public CompletableFuture<Response> get(String key) { return submit(() -> delegate.get(key)); }
    public CompletableFuture<Response> delete(String key) { return submit(() -> delegate.delete(key)); }
    public CompletableFuture<Response> list() { return submit(delegate::list); }
    public CompletableFuture<Response> stats() { return submit(delegate::stats); }

    private CompletableFuture<Response> submit(CheckedSupplier<Response> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try { return supplier.get(); }
            catch (ClientException ex) { throw new java.util.concurrent.CompletionException(ex); }
        }, executor);
    }

    @Override public void close() { executor.shutdownNow(); delegate.close(); }
    @FunctionalInterface private interface CheckedSupplier<T> { T get() throws ClientException; }
}
