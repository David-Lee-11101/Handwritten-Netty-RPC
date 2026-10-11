package com.seclab.nettyClient;

import io.netty.util.concurrent.Promise;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ClientPromise {
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
    private static final ConcurrentHashMap<String, Promise<String>> PROMISES = new ConcurrentHashMap<>();

    private ClientPromise() {
    }

    public static String nextRequestId() {
        return Long.toUnsignedString(REQUEST_SEQUENCE.incrementAndGet());
    }

    public static void register(String requestId, Promise<String> promise) {
        Promise<String> previous = PROMISES.putIfAbsent(requestId, promise);
        if (previous != null) {
            throw new IllegalStateException("Duplicate request ID: " + requestId);
        }
        promise.addListener(done -> {
            if (done.isCancelled()) {
                PROMISES.remove(requestId, promise);
            }
        });
    }

    public static boolean succeed(String requestId, String value) {
        Promise<String> promise = PROMISES.remove(requestId);
        return promise != null && promise.trySuccess(value);
    }

    public static boolean fail(String requestId, Throwable cause) {
        Promise<String> promise = PROMISES.remove(requestId);
        return promise != null && promise.tryFailure(cause);
    }

    public static void failAll(Throwable cause) {
        PROMISES.forEach((requestId, promise) -> {
            if (PROMISES.remove(requestId, promise)) {
                promise.tryFailure(cause);
            }
        });
    }

    public static void remove(String requestId, Promise<String> promise) {
        PROMISES.remove(requestId, promise);
    }

    public static int pendingCount() {
        return PROMISES.size();
    }
}
