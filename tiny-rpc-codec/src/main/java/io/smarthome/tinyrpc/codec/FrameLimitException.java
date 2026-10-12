package io.smarthome.tinyrpc.codec;

public final class FrameLimitException extends IllegalArgumentException {
    public FrameLimitException(int maximum, int actual) {
        super("Encoded frame exceeds limit: maximum=" + maximum + ", actual=" + actual);
    }
}
