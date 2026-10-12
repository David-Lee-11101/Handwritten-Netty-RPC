package com.seclab.nettyServer;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> implements ApplicationContextAware {


    private ApplicationContext applicationContext;

    public ServerChannelInitializer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    /**
     * Initializes the channel.
     */
    @Override
    public void initChannel(SocketChannel ch) throws Exception {

        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext is null");
        }

        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
        pipeline.addLast(new JsonObjectDecoder());
        pipeline.addLast((ChannelHandler) applicationContext.getBean("serverChannelInboundHandler"));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
