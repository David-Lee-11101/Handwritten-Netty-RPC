package com.seclab.nettyClient;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientPromiseTest {
    private DefaultEventExecutor executor;

    @Before
    public void setUp() {
        executor = new DefaultEventExecutor();
    }

    @After
    public void tearDown() {
        ClientPromise.failAll(new IllegalStateException("test cleanup"));
        executor.shutdownGracefully().syncUninterruptibly();
        assertEquals(0, ClientPromise.pendingCount());
    }

    @Test
    public void idsAreUniqueAndCorrelationsAreRemovedOnSuccess() throws Exception {
        Set<String> ids = java.util.Collections.synchronizedSet(new HashSet<>());
        int threadCount = 8;
        int idsPerThread = 250;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        for (int thread = 0; thread < threadCount; thread++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < idsPerThread; i++) {
                        ids.add(ClientPromise.nextRequestId());
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertEquals(threadCount * idsPerThread, ids.size());

        String requestId = ClientPromise.nextRequestId();
        Promise<String> promise = new DefaultPromise<>(executor);
        ClientPromise.register(requestId, promise);
        assertTrue(ClientPromise.succeed(requestId, "done"));
        assertEquals("done", promise.get(1, TimeUnit.SECONDS));
        assertFalse(ClientPromise.succeed(requestId, "again"));
    }

    @Test
    public void cancellationAndChannelCloseClearCorrelations() {
        Promise<String> cancelled = new DefaultPromise<>(executor);
        ClientPromise.register(ClientPromise.nextRequestId(), cancelled);
        assertTrue(cancelled.cancel(false));
        executor.submit(() -> { }).syncUninterruptibly();
        assertEquals(0, ClientPromise.pendingCount());

        Promise<String> closed = new DefaultPromise<>(executor);
        ClientPromise.register(ClientPromise.nextRequestId(), closed);
        ClientPromise.failAll(new ClosedChannelException());
        assertTrue(closed.cause() instanceof ClosedChannelException);
        assertEquals(0, ClientPromise.pendingCount());
    }
}
