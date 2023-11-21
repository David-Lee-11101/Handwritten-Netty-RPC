package com.seclab.nettyServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Configuration
public class Server implements Runnable, ApplicationContextAware {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Value("${netty.port}")
    private int port;
    private ApplicationContext applicationContext;

    public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    //.childHandler(getChannelInitializer());
                    .childHandler(new ServerChannelInitializer(applicationContext));

            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            log.info("Netty服务启动: [port: {}, time: {}]", port, LocalDateTime.now());
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("Netty服务启动异常-" + e.getMessage());
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

//    @Bean(value = "channelInitializer")
//    public ChannelInitializer<SocketChannel>  getChannelInitializer(){
//        ChannelInitializer<SocketChannel> channelInitializer = new ChannelInitializer<SocketChannel>() {
//
//            /**
//             * 初始化channel
//             */
//            @Override
//            public void initChannel(SocketChannel ch) throws Exception {
//                ChannelPipeline pipeline = ch.pipeline();
//                pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
//                pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
//                pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
//                pipeline.addLast((ChannelHandler) applicationContext.getBean("serverChannelInboundHandler"));
//            }
//        };
//        return channelInitializer;
//    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public static void main(String[] args) {
        new Server().run();
    }
}
