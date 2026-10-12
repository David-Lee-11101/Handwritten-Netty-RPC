package io.smarthome.tinyrpc.telemetry;

public final class TransportObservation {
    private final String schemaVersion = "1.0.0";
    private final String traceId;
    private final String sessionId;
    private final String requestId;
    private final String direction;
    private final int encodedFrameLength;
    private final long monotonicOffsetNanos;
    private final String transport = "tiny-rpc-netty";
    private final String protocolVersion = "1.0";

    public TransportObservation(String traceId, String sessionId, String requestId, String direction,
                                int encodedFrameLength, long monotonicOffsetNanos) {
        this.traceId = traceId; this.sessionId = sessionId; this.requestId = requestId;
        this.direction = direction; this.encodedFrameLength = encodedFrameLength;
        this.monotonicOffsetNanos = monotonicOffsetNanos;
    }

    public String getSchemaVersion() { return schemaVersion; }
    public String getTraceId() { return traceId; }
    public String getSessionId() { return sessionId; }
    public String getRequestId() { return requestId; }
    public String getDirection() { return direction; }
    public int getEncodedFrameLength() { return encodedFrameLength; }
    public long getMonotonicOffsetNanos() { return monotonicOffsetNanos; }
    public String getTransport() { return transport; }
    public String getProtocolVersion() { return protocolVersion; }
}
