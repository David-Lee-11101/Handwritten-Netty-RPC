package com.seclab.nettyServer;

import com.alibaba.fastjson.JSON;
import com.seclab.model.RequestModel;
import com.seclab.model.ResponseModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@ChannelHandler.Sharable
@Component
public class ServerChannelInboundHandler extends SimpleChannelInboundHandler<String> implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ServerChannelInboundHandler.class);
    private ApplicationContext applicationContext;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        StringBuilder sb = new StringBuilder();
        RequestModel result = null;
        try {
            result = JSON.parseObject(msg, RequestModel.class);

            String requestId = result.getRequestId();
            String serviceInterfaceName = result.getServiceName();
            String methodName = result.getMethodName();
            Class<?>[] paramTypes = result.getParamTypes();
            Object[] paramValues = result.getParamValues();

            log.info("Server received request: [RequestId: {}, Service: {}, Method: {}]", requestId, serviceInterfaceName, methodName);

            String serviceSimpleName = serviceInterfaceName.substring(serviceInterfaceName.lastIndexOf(".") + 1);
            String serviceObjectName = serviceSimpleName.substring(0, 1).toLowerCase() + serviceSimpleName.substring(1);
            Object serviceObject = applicationContext.getBean(serviceObjectName);

            Method method = Class.forName(serviceInterfaceName).getMethod(methodName, paramTypes);
            Object returnValue = method.invoke(serviceObject, paramValues);

            ResponseModel responseModel = new ResponseModel(requestId, serviceInterfaceName, methodName, "200", JSON.toJSONString(returnValue));
            sb.append(JSON.toJSONString(responseModel)).append("\n");
            log.info("Completed RPC request [requestId={}, service={}, method={}, status=200]",
                    requestId, serviceInterfaceName, methodName);
            ctx.writeAndFlush(sb);
        } catch (Exception e) {
            String requestId = result != null ? result.getRequestId() : "";
            String serviceName = result != null ? result.getServiceName() : "";
            String methodName = result != null ? result.getMethodName() : "";
            log.error("Failed RPC request [requestId={}, service={}, method={}, status=500, errorType={}]",
                    requestId, serviceName, methodName, e.getClass().getSimpleName());
            ResponseModel responseModel = new ResponseModel(requestId, "", "", "500", e.getMessage());
            String errorCode = JSON.toJSONString(responseModel) + "\n";
            ctx.writeAndFlush(errorCode);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        InetSocketAddress insocket = (InetSocketAddress) ctx.channel().remoteAddress();
        String clientIp = insocket.getAddress().getHostAddress();
        log.info("Client connected [IP: {}]", clientIp);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception caught: {}", cause.getMessage());
        ctx.close();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

