package io.smarthome.tinyrpc.codec;

import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JsonLineCodecTest {
    @Test
    public void roundTripsRequestAndRejectsOversizeFrame() {
        JsonLineCodec codec = new JsonLineCodec(new RpcLimits(512, 2, 2, 2));
        RpcRequest request = new RpcRequest("trace-001", "session-001", "request-001",
                "sensor.service", "read", "[]", System.currentTimeMillis() + 1000);
        byte[] frame = codec.encodeRequest(request);
        assertEquals('\n', frame[frame.length - 1]);
        assertEquals("request-001", codec.decodeRequest(frame).getRequestId());
        try {
            codec.encodeRequest(new RpcRequest("trace-002", "session-002", "request-002",
                    "sensor.service", "read", repeat('x', 600), System.currentTimeMillis() + 1000));
            throw new AssertionError("Expected FrameLimitException");
        } catch (FrameLimitException expected) {
            assertEquals(true, expected.getMessage().contains("maximum=512"));
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
