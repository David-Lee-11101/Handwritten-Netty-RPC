package com.seclab.nettyClient;

import com.alibaba.fastjson.JSON;
import com.seclab.model.RequestModel;
import io.netty.channel.Channel;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RpcClientProxyFactory {
    public static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 5000;

    private final String host;
    private final int port;
    private final long requestTimeoutMillis;

    public RpcClientProxyFactory(String host, int port) {
        this(host, port, DEFAULT_REQUEST_TIMEOUT_MILLIS);
    }

    public RpcClientProxyFactory(String host, int port, long requestTimeoutMillis) {
        if (requestTimeoutMillis <= 0) {
            throw new IllegalArgumentException("requestTimeoutMillis must be positive");
        }
        this.host = host;
        this.port = port;
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> serviceClass) {
        return (T) Proxy.newProxyInstance(serviceClass.getClassLoader(), new Class[]{serviceClass},
                (proxy, method, args) -> {
                    Channel channel = Client.getChannel(host, port);
                    String requestId = ClientPromise.nextRequestId();
                    RequestModel request = new RequestModel(
                            requestId,
                            method.getDeclaringClass().getName(),
                            method.getName(),
                            method.getParameterTypes(),
                            args);
                    Promise<String> promise = new DefaultPromise<>(channel.eventLoop());
                    ClientPromise.register(requestId, promise);
                    ScheduledFuture<?> timeout = channel.eventLoop().schedule(
                            () -> ClientPromise.fail(requestId,
                                    new TimeoutException("RPC request timed out after "
                                            + requestTimeoutMillis + " ms")),
                            requestTimeoutMillis,
                            TimeUnit.MILLISECONDS);
                    promise.addListener(done -> timeout.cancel(false));

                    channel.writeAndFlush(JSON.toJSONString(request) + "\n").addListener(write -> {
                        if (!write.isSuccess()) {
                            ClientPromise.fail(requestId, write.cause());
                        }
                    });

                    try {
                        promise.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        ClientPromise.fail(requestId, interrupted);
                        throw interrupted;
                    } finally {
                        ClientPromise.remove(requestId, promise);
                    }

                    if (promise.isSuccess()) {
                        return JSON.parseObject(promise.getNow(), method.getReturnType());
                    }
                    throw promise.cause();
                });
    }
}
