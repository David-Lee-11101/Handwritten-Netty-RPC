package io.smarthome.tinyrpc.core;

@FunctionalInterface
public interface RpcHandler {
    String handle(String argumentsJson) throws Exception;
}
