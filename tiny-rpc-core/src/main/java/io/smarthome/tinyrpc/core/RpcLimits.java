package io.smarthome.tinyrpc.core;

public final class RpcLimits {
    public static final int DEFAULT_MAX_FRAME_BYTES = 1024 * 1024;
    public static final int DEFAULT_MAX_PENDING_REQUESTS = 256;
    public static final int DEFAULT_MAX_QUEUED_WRITES = 256;
    public static final int DEFAULT_MAX_SERVER_WORK_ITEMS = 256;

    private final int maxFrameBytes;
    private final int maxPendingRequests;
    private final int maxQueuedWrites;
    private final int maxServerWorkItems;

    public RpcLimits(int maxFrameBytes, int maxPendingRequests, int maxQueuedWrites, int maxServerWorkItems) {
        if (maxFrameBytes < 128 || maxPendingRequests < 1 || maxQueuedWrites < 1 || maxServerWorkItems < 1) {
            throw new IllegalArgumentException("All RPC limits must be positive and frame size must be at least 128 bytes");
        }
        this.maxFrameBytes = maxFrameBytes;
        this.maxPendingRequests = maxPendingRequests;
        this.maxQueuedWrites = maxQueuedWrites;
        this.maxServerWorkItems = maxServerWorkItems;
    }

    public static RpcLimits defaults() {
        return new RpcLimits(DEFAULT_MAX_FRAME_BYTES, DEFAULT_MAX_PENDING_REQUESTS,
                DEFAULT_MAX_QUEUED_WRITES, DEFAULT_MAX_SERVER_WORK_ITEMS);
    }

    public int getMaxFrameBytes() { return maxFrameBytes; }
    public int getMaxPendingRequests() { return maxPendingRequests; }
    public int getMaxQueuedWrites() { return maxQueuedWrites; }
    public int getMaxServerWorkItems() { return maxServerWorkItems; }
}
