package io.smarthome.tinyrpc.netty;

import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;
import org.junit.Test;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TinyRpcAdversarialTest {
    @Test
    public void isolatesPeersRejectsOversizeAndToleratesLateResponses() throws Exception {
        RpcLimits limits = new RpcLimits(512, 8, 8, 8);
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits)
                .register("guard.service", "echo", arguments -> arguments)
                .register("guard.service", "late", arguments -> { Thread.sleep(100); return arguments; })
                .build().bind("127.0.0.1", 0);
             TinyRpcClient first = TinyRpcClient.connect("127.0.0.1", server.port(), limits, null);
             TinyRpcClient second = TinyRpcClient.connect("127.0.0.1", server.port(), limits, null)) {
            sendRaw(server.port(), "not-json\n");
            try {
                first.invoke(request("request-big", "guard.service", "echo", largePayload(), 1000)).get(1, TimeUnit.SECONDS);
                throw new AssertionError("Expected frame bound failure");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof IllegalArgumentException);
            }
            first.close();
            assertEquals("\"peer-two\"", second.invoke(request("request-peer", "guard.service", "echo", "\"peer-two\"", 1000))
                    .get(1, TimeUnit.SECONDS).getValueJson());
            try {
                second.invoke(request("request-late", "guard.service", "late", "\"late\"", 10)).get(1, TimeUnit.SECONDS);
                throw new AssertionError("Expected timeout");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof java.util.concurrent.TimeoutException);
            }
            Thread.sleep(150);
            assertEquals(0, second.pendingCount());
            assertEquals("\"after-late\"", second.invoke(request("request-after", "guard.service", "echo", "\"after-late\"", 1000))
                    .get(1, TimeUnit.SECONDS).getValueJson());
            second.close();
            second.close();
        }
    }

    @Test
    public void nestedInvocationDoesNotBlockEventLoopAndOverloadIsStructured() throws Exception {
        RpcLimits innerLimits = new RpcLimits(1024, 8, 8, 8);
        try (TinyRpcServer inner = TinyRpcServer.builder().limits(innerLimits)
                .register("inner.service", "echo", arguments -> { Thread.sleep(80); return arguments; })
                .build().bind("127.0.0.1", 0);
             TinyRpcClient nestedClient = TinyRpcClient.connect("127.0.0.1", inner.port(), innerLimits, null)) {
            RpcLimits outerLimits = new RpcLimits(1024, 16, 16, 1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch entered = new CountDownLatch(2);
            try (TinyRpcServer outer = TinyRpcServer.builder().limits(outerLimits)
                    .register("outer.service", "nested", arguments -> nestedClient.invoke(
                            request("request-inner", "inner.service", "echo", arguments, 5000)).get(5, TimeUnit.SECONDS).getValueJson())
                    .register("outer.service", "ping", arguments -> "\"pong\"")
                    .register("outer.service", "block", arguments -> { entered.countDown(); release.await(10, TimeUnit.SECONDS); return arguments; })
                    .build().bind("127.0.0.1", 0);
                 TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", outer.port(), outerLimits, null)) {
                CompletableFuture<RpcResult> nested = client.invoke(request("request-nested", "outer.service", "nested", "\"nested\"", 5000));
                assertEquals("\"pong\"", client.invoke(request("request-ping", "outer.service", "ping", "[]", 5000))
                        .get(5, TimeUnit.SECONDS).getValueJson());
                assertEquals("\"nested\"", nested.get(5, TimeUnit.SECONDS).getValueJson());

                List<CompletableFuture<RpcResult>> blocked = new ArrayList<>();
                try {
                    for (int index = 0; index < 2; index++) {
                        blocked.add(client.invoke(request("request-block-" + index, "outer.service", "block", "[]", 5000)));
                        // Wait for each worker to start before filling the one-item queue.
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                        while (entered.getCount() > 1 - index && System.nanoTime() < deadline) Thread.sleep(1);
                        assertTrue("Dispatch worker did not start", entered.getCount() <= 1 - index);
                    }
                    blocked.add(client.invoke(request("request-block-2", "outer.service", "block", "[]", 5000)));
                    blocked.add(client.invoke(request("request-block-3", "outer.service", "block", "[]", 5000)));
                    assertEquals("SERVER_OVERLOADED", blocked.get(3).get(5, TimeUnit.SECONDS).getErrorCode());
                } finally { release.countDown(); }
                for (int index = 0; index < 3; index++) assertTrue(blocked.get(index).get(5, TimeUnit.SECONDS).isSuccess());
            }
        }
    }

    private static RpcRequest request(String id, String service, String operation, String arguments, long timeoutMs) {
        return new RpcRequest("trace-" + id, "session-adversarial", id, service, operation, arguments,
                System.currentTimeMillis() + timeoutMs);
    }

    private static String largePayload() {
        StringBuilder value = new StringBuilder("\"");
        for (int index = 0; index < 600; index++) value.append('x');
        return value.append('"').toString();
    }

    private static void sendRaw(int port, String value) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream output = socket.getOutputStream(); output.write(value.getBytes(StandardCharsets.UTF_8)); output.flush();
        }
    }
}
