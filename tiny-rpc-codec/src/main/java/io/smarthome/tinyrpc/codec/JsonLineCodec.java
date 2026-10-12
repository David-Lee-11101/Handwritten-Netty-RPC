package io.smarthome.tinyrpc.codec;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;

import java.nio.charset.StandardCharsets;

public final class JsonLineCodec {
    private final int maxFrameBytes;
    private final ParserConfig parserConfig;

    public JsonLineCodec(RpcLimits limits) {
        this.maxFrameBytes = limits.getMaxFrameBytes();
        this.parserConfig = new ParserConfig();
        this.parserConfig.setAutoTypeSupport(false);
        this.parserConfig.setSafeMode(true);
    }

    public byte[] encodeRequest(RpcRequest request) {
        JSONObject object = new JSONObject(true);
        object.put("schemaVersion", request.getSchemaVersion()); object.put("traceId", request.getTraceId());
        object.put("sessionId", request.getSessionId()); object.put("requestId", request.getRequestId());
        object.put("serviceId", request.getServiceId()); object.put("operation", request.getOperation());
        object.put("argumentsJson", request.getArgumentsJson()); object.put("deadlineEpochMs", request.getDeadlineEpochMs());
        return encode(object.toJSONString());
    }

    public byte[] encodeResult(RpcResult result) {
        result.validate();
        JSONObject object = new JSONObject(true);
        object.put("schemaVersion", result.getSchemaVersion()); object.put("requestId", result.getRequestId());
        object.put("status", result.getStatus());
        if (result.getValueJson() != null) object.put("valueJson", result.getValueJson());
        if (result.getErrorCode() != null) object.put("errorCode", result.getErrorCode());
        if (result.getErrorMessage() != null) object.put("errorMessage", result.getErrorMessage());
        object.put("durationMs", result.getDurationMs());
        return encode(object.toJSONString());
    }

    public RpcRequest decodeRequest(byte[] frame) {
        JSONObject object = parseStrictObject(frame);
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
        JSONObject object = parseStrictObject(frame);
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

    private JSONObject parseStrictObject(byte[] frame) {
        String json = decode(frame);
        if (json.contains("\"@type\"") || json.contains("\"$ref\"")) throw new IllegalArgumentException("Special JSON keys are forbidden");
        Object parsed = JSON.parseObject(json, Object.class, parserConfig, Feature.DisableSpecialKeyDetect);
        if (!(parsed instanceof JSONObject)) throw new IllegalArgumentException("RPC frame must contain one JSON object");
        return (JSONObject) parsed;
    }

    private static void requireKnownKeys(JSONObject object, String... allowed) {
        java.util.HashSet<String> keys = new java.util.HashSet<>(); java.util.Collections.addAll(keys, allowed);
        for (String key : object.keySet()) if (!keys.contains(key)) throw new IllegalArgumentException("Unknown RPC field: " + key);
    }
    private static String requiredString(JSONObject object, String key) {
        Object value = object.get(key); if (!(value instanceof String)) throw new IllegalArgumentException(key + " must be a string"); return (String) value;
    }
    private static long requiredLong(JSONObject object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long))
            throw new IllegalArgumentException(key + " must be an integer");
        return ((Number) value).longValue();
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
        return new String(frame, 0, length, StandardCharsets.UTF_8);
    }
}
