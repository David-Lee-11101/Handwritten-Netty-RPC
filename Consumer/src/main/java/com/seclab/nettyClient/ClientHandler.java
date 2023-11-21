package com.seclab.nettyClient;

import com.alibaba.fastjson.JSON;
import com.seclab.model.ResponseModel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@ChannelHandler.Sharable
public class ClientHandler extends SimpleChannelInboundHandler<String> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        log.info("收到服务端消息: {}", msg);

        ResponseModel responseModel = JSON.parseObject(msg, ResponseModel.class);
        String responseId = responseModel.getResponseId();
        Promise<String> promise = ClientPromise.PROMISE_MAP.remove(responseId);

        if (promise != null) {
            String code = responseModel.getCode();
            if ("200".equals(code)) {
                promise.setSuccess(responseModel.getData());
            } else {
                promise.setFailure(new RuntimeException(responseModel.getData()));
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("发生异常", cause);
        // 可以选择将异常传播给更上层的处理层，或者在这里进行其他处理
    }
}

