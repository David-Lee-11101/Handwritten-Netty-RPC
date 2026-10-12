package io.smarthome.tinyrpc.netty;

import io.smarthome.tinyrpc.codec.JsonLineCodec;
import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import org.junit.Test;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class TinyRpcWireFrameLimitTest {
    @Test public void clientCountsFullDelimitersAndRejectsOversizedReply() throws Exception {
        RpcLimits limits = new RpcLimits(1024, 8, 8, 8);
        JsonLineCodec codec = new JsonLineCodec(limits);
        io.smarthome.tinyrpc.core.RpcResult result = io.smarthome.tinyrpc.core.RpcResult.success("request.client.frame", "", 0);
        int base = codec.encodeResult(result).length;
        for (int mode = 0; mode < 3; mode++) {
            char[] chars = new char[1024 - base - (mode == 1 ? 1 : 0)]; Arrays.fill(chars, 'x'); result.setValueJson(new String(chars));
            byte[] bytes = codec.encodeResult(result); final byte[] reply = mode == 0 ? bytes : crlf(bytes);
            java.util.concurrent.CompletableFuture<Void> peer = new java.util.concurrent.CompletableFuture<>();
            java.util.List<io.smarthome.tinyrpc.telemetry.TransportObservation> observed = new java.util.concurrent.CopyOnWriteArrayList<>();
            try (java.net.ServerSocket listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
                Thread responder = new Thread(() -> {
                    try (Socket socket = listener.accept()) {
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
                        socket.getOutputStream().write(reply); socket.getOutputStream().flush(); peer.complete(null);
                    } catch (Exception failure) { peer.completeExceptionally(failure); }
                }, "frame-limit-test-peer");
                responder.start();
                try (TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", listener.getLocalPort(), limits, observed::add)) {
                    java.util.concurrent.CompletableFuture<io.smarthome.tinyrpc.core.RpcResult> invoked = client.invoke(new RpcRequest("trace.client", "session.client", "request.client.frame",
                            "frame.service", "read", "[]", System.currentTimeMillis() + 5000));
                    if (mode < 2) {
                        assertTrue(invoked.get(5, java.util.concurrent.TimeUnit.SECONDS).isSuccess());
                        assertEquals(1024, observed.get(1).getEncodedFrameLength());
                    } else {
                        try { invoked.get(5, java.util.concurrent.TimeUnit.SECONDS); fail("Expected oversized reply rejection"); }
                        catch (java.util.concurrent.ExecutionException expected) {
                            assertTrue(expected.getCause() instanceof io.smarthome.tinyrpc.codec.FrameLimitException);
                        }
                        assertEquals(0, client.pendingCount());
                    }
                } finally { peer.get(5, java.util.concurrent.TimeUnit.SECONDS); responder.join(5000); assertFalse(responder.isAlive()); }
            }
        }
    }
    @Test public void exactLfAndCrLfWireLimitsIncludeAllDelimiterBytes() throws Exception {
        RpcLimits limits = new RpcLimits(1024, 8, 8, 8);
        AtomicInteger calls = new AtomicInteger();
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits)
                .register("frame.service", "read", arguments -> { calls.incrementAndGet(); return "true"; })
                .build().bind("127.0.0.1", 0)) {
            byte[] maximumLf = frame(limits, 1024);
            assertEquals(1024, maximumLf.length); assertTrue(exchange(server.port(), maximumLf));
            byte[] validCrLf = crlf(frame(limits, 1023));
            assertEquals(1024, validCrLf.length); assertTrue(exchange(server.port(), validCrLf));
            byte[] oversizedCrLf = crlf(maximumLf);
            assertEquals(1025, oversizedCrLf.length); assertFalse(exchange(server.port(), oversizedCrLf));
            byte[] oversizedLf = new String(maximumLf, StandardCharsets.UTF_8)
                    .replace("\"argumentsJson\":\"", "\"argumentsJson\":\"x").getBytes(StandardCharsets.UTF_8);
            assertEquals(1025, oversizedLf.length); assertFalse(exchange(server.port(), oversizedLf));
            assertEquals(2, calls.get());
        }
    }
    private static byte[] frame(RpcLimits limits, int size) {
        JsonLineCodec codec = new JsonLineCodec(limits);
        RpcRequest request = new RpcRequest("trace.frame", "session.frame", "request.frame", "frame.service", "read", "", System.currentTimeMillis() + 5000);
        int padding = size - codec.encodeRequest(request).length;
        char[] chars = new char[padding]; Arrays.fill(chars, 'x'); request.setArgumentsJson(new String(chars));
        return codec.encodeRequest(request);
    }
    private static byte[] crlf(byte[] lf) {
        byte[] frame = Arrays.copyOf(lf, lf.length + 1); frame[frame.length - 2] = '\r'; frame[frame.length - 1] = '\n'; return frame;
    }
    private static boolean exchange(int port, byte[] bytes) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000); socket.getOutputStream().write(bytes); socket.getOutputStream().flush();
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine() != null;
        }
    }
}
