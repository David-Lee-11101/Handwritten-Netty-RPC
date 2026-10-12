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

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Measurement-only entry point for the untouched legacy implementation.
 * It is intentionally stored in test sources and does not modify production behavior.
 */
public final class LegacyBenchmarkMain {
    private static final int WARMUP_CALLS = 200;
    private static final int MEASURED_CALLS = 1000;

    private LegacyBenchmarkMain() {
    }

    public static void main(String[] args) throws Exception {
        // Metrology v3 starts before all implementation-owned runtime groups, matching TinyRpcBenchmarkMain.
        long processStart = System.nanoTime();
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup workers = new NioEventLoopGroup(2);
        Channel server = null;
        try {
            EchoService target = new EchoServiceImpl();
            server = new ServerBootstrap()
                    .group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline()
                                    .addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()))
                                    .addLast(new StringDecoder(CharsetUtil.UTF_8))
                                    .addLast(new StringEncoder(CharsetUtil.UTF_8))
                                    .addLast(new Endpoint(target));
                        }
                    })
                    .bind("127.0.0.1", 0)
                    .sync()
                    .channel();

            int port = ((InetSocketAddress) server.localAddress()).getPort();
            EchoService client = new RpcClientProxyFactory("127.0.0.1", port, 2000)
                    .create(EchoService.class);
            String first = client.echo("ready");
            if (!"ready".equals(first)) {
                throw new IllegalStateException("Unexpected first response");
            }
            long startupNanos = System.nanoTime() - processStart;

            for (int index = 0; index < WARMUP_CALLS; index++) {
                client.echo("warmup-" + index);
            }

            System.gc();
            Thread.sleep(200);
            long heapBytes = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long rssBytes = readRssBytes();

            List<Long> latencyNanos = new ArrayList<>(MEASURED_CALLS);
            long measuredStart = System.nanoTime();
            for (int index = 0; index < MEASURED_CALLS; index++) {
                long started = System.nanoTime();
                String expected = "value-" + index;
                String actual = client.echo(expected);
                if (!expected.equals(actual)) {
                    throw new IllegalStateException("Unexpected response");
                }
                latencyNanos.add(System.nanoTime() - started);
            }
            long measuredNanos = System.nanoTime() - measuredStart;
            Collections.sort(latencyNanos);

            String json = "{"
                    + "\"schemaVersion\":\"1.0.0\","
                    + "\"pid\":" + currentPid() + ','
                    + "\"startupNanos\":" + startupNanos + ','
                    + "\"idleHeapBytes\":" + heapBytes + ','
                    + "\"idleRssBytes\":" + rssBytes + ','
                    + "\"measuredCalls\":" + MEASURED_CALLS + ','
                    + "\"p50LatencyNanos\":" + percentile(latencyNanos, 0.50) + ','
                    + "\"p95LatencyNanos\":" + percentile(latencyNanos, 0.95) + ','
                    + "\"p99LatencyNanos\":" + percentile(latencyNanos, 0.99) + ','
                    + "\"throughputPerSecond\":"
                    + String.format(java.util.Locale.ROOT, "%.3f", MEASURED_CALLS * 1_000_000_000.0 / measuredNanos)
                    + '}';
            System.out.println("BENCHMARK_JSON=" + json);
        } finally {
            Client.shutdown();
            if (server != null) {
                server.close().syncUninterruptibly();
            }
            boss.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
            workers.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    private static long percentile(List<Long> values, double percentile) {
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private static long readRssBytes() throws Exception {
        Process process = new ProcessBuilder("ps", "-o", "rss=", "-p", Long.toString(currentPid())).start();
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), CharsetUtil.UTF_8));
        String value = reader.readLine();
        if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0 || value == null) {
            throw new IllegalStateException("Unable to read process RSS");
        }
        return Long.parseLong(value.trim()) * 1024L;
    }

    private static long currentPid() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return Long.parseLong(runtimeName.substring(0, runtimeName.indexOf('@')));
    }

    public interface EchoService {
        String echo(String value);
    }

    public static final class EchoServiceImpl implements EchoService {
        @Override
        public String echo(String value) {
            return value;
        }
    }

    private static final class Endpoint extends SimpleChannelInboundHandler<String> {
        private final Object target;

        private Endpoint(Object target) {
            this.target = target;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, String message) throws Exception {
            RequestModel request = JSON.parseObject(message, RequestModel.class);
            Class<?> serviceClass = Class.forName(request.getServiceName());
            Method method = serviceClass.getMethod(request.getMethodName(), request.getParamTypes());
            Object result = method.invoke(target, request.getParamValues());
            ResponseModel response = new ResponseModel(
                    request.getRequestId(), request.getServiceName(), request.getMethodName(),
                    "200", JSON.toJSONString(result));
            context.writeAndFlush(JSON.toJSONString(response) + "\n");
        }
    }
}

