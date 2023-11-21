package com.seclab.nettyClient;

import io.netty.util.concurrent.Promise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientPromise {
    public static Map<String, Promise<String>> PROMISE_MAP = new ConcurrentHashMap<String, Promise<String>>();
}
