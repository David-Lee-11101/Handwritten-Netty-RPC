package com.seclab.nettyClient;

import com.alibaba.fastjson.JSON;
import com.seclab.model.ResponseModel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@ChannelHandler.Sharable
public class ClientHandler extends SimpleChannelInboundHandler<String> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        ResponseModel responseModel = JSON.parseObject(msg, ResponseModel.class);
        String responseId = responseModel.getResponseId();
        String code = responseModel.getCode();
        log.info("Received RPC response [requestId={}, status={}]", responseId, code);
        boolean completed;
        if ("200".equals(code)) {
            completed = ClientPromise.succeed(responseId, responseModel.getData());
        } else {
            completed = ClientPromise.fail(responseId, new RuntimeException(responseModel.getData()));
        }
        if (!completed) {
            log.warn("Ignoring response for unknown request ID {}", responseId);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ClientPromise.failAll(new java.nio.channels.ClosedChannelException());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Channel failure", cause);
        ClientPromise.failAll(cause);
        ctx.close();
    }
}

