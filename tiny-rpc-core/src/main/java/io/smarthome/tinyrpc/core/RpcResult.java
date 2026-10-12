package io.smarthome.tinyrpc.core;

public class RpcResult {
    private static final java.util.regex.Pattern IDENTIFIER = java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9._:-]{2,127}");
    private String schemaVersion = "1.0.0";
    private String requestId;
    private String status;
    private String valueJson;
    private String errorCode;
    private String errorMessage;
    private long durationMs;

    public RpcResult() { }

    public static RpcResult success(String requestId, String valueJson, long durationMs) {
        RpcResult result = new RpcResult(); result.requestId = requestId; result.status = "success";
        result.valueJson = valueJson; result.durationMs = durationMs; return result;
    }

    public static RpcResult failure(String requestId, String status, String errorCode, String errorMessage, long durationMs) {
        RpcResult result = new RpcResult(); result.requestId = requestId; result.status = status;
        result.errorCode = errorCode; result.errorMessage = bounded(errorMessage); result.durationMs = durationMs; return result;
    }

    private static String bounded(String value) {
        if (value == null) return "";
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    public void validate() {
        if (!"1.0.0".equals(schemaVersion)) throw new IllegalArgumentException("Unsupported schemaVersion");
        if (requestId == null || !IDENTIFIER.matcher(requestId).matches())
            throw new IllegalArgumentException("requestId must be an opaque contract identifier");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs must be non-negative");
        if ("success".equals(status)) {
            if (valueJson == null || errorCode != null || errorMessage != null)
                throw new IllegalArgumentException("Successful result must contain only a value");
            return;
        }
        if (!("error".equals(status) || "timeout".equals(status) || "cancelled".equals(status)))
            throw new IllegalArgumentException("Unsupported result status");
        if (valueJson != null || errorCode == null || errorCode.trim().isEmpty() || errorMessage == null || errorMessage.length() > 512)
            throw new IllegalArgumentException("Failed result must contain a bounded structured error");
    }

    public boolean isSuccess() { return "success".equals(status); }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getValueJson() { return valueJson; }
    public void setValueJson(String valueJson) { this.valueJson = valueJson; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
