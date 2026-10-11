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

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

/**
 * Netty 客户端管理类.
 */
public class Client {

    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final Object LOCK = new Object();
    private static final EventLoopGroup GROUP = new NioEventLoopGroup();
    private static volatile Channel channel;
    private static volatile String connectedHost;
    private static volatile int connectedPort;

    private Client() {}

    public static Channel getChannel(String host, int port) {
        if (!isUsable(channel) || !host.equals(connectedHost) || port != connectedPort) {
            synchronized (LOCK) {
                if (!isUsable(channel) || !host.equals(connectedHost) || port != connectedPort) {
                    if (channel != null) {
                        channel.close();
                    }
                    channel = createChannel(host, port);
                    connectedHost = host;
                    connectedPort = port;
                }
            }
        }
        return channel;
    }

    /**
     * 创建 channel.
     */
    public static Channel createChannel(String host, int port) {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(GROUP).channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);

            bootstrap.handler(new ClientInitializer());
            Channel newChannel = bootstrap.connect(host, port).sync().channel();

            newChannel.closeFuture().addListener((ChannelFutureListener) future -> {
                synchronized (LOCK) {
                    if (channel == newChannel) {
                        channel = null;
                    }
                }
                ClientPromise.failAll(new ClosedChannelException());
            });

            return newChannel;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while connecting to " + host + ':' + port, interrupted);
        } catch (RuntimeException connectionFailure) {
            throw new IllegalStateException("Unable to connect to " + host + ':' + port, connectionFailure);
        }
    }

    public static void shutdown() {
        Channel channelToClose;
        synchronized (LOCK) {
            channelToClose = channel;
            channel = null;
        }
        if (channelToClose != null) {
            channelToClose.close().syncUninterruptibly();
        }
        ClientPromise.failAll(new IllegalStateException("RPC client is shutting down"));
        GROUP.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
    }

    public static boolean isShutdown() {
        return GROUP.isTerminated();
    }

    private static boolean isUsable(Channel candidate) {
        return candidate != null && candidate.isActive();
    }
}
