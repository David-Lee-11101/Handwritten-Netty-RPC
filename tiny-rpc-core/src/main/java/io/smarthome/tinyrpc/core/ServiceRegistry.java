package io.smarthome.tinyrpc.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceRegistry {
    private final Map<String, RpcHandler> handlers = new ConcurrentHashMap<>();

    public ServiceRegistry register(String serviceId, String operation, RpcHandler handler) {
        if (handler == null) throw new IllegalArgumentException("handler is required");
        String key = key(serviceId, operation);
        if (handlers.putIfAbsent(key, handler) != null) throw new IllegalStateException("Duplicate operation: " + key);
        return this;
    }

    public RpcHandler resolve(String serviceId, String operation) {
        return handlers.get(key(serviceId, operation));
    }

    private static String key(String serviceId, String operation) {
        if (serviceId == null || operation == null) throw new IllegalArgumentException("serviceId and operation are required");
        return serviceId + '#' + operation;
    }
}
