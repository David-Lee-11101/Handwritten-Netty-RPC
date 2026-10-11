package com.seclab.nettyClient;

import com.alibaba.fastjson.JSON;
import com.seclab.model.RequestModel;
import com.seclab.model.ResponseModel;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RpcLoopbackIntegrationTest {
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;
    private static Channel serverChannel;
    private static RpcClientProxyFactory proxyFactory;

    @BeforeClass
    public static void startServer() throws Exception {
        EchoService target = new EchoServiceImpl();
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);
        serverChannel = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()))
                                .addLast(new StringDecoder(CharsetUtil.UTF_8))
                                .addLast(new StringEncoder(CharsetUtil.UTF_8))
                                .addLast(new ReflectionEndpoint(target));
                    }
                })
                .bind("127.0.0.1", 0)
                .sync()
                .channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        proxyFactory = new RpcClientProxyFactory("127.0.0.1", port, 200);
    }

    @AfterClass
    public static void stopServer() {
        Client.shutdown();
        assertTrue(Client.isShutdown());
        assertEquals(0, ClientPromise.pendingCount());
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test
    public void realLoopbackCoversRapidConcurrentTimeoutAndDisconnectPaths() throws Exception {
        EchoService service = proxyFactory.create(EchoService.class);
        assertEquals("rapid", service.echo("rapid"));

        String ruleSpec = "{\"event\":\"temperature.high\",\"action\":\"fan.start\",\"version\":1}";
        assertEquals(ruleSpec, service.echo(ruleSpec));

        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> responses = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                final String expected = "response-" + index;
                responses.add(callers.submit(() -> service.echo(expected)));
            }
            for (int index = 0; index < responses.size(); index++) {
                assertEquals("response-" + index, responses.get(index).get(3, TimeUnit.SECONDS));
            }
        } finally {
            callers.shutdownNow();
            callers.awaitTermination(2, TimeUnit.SECONDS);
        }
        assertEquals(0, ClientPromise.pendingCount());

        try {
            service.timeout("late");
            fail("Expected timeout");
        } catch (Throwable failure) {
            Throwable root = rootCause(failure);
            assertTrue("Expected TimeoutException but got " + root, root instanceof TimeoutException);
        }
        awaitNoPendingPromises();

        try {
            service.disconnect("close");
            fail("Expected disconnect");
        } catch (Throwable failure) {
            Throwable root = rootCause(failure);
            assertTrue("Expected ClosedChannelException but got " + root,
                    root instanceof ClosedChannelException);
        }
        awaitNoPendingPromises();

        assertEquals("reconnected", service.echo("reconnected"));
        assertEquals(0, ClientPromise.pendingCount());
    }

    private static void awaitNoPendingPromises() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (ClientPromise.pendingCount() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(0, ClientPromise.pendingCount());
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public interface EchoService {
        String echo(String value);
        String timeout(String value);
        String disconnect(String value);
    }

    public static class EchoServiceImpl implements EchoService {
        @Override
        public String echo(String value) {
            return value;
        }

        @Override
        public String timeout(String value) {
            return value;
        }

        @Override
        public String disconnect(String value) {
            return value;
        }
    }

    private static class ReflectionEndpoint extends SimpleChannelInboundHandler<String> {
        private final Object target;

        private ReflectionEndpoint(Object target) {
            this.target = target;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, String message) throws Exception {
            RequestModel request = JSON.parseObject(message, RequestModel.class);
            if ("timeout".equals(request.getMethodName())) {
                return;
            }
            if ("disconnect".equals(request.getMethodName())) {
                context.close();
                return;
            }

            ResponseModel unknown = new ResponseModel(
                    "unknown-" + request.getRequestId(),
                    request.getServiceName(),
                    request.getMethodName(),
                    "200",
                    JSON.toJSONString("ignored"));
            context.writeAndFlush(JSON.toJSONString(unknown) + "\n");

            Class<?> serviceClass = Class.forName(request.getServiceName());
            Method method = serviceClass.getMethod(request.getMethodName(), request.getParamTypes());
            Object result = method.invoke(target, request.getParamValues());
            ResponseModel response = new ResponseModel(
                    request.getRequestId(),
                    request.getServiceName(),
                    request.getMethodName(),
                    "200",
                    JSON.toJSONString(result));
            String frame = JSON.toJSONString(response) + "\n";
            context.writeAndFlush(frame);
            context.writeAndFlush(frame);
        }
    }
}
