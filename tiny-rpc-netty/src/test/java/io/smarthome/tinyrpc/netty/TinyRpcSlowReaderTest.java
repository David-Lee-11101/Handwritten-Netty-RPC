package io.smarthome.tinyrpc.netty;

import io.smarthome.tinyrpc.codec.JsonLineCodec;
import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import org.junit.Test;
import java.net.Socket;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class TinyRpcSlowReaderTest {
    @Test public void nonReadingPeerCannotGrowOutboundQueueAndOtherPeerStillInvokes() throws Exception {
        RpcLimits limits = new RpcLimits(64 * 1024, 16, 2, 256);
        StringBuilder response = new StringBuilder("\"");
        for (int i = 0; i < 60000; i++) response.append('x');
        response.append('"');
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits)
                .register("reader.service", "large", arguments -> response.toString())
                .register("reader.service", "ping", arguments -> "true").build().bind("127.0.0.1", 0);
             Socket slow = new Socket("127.0.0.1", server.port())) {
            slow.setReceiveBufferSize(1024);
            JsonLineCodec codec = new JsonLineCodec(limits);
            OutputStream output = slow.getOutputStream();
            try {
                for (int i = 0; i < 300; i++) output.write(codec.encodeRequest(new RpcRequest("trace.slow." + i,
                        "session.slow", "request.slow." + i, "reader.service", "large", "[]", System.currentTimeMillis() + 5000)));
                output.flush();
            } catch (java.io.IOException peerClosed) { /* A bounded peer may close while the sender is still writing. */ }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((server.rejectedWriteCount() == 0 || server.pendingWriteCount() != 0) && System.nanoTime() < deadline) {
                assertTrue(server.pendingWriteCount() <= limits.getMaxQueuedWrites());
                assertTrue(server.pendingWriteBytes() <= (long) limits.getMaxQueuedWrites() * limits.getMaxFrameBytes());
                Thread.sleep(1);
            }
            assertTrue("Slow reader did not exercise bounded rejection", server.rejectedWriteCount() > 0);
            assertEquals(0, server.pendingWriteCount());
            assertEquals(0, server.pendingWriteBytes());
            try (TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", server.port(), limits, null)) {
                assertTrue(client.invoke(new RpcRequest("trace.ping", "session.ping", "request.ping", "reader.service", "ping", "[]",
                        System.currentTimeMillis() + 5000)).get(5, TimeUnit.SECONDS).isSuccess());
            }
        }
    }
}
