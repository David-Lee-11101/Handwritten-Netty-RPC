package io.smarthome.tinyrpc.codec;

import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict codec for the flat, versioned TinyRPC JSON-line envelope. */
public final class JsonLineCodec {
    private final int maxFrameBytes;

    public JsonLineCodec(RpcLimits limits) {
        this.maxFrameBytes = limits.getMaxFrameBytes();
    }

    public byte[] encodeRequest(RpcRequest request) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("schemaVersion", request.getSchemaVersion()); object.put("traceId", request.getTraceId());
        object.put("sessionId", request.getSessionId()); object.put("requestId", request.getRequestId());
        object.put("serviceId", request.getServiceId()); object.put("operation", request.getOperation());
        object.put("argumentsJson", request.getArgumentsJson()); object.put("deadlineEpochMs", request.getDeadlineEpochMs());
        return encode(FlatJsonObject.write(object));
    }

    public byte[] encodeResult(RpcResult result) {
        result.validate();
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("schemaVersion", result.getSchemaVersion()); object.put("requestId", result.getRequestId());
        object.put("status", result.getStatus());
        if (result.getValueJson() != null) object.put("valueJson", result.getValueJson());
        if (result.getErrorCode() != null) object.put("errorCode", result.getErrorCode());
        if (result.getErrorMessage() != null) object.put("errorMessage", result.getErrorMessage());
        object.put("durationMs", result.getDurationMs());
        return encode(FlatJsonObject.write(object));
    }

    public RpcRequest decodeRequest(byte[] frame) {
        Map<String, Object> object = parseStrictObject(frame);
        requireKnownKeys(object, "schemaVersion", "traceId", "sessionId", "requestId", "serviceId", "operation", "argumentsJson", "deadlineEpochMs");
        RpcRequest request = new RpcRequest();
        request.setSchemaVersion(requiredString(object, "schemaVersion"));
        request.setTraceId(requiredString(object, "traceId")); request.setSessionId(requiredString(object, "sessionId"));
        request.setRequestId(requiredString(object, "requestId")); request.setServiceId(requiredString(object, "serviceId"));
        request.setOperation(requiredString(object, "operation")); request.setArgumentsJson(requiredString(object, "argumentsJson"));
        request.setDeadlineEpochMs(requiredLong(object, "deadlineEpochMs"));
        return request;
    }

    public RpcResult decodeResult(byte[] frame) {
        Map<String, Object> object = parseStrictObject(frame);
        requireKnownKeys(object, "schemaVersion", "requestId", "status", "valueJson", "errorCode", "errorMessage", "durationMs");
        RpcResult result = new RpcResult();
        result.setSchemaVersion(requiredString(object, "schemaVersion")); result.setRequestId(requiredString(object, "requestId"));
        result.setStatus(requiredString(object, "status")); result.setDurationMs(requiredLong(object, "durationMs"));
        if (object.containsKey("valueJson")) result.setValueJson(requiredString(object, "valueJson"));
        if (object.containsKey("errorCode")) result.setErrorCode(requiredString(object, "errorCode"));
        if (object.containsKey("errorMessage")) result.setErrorMessage(requiredString(object, "errorMessage"));
        result.validate();
        return result;
    }

    private Map<String, Object> parseStrictObject(byte[] frame) {
        return FlatJsonObject.parse(decode(frame));
    }

    private static void requireKnownKeys(Map<String, Object> object, String... allowed) {
        java.util.HashSet<String> keys = new java.util.HashSet<>(); java.util.Collections.addAll(keys, allowed);
        for (String key : object.keySet()) if (!keys.contains(key)) throw new IllegalArgumentException("Unknown RPC field: " + key);
    }

    private static String requiredString(Map<String, Object> object, String key) {
        Object value = object.get(key); if (!(value instanceof String)) throw new IllegalArgumentException(key + " must be a string"); return (String) value;
    }

    private static long requiredLong(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Long)) throw new IllegalArgumentException(key + " must be an integer");
        return (Long) value;
    }

    private byte[] encode(String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        if (payload.length + 1 > maxFrameBytes) throw new FrameLimitException(maxFrameBytes, payload.length + 1);
        byte[] frame = new byte[payload.length + 1];
        System.arraycopy(payload, 0, frame, 0, payload.length);
        frame[frame.length - 1] = '\n';
        return frame;
    }

    private String decode(byte[] frame) {
        if (frame == null || frame.length == 0 || frame.length > maxFrameBytes) {
            throw new FrameLimitException(maxFrameBytes, frame == null ? 0 : frame.length);
        }
        int length = frame[frame.length - 1] == '\n' ? frame.length - 1 : frame.length;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(frame, 0, length)).toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new IllegalArgumentException("RPC frame must be valid UTF-8", invalidUtf8);
        }
    }
}
