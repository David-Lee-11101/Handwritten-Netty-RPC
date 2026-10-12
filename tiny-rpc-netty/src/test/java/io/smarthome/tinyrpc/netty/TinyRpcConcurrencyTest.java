package io.smarthome.tinyrpc.netty;

import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TinyRpcConcurrencyTest {
    @Test
    public void handlesTwoThousandSequentialAndTwentyFourConcurrentCalls() throws Exception {
        RpcLimits limits = new RpcLimits(4096, 64, 64, 64);
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits)
                .register("echo.service", "echo", arguments -> arguments)
                .build().bind("127.0.0.1", 0);
             TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", server.port(), limits, null)) {
            for (int index = 0; index < 2000; index++) {
                String value = "\"sequential-" + index + "\"";
                assertEquals(value, invoke(client, "sequential-" + index, value).getValueJson());
            }
            ExecutorService callers = Executors.newFixedThreadPool(8);
            try {
                List<Future<RpcResult>> results = new ArrayList<>();
                for (int index = 0; index < 24; index++) {
                    final int sequence = index;
                    results.add(callers.submit(() -> invoke(client, "concurrent-" + sequence,
                            "\"concurrent-" + sequence + "\"")));
                }
                for (int index = 0; index < results.size(); index++) {
                    assertEquals("\"concurrent-" + index + "\"", results.get(index).get(3, TimeUnit.SECONDS).getValueJson());
                }
            } finally {
                callers.shutdownNow(); callers.awaitTermination(2, TimeUnit.SECONDS);
            }
            assertEquals(0, client.pendingCount());
        }
    }

    @Test
    public void permitsResponseReorderingWithoutCorrelationLoss() throws Exception {
        RpcLimits limits = new RpcLimits(2048, 8, 8, 8);
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits)
                .register("order.service", "slow", arguments -> { Thread.sleep(150); return "\"slow\""; })
                .register("order.service", "fast", arguments -> "\"fast\"")
                .build().bind("127.0.0.1", 0);
             TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", server.port(), limits, null)) {
            CompletableFuture<RpcResult> slow = client.invoke(request("request-slow", "order.service", "slow", "[]", 1000));
            CompletableFuture<RpcResult> fast = client.invoke(request("request-fast", "order.service", "fast", "[]", 1000));
            assertEquals("\"fast\"", fast.get(1, TimeUnit.SECONDS).getValueJson());
            assertFalse(slow.isDone());
            assertEquals("\"slow\"", slow.get(1, TimeUnit.SECONDS).getValueJson());
        }
    }

    private static RpcResult invoke(TinyRpcClient client, String suffix, String arguments) throws Exception {
        return client.invoke(request("request-" + suffix, "echo.service", "echo", arguments, 2000))
                .get(3, TimeUnit.SECONDS);
    }

    private static RpcRequest request(String requestId, String service, String operation, String arguments, long timeoutMs) {
        return new RpcRequest("trace-" + requestId, "session-concurrency", requestId, service, operation,
                arguments, System.currentTimeMillis() + timeoutMs);
    }
}
