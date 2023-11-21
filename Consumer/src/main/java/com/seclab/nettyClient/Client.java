package com.seclab.nettyClient;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty 客户端管理类.
 */
public class Client {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final Object LOCK = new Object();
    private static volatile Channel channel = null;

    private Client() {}

    public static Channel getChannel(String host, int port) {
        if (channel == null) {
            synchronized (LOCK) {
                if (channel == null) {
                    channel = createChannel(host, port);
                }
            }
        }
        return channel;
    }

    /**
     * 创建 channel.
     */
    public static Channel createChannel(String host, int port) {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
            //bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

            bootstrap.handler(new ClientInitializer());
            Channel channel = bootstrap.connect(host, port).sync().channel();

            channel.closeFuture().addListener((ChannelFutureListener) future -> {
                group.shutdownGracefully();
            });

            return channel;
        } catch (Exception e) {
            logger.error("Client 创建 channel 异常", e);
        }
        return null;
    }
}
