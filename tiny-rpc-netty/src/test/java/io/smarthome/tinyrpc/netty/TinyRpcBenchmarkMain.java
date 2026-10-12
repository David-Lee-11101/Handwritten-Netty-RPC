package io.smarthome.tinyrpc.netty;

import io.netty.util.CharsetUtil;
import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Matched-workload process benchmark for the governed TinyRPC runtime. */
public final class TinyRpcBenchmarkMain {
    private static final int WARMUP_CALLS = 200;
    private static final int MEASURED_CALLS = 1000;

    private TinyRpcBenchmarkMain() { }

    public static void main(String[] args) throws Exception {
        long processStart = System.nanoTime();
        RpcLimits limits = RpcLimits.defaults();
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits)
                .register("benchmark.echo", "echo", arguments -> arguments)
                .build().bind("127.0.0.1", 0);
             TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", server.port(), limits, null)) {
            RpcResult first = invoke(client, 0, "ready");
            if (!first.isSuccess() || !"\"ready\"".equals(first.getValueJson())) {
                throw new IllegalStateException("Unexpected first response");
            }
            long startupNanos = System.nanoTime() - processStart;
            for (int index = 0; index < WARMUP_CALLS; index++) invoke(client, index + 1, "warmup-" + index);
            System.gc(); Thread.sleep(200);
            long heapBytes = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long rssBytes = readRssBytes();

            List<Long> latencies = new ArrayList<>(MEASURED_CALLS);
            long measuredStart = System.nanoTime();
            for (int index = 0; index < MEASURED_CALLS; index++) {
                long started = System.nanoTime();
                RpcResult result = invoke(client, index + WARMUP_CALLS + 1, "value-" + index);
                if (!result.isSuccess()) throw new IllegalStateException("Invocation failed: " + result.getErrorCode());
                latencies.add(System.nanoTime() - started);
            }
            long measuredNanos = System.nanoTime() - measuredStart;
            Collections.sort(latencies);
            System.out.println("BENCHMARK_JSON={"
                    + "\"schemaVersion\":\"1.0.0\","
                    + "\"pid\":" + currentPid() + ','
                    + "\"startupNanos\":" + startupNanos + ','
                    + "\"idleHeapBytes\":" + heapBytes + ','
                    + "\"idleRssBytes\":" + rssBytes + ','
                    + "\"measuredCalls\":" + MEASURED_CALLS + ','
                    + "\"p50LatencyNanos\":" + percentile(latencies, 0.50) + ','
                    + "\"p95LatencyNanos\":" + percentile(latencies, 0.95) + ','
                    + "\"p99LatencyNanos\":" + percentile(latencies, 0.99) + ','
                    + "\"throughputPerSecond\":"
                    + String.format(Locale.ROOT, "%.3f", MEASURED_CALLS * 1_000_000_000.0 / measuredNanos)
                    + '}');
        }
    }

    private static RpcResult invoke(TinyRpcClient client, int sequence, String value) throws Exception {
        String suffix = String.format(Locale.ROOT, "%06d", sequence);
        RpcRequest request = new RpcRequest("trace-bench-" + suffix, "session-benchmark", "request-bench-" + suffix,
                "benchmark.echo", "echo", "\"" + value + "\"", System.currentTimeMillis() + 2000);
        return client.invoke(request).get(2, TimeUnit.SECONDS);
    }

    private static long percentile(List<Long> values, double percentile) {
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private static long readRssBytes() throws Exception {
        Process process = new ProcessBuilder("ps", "-o", "rss=", "-p", Long.toString(currentPid())).start();
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), CharsetUtil.UTF_8));
        String value = reader.readLine();
        if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0 || value == null) {
            throw new IllegalStateException("Unable to read process RSS");
        }
        return Long.parseLong(value.trim()) * 1024L;
    }

    private static long currentPid() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return Long.parseLong(runtimeName.substring(0, runtimeName.indexOf('@')));
    }
}
