package io.smarthome.tinyrpc.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.smarthome.tinyrpc.codec.JsonLineCodec;
import io.smarthome.tinyrpc.core.RpcHandler;
import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;
import io.smarthome.tinyrpc.core.ServiceRegistry;
import io.smarthome.tinyrpc.telemetry.TelemetryListener;
import io.smarthome.tinyrpc.telemetry.TransportObservation;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TinyRpcServer implements Closeable {
    private final RpcLimits limits;
    private final JsonLineCodec codec;
    private final ServiceRegistry registry;
    private final TelemetryListener telemetry;
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup workers = new NioEventLoopGroup(1);
    private final ThreadPoolExecutor dispatch;
    private final long monotonicOrigin = System.nanoTime();
    private final AtomicBoolean closed = new AtomicBoolean();
    private Channel channel;

    private TinyRpcServer(Builder builder) {
        limits = builder.limits; registry = builder.registry; codec = new JsonLineCodec(limits);
        telemetry = builder.telemetry;
        dispatch = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(limits.getMaxServerWorkItems()), new ThreadPoolExecutor.AbortPolicy());
    }

    public static Builder builder() { return new Builder(); }

    public TinyRpcServer bind(String host, int port) throws InterruptedException {
        channel = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel socket) {
                        socket.pipeline().addLast(new LineBasedFrameDecoder(limits.getMaxFrameBytes(), true, true));
                        socket.pipeline().addLast(new RequestHandler());
                    }
                }).bind(host, port).sync().channel();
        return this;
    }

    public int port() { return ((InetSocketAddress) channel.localAddress()).getPort(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (channel != null) channel.close().syncUninterruptibly();
        dispatch.shutdownNow();
        workers.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        boss.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
    }

    private final class RequestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override protected void channelRead0(ChannelHandlerContext context, ByteBuf frame) {
            byte[] bytes = new byte[frame.readableBytes()]; frame.readBytes(bytes);
            final RpcRequest request;
            try { request = codec.decodeRequest(bytes); request.validate(System.currentTimeMillis()); }
            catch (RuntimeException malformed) { context.close(); return; }
            long started = System.nanoTime();
            try {
                dispatch.execute(() -> execute(context, request, started));
            } catch (RejectedExecutionException overloaded) {
                write(context, request, RpcResult.failure(request.getRequestId(), "error", "SERVER_OVERLOADED",
                        "Server work queue is full", elapsedMs(started)));
            }
        }

        private void execute(ChannelHandlerContext context, RpcRequest request, long started) {
            RpcHandler handler = registry.resolve(request.getServiceId(), request.getOperation());
            if (handler == null) {
                write(context, request, RpcResult.failure(request.getRequestId(), "error", "METHOD_NOT_ALLOWED",
                        "Service operation is not registered", elapsedMs(started))); return;
            }
            if (System.currentTimeMillis() >= request.getDeadlineEpochMs()) {
                write(context, request, RpcResult.failure(request.getRequestId(), "timeout", "DEADLINE_EXCEEDED",
                        "Request deadline exceeded", elapsedMs(started))); return;
            }
            try {
                String valueJson = handler.handle(request.getArgumentsJson());
                write(context, request, RpcResult.success(request.getRequestId(), valueJson, elapsedMs(started)));
            } catch (Exception failure) {
                write(context, request, RpcResult.failure(request.getRequestId(), "error", "HANDLER_FAILURE",
                        failure.getClass().getSimpleName(), elapsedMs(started)));
            }
        }

        private void write(ChannelHandlerContext context, RpcRequest request, RpcResult result) {
            byte[] bytes = codec.encodeResult(result);
            observe(new TransportObservation(request.getTraceId(), request.getSessionId(), request.getRequestId(),
                    "server-to-client", bytes.length, System.nanoTime() - monotonicOrigin));
            context.writeAndFlush(Unpooled.wrappedBuffer(bytes));
        }

        @Override public void exceptionCaught(ChannelHandlerContext context, Throwable cause) { context.close(); }
    }

    private static long elapsedMs(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
    private void observe(TransportObservation observation) {
        try { telemetry.onObservation(observation); } catch (RuntimeException ignored) { }
    }

    public static final class Builder {
        private RpcLimits limits = RpcLimits.defaults();
        private TelemetryListener telemetry = TelemetryListener.NOOP;
        private final ServiceRegistry registry = new ServiceRegistry();
        public Builder limits(RpcLimits limits) { this.limits = limits; return this; }
        public Builder telemetry(TelemetryListener telemetry) { this.telemetry = telemetry == null ? TelemetryListener.NOOP : telemetry; return this; }
        public Builder register(String serviceId, String operation, RpcHandler handler) { registry.register(serviceId, operation, handler); return this; }
        public TinyRpcServer build() { return new TinyRpcServer(this); }
    }
}
