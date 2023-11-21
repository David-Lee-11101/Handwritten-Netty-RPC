package com.seclab;

import com.alibaba.fastjson.JSON;
import com.seclab.model.RequestModel;
import com.seclab.nettyClient.Client;
import com.seclab.nettyClient.ClientPromise;
import com.seclab.pojo.User;
import com.seclab.service.UserService;
import io.netty.channel.Channel;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Proxy;
import java.util.Map;

@RestController
@SpringBootApplication
public class ClientApp {


    @Value("${host}")
    private String host;
    @Value("${port}")
    private int port;

    private static final Logger logger = LoggerFactory.getLogger(ClientApp.class);

    public static void main(String[] args) {
        SpringApplication.run(ClientApp.class, args);
    }

    @SuppressWarnings(value = "unchecked")
    private <T> T createServiceProxy(Class<T> serviceClass) {
        return (T) Proxy.newProxyInstance(serviceClass.getClassLoader(), new Class[]{serviceClass}, (proxy, method, args) -> {
            Channel channel = Client.getChannel(host, port);
            RequestModel requestModel = new RequestModel("100001", method.getDeclaringClass().getName(), method.getName(), method.getParameterTypes(), args);
            channel.writeAndFlush(JSON.toJSONString(requestModel) + "\n");

            Promise promise = new DefaultPromise(channel.eventLoop());
            ClientPromise.PROMISE_MAP.put(requestModel.getRequestId(), promise);

            logger.info("客户端请求发送: {}", requestModel);

            promise.await();

            if (promise.isSuccess()) {
                Class<?> returnType = method.getReturnType();
                return JSON.toJavaObject(JSON.parseObject(promise.getNow() + ""), returnType);
            } else {
                logger.error("客户端RPC调用失败", promise.cause());
                return promise.cause();
            }
        });
    }


    @GetMapping("getUser")
    public String getUser(@RequestParam(name = "id") String userId) {
        UserService userService = createServiceProxy(UserService.class);
        return userService.getUser(userId);
    }

    @GetMapping("getUsers")
    public Map<String, User> getUsers() {
        UserService userService = createServiceProxy(UserService.class);
        return userService.getUser();
    }

}
