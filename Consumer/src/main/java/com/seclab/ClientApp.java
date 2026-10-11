package com.seclab;

import com.seclab.nettyClient.Client;
import com.seclab.nettyClient.RpcClientProxyFactory;
import com.seclab.pojo.User;
import com.seclab.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import javax.annotation.PreDestroy;

@RestController
@SpringBootApplication
public class ClientApp {


    @Value("${host}")
    private String host;
    @Value("${port}")
    private int port;

    public static void main(String[] args) {
        SpringApplication.run(ClientApp.class, args);
    }

    private <T> T createServiceProxy(Class<T> serviceClass) {
        return new RpcClientProxyFactory(host, port).create(serviceClass);
    }

    @PreDestroy
    public void shutdownRpcClient() {
        Client.shutdown();
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
