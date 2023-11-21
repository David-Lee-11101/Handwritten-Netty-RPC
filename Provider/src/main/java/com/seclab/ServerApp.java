package com.seclab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ServerApp {
    public static void main(String[] args) {

        ConfigurableApplicationContext context = SpringApplication.run(ServerApp.class, args);
        new Thread((Runnable) context.getBean("server")).start();
    }
}
