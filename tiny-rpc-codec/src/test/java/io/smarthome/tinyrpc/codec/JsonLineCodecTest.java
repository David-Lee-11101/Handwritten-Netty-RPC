package io.smarthome.tinyrpc.codec;

import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;
import com.alibaba.fastjson.parser.ParserConfig;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

    @Test public void usesLocalSafeParserAndRejectsSpecialOrUnknownKeys() {
        boolean globalSafeMode = ParserConfig.getGlobalInstance().isSafeMode();
        boolean globalAutoType = ParserConfig.getGlobalInstance().isAutoTypeSupport();
        JsonLineCodec codec = new JsonLineCodec(new RpcLimits(2048, 2, 2, 2));
        assertEquals(globalSafeMode, ParserConfig.getGlobalInstance().isSafeMode());
        assertEquals(globalAutoType, ParserConfig.getGlobalInstance().isAutoTypeSupport());
        expectInvalid(() -> codec.decodeRequest(frame("{\"schemaVersion\":\"1.0.0\",\"traceId\":\"trace-001\",\"sessionId\":\"session-001\",\"requestId\":\"request-001\",\"serviceId\":\"sensor.service\",\"operation\":\"read\",\"argumentsJson\":\"[]\",\"deadlineEpochMs\":9999999999999,\"unknown\":true}")));
        expectInvalid(() -> codec.decodeRequest(frame("{\"@type\":\"java.lang.Runtime\"}")));
        expectInvalid(() -> codec.decodeRequest(frame("{\"$ref\":\"$\"}")));
    }

    @Test public void validatesResultShapeAndDoesNotSerializeDerivedGetter() {
        JsonLineCodec codec = new JsonLineCodec(new RpcLimits(2048, 2, 2, 2));
        byte[] encoded = codec.encodeResult(RpcResult.success("request-001", "{\"type\":\"number\",\"value\":1}", 1));
        assertFalse(new String(encoded, StandardCharsets.UTF_8).contains("\"success\":"));
        assertEquals("request-001", codec.decodeResult(encoded).getRequestId());
        expectInvalid(() -> codec.decodeResult(frame("{\"schemaVersion\":\"9.0.0\",\"requestId\":\"request-001\",\"status\":\"success\",\"valueJson\":\"{}\",\"durationMs\":0}")));
        expectInvalid(() -> codec.decodeResult(frame("{\"schemaVersion\":\"1.0.0\",\"requestId\":\"request-001\",\"status\":\"success\",\"valueJson\":\"{}\",\"durationMs\":0,\"extra\":1}")));
    }

    private static byte[] frame(String json) { return (json + "\n").getBytes(StandardCharsets.UTF_8); }
    private static void expectInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid frame"); }
        catch (IllegalArgumentException expected) { }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
