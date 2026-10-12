package io.smarthome.tinyrpc.netty;

import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;
import io.smarthome.tinyrpc.telemetry.TransportObservation;
import org.junit.Test;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TinyRpcLoopbackTest {
    @Test
    public void realLoopbackIsBoundedAllowlistedIsolatedAndPayloadFree() throws Exception {
        RpcLimits limits = new RpcLimits(1024, 2, 2, 2);
        List<TransportObservation> observations = new CopyOnWriteArrayList<>();
        AtomicReference<String> dispatchThread = new AtomicReference<>();
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits)
                .register("sensor.service", "read", arguments -> {
                    dispatchThread.set(Thread.currentThread().getName());
                    return "{\"type\":\"number\",\"value\":21.5}";
                })
                .build().bind("127.0.0.1", 0)) {
            sendMalformedPeer(server.port());
            try (TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", server.port(), limits, observations::add)) {
                RpcResult result = client.invoke(request("request-001", "sensor.service", "read", 1000))
                        .get(2, TimeUnit.SECONDS);
                assertTrue(result.isSuccess());
                assertEquals("{\"type\":\"number\",\"value\":21.5}", result.getValueJson());
                RpcResult denied = client.invoke(request("request-002", "sensor.service", "classForName", 1000))
                        .get(2, TimeUnit.SECONDS);
                assertEquals("METHOD_NOT_ALLOWED", denied.getErrorCode());
                assertEquals(0, client.pendingCount());
                assertEquals(0, client.queuedWriteCount());
            }
        }
        assertTrue(!dispatchThread.get().contains("nioEventLoop"));
        assertEquals(4, observations.size());
        assertEquals(2, observations.stream().filter(value -> "client-to-server".equals(value.getDirection())).count());
        assertEquals(2, observations.stream().filter(value -> "server-to-client".equals(value.getDirection())).count());
        for (int index = 1; index < observations.size(); index++)
            assertTrue(observations.get(index).getMonotonicOffsetNanos() >= observations.get(index - 1).getMonotonicOffsetNanos());
        observations.forEach(observation -> {
            assertEquals("tiny-rpc-netty", observation.getTransport());
            assertTrue(observation.getEncodedFrameLength() > 0);
            assertTrue(observation.getMonotonicOffsetNanos() >= 0);
        });
    }

    @Test public void telemetryFailureDoesNotChangeRpcOutcomeOrLeakPendingCalls() throws Exception {
        RpcLimits limits = new RpcLimits(1024, 2, 2, 2);
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits)
                .register("sensor.service", "read", arguments -> "true").build().bind("127.0.0.1", 0);
             TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", server.port(), limits,
                     observation -> { throw new IllegalStateException("observer failed"); })) {
            assertTrue(client.invoke(request("request-201", "sensor.service", "read", 1000)).get(2, TimeUnit.SECONDS).isSuccess());
            assertEquals(0, client.pendingCount()); assertEquals(0, client.queuedWriteCount());
        }
    }

    @Test public void failedConnectReleasesOwnedEventLoop() throws Exception {
        java.net.ServerSocket reservation = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        int unavailablePort = reservation.getLocalPort(); reservation.close();
        java.util.Set<Long> before = nettyThreadIds();
        try {
            TinyRpcClient.connect("127.0.0.1", unavailablePort, RpcLimits.defaults(), null);
            throw new AssertionError("Expected connection failure");
        } catch (IllegalStateException expected) { }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!newThreads(before).isEmpty() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue("Failed connection leaked event-loop threads: " + newThreads(before), newThreads(before).isEmpty());
    }

    private static java.util.Set<Long> nettyThreadIds() {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) if (thread.isAlive() && thread.getName().contains("nioEventLoopGroup")) ids.add(thread.getId());
        return ids;
    }
    private static java.util.Set<Long> newThreads(java.util.Set<Long> before) {
        java.util.Set<Long> ids = nettyThreadIds(); ids.removeAll(before); return ids;
    }

    @Test
    public void pendingLimitAndDeadlineHaveDefinedFailures() throws Exception {
        RpcLimits limits = new RpcLimits(1024, 1, 1, 1);
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits)
                .register("slow.service", "wait", arguments -> { Thread.sleep(250); return "true"; })
                .build().bind("127.0.0.1", 0);
             TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", server.port(), limits, null)) {
            java.util.concurrent.CompletableFuture<RpcResult> first = client.invoke(request("request-101", "slow.service", "wait", 1000));
            try {
                client.invoke(request("request-102", "slow.service", "wait", 1000)).get(1, TimeUnit.SECONDS);
                throw new AssertionError("Expected pending limit failure");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof java.util.concurrent.RejectedExecutionException);
            }
            assertTrue(first.get(2, TimeUnit.SECONDS).isSuccess());
            java.util.concurrent.CompletableFuture<RpcResult> cancelled =
                    client.invoke(request("request-104", "slow.service", "wait", 1000));
            assertTrue(cancelled.cancel(true));
            assertEquals(0, client.pendingCount());
            try {
                client.invoke(request("request-103", "slow.service", "wait", 100)).get(1, TimeUnit.SECONDS);
                throw new AssertionError("Expected deadline failure");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof java.util.concurrent.TimeoutException);
            }
        }
    }

    private static RpcRequest request(String id, String service, String operation, long timeoutMs) {
        return new RpcRequest("trace-001", "session-001", id, service, operation, "[]",
                System.currentTimeMillis() + timeoutMs);
    }

    private static void sendMalformedPeer(int port) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream output = socket.getOutputStream();
            output.write("not-json\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }
}
