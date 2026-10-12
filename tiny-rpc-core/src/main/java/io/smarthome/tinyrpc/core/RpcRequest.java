package io.smarthome.tinyrpc.core;

public class RpcRequest {
    private String schemaVersion = "1.0.0";
    private String traceId;
    private String sessionId;
    private String requestId;
    private String serviceId;
    private String operation;
    private String argumentsJson;
    private long deadlineEpochMs;

    public RpcRequest() { }

    public RpcRequest(String traceId, String sessionId, String requestId, String serviceId,
                      String operation, String argumentsJson, long deadlineEpochMs) {
        this.traceId = requireId(traceId, "traceId");
        this.sessionId = requireId(sessionId, "sessionId");
        this.requestId = requireId(requestId, "requestId");
        this.serviceId = requireId(serviceId, "serviceId");
        this.operation = requireId(operation, "operation");
        this.argumentsJson = argumentsJson == null ? "[]" : argumentsJson;
        this.deadlineEpochMs = deadlineEpochMs;
    }

    public void validate(long nowEpochMs) {
        if (!"1.0.0".equals(schemaVersion)) throw new IllegalArgumentException("Unsupported schemaVersion");
        requireId(traceId, "traceId"); requireId(sessionId, "sessionId"); requireId(requestId, "requestId");
        requireId(serviceId, "serviceId"); requireId(operation, "operation");
        if (deadlineEpochMs <= nowEpochMs) throw new IllegalArgumentException("Request deadline has expired");
        if (argumentsJson == null) throw new IllegalArgumentException("argumentsJson is required");
    }

    private static String requireId(String value, String name) {
        if (value == null || !value.matches("[a-zA-Z][a-zA-Z0-9._:-]{2,127}")) {
            throw new IllegalArgumentException(name + " must be an opaque contract identifier");
        }
        return value;
    }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getArgumentsJson() { return argumentsJson; }
    public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
    public long getDeadlineEpochMs() { return deadlineEpochMs; }
    public void setDeadlineEpochMs(long deadlineEpochMs) { this.deadlineEpochMs = deadlineEpochMs; }
}
