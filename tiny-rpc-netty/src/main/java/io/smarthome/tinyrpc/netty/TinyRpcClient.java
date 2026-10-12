package io.smarthome.tinyrpc.netty;

import io.netty.bootstrap.Bootstrap;
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
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.smarthome.tinyrpc.codec.JsonLineCodec;
import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;
import io.smarthome.tinyrpc.telemetry.TelemetryListener;
import io.smarthome.tinyrpc.telemetry.TransportObservation;

import java.io.Closeable;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TinyRpcClient implements Closeable {
    private final RpcLimits limits;
    private final JsonLineCodec codec;
    private final TelemetryListener telemetry;
    private final EventLoopGroup eventLoop = new NioEventLoopGroup(1);
    private final Map<String, CompletableFuture<RpcResult>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger pendingReservations = new AtomicInteger();
    private final AtomicInteger queuedWrites = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final long monotonicOrigin = System.nanoTime();
    private final Channel channel;

    private TinyRpcClient(String host, int port, RpcLimits limits, TelemetryListener telemetry) throws InterruptedException {
        this.limits = limits;
        this.codec = new JsonLineCodec(limits);
        this.telemetry = telemetry == null ? TelemetryListener.NOOP : telemetry;
        this.channel = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel socket) {
                        socket.pipeline().addLast(new LineBasedFrameDecoder(limits.getMaxFrameBytes(), true, true));
                        socket.pipeline().addLast(new ResultHandler());
                    }
                }).connect(host, port).sync().channel();
        this.channel.closeFuture().addListener(ignored -> failAll(new ClosedChannelException()));
    }

    public static TinyRpcClient connect(String host, int port, RpcLimits limits, TelemetryListener telemetry)
            throws InterruptedException {
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("host is required");
        return new TinyRpcClient(host, port, limits == null ? RpcLimits.defaults() : limits, telemetry);
    }

    public CompletableFuture<RpcResult> invoke(RpcRequest request) {
        if (closed.get()) return failed(new ClosedChannelException());
        try { request.validate(System.currentTimeMillis()); } catch (RuntimeException invalid) { return failed(invalid); }
        if (!reservePending()) {
            return failed(new RejectedExecutionException("Maximum pending requests reached"));
        }
        CompletableFuture<RpcResult> future = new CompletableFuture<>();
        if (pending.putIfAbsent(request.getRequestId(), future) != null) {
            pendingReservations.decrementAndGet();
            return failed(new IllegalArgumentException("Duplicate requestId"));
        }
        int queued = queuedWrites.incrementAndGet();
        if (queued > limits.getMaxQueuedWrites()) {
            queuedWrites.decrementAndGet(); removePending(request.getRequestId(), future);
            return failed(new RejectedExecutionException("Maximum queued writes reached"));
        }
        final byte[] frame;
        try { frame = codec.encodeRequest(request); } catch (RuntimeException invalid) {
            queuedWrites.decrementAndGet(); removePending(request.getRequestId(), future); future.completeExceptionally(invalid); return future;
        }
        telemetry.onObservation(new TransportObservation(request.getTraceId(), request.getSessionId(), request.getRequestId(),
                "client-to-server", frame.length, System.nanoTime() - monotonicOrigin));
        long timeoutMs = Math.max(1L, request.getDeadlineEpochMs() - System.currentTimeMillis());
        io.netty.util.concurrent.ScheduledFuture<?> timeout = eventLoop.schedule(() -> {
            if (removePending(request.getRequestId(), future)) {
                future.completeExceptionally(new TimeoutException("RPC deadline exceeded"));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        future.whenComplete((value, failure) -> {
            timeout.cancel(false);
            if (future.isCancelled()) removePending(request.getRequestId(), future);
        });
        channel.writeAndFlush(Unpooled.wrappedBuffer(frame)).addListener(write -> {
            queuedWrites.decrementAndGet();
            if (!write.isSuccess() && removePending(request.getRequestId(), future)) future.completeExceptionally(write.cause());
        });
        return future;
    }

    public int pendingCount() { return pending.size(); }
    public int queuedWriteCount() { return queuedWrites.get(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        channel.close().syncUninterruptibly();
        failAll(new ClosedChannelException());
        eventLoop.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
    }

    private void failAll(Throwable failure) {
        pending.forEach((id, future) -> { if (removePending(id, future)) future.completeExceptionally(failure); });
    }

    private boolean reservePending() {
        while (true) {
            int current = pendingReservations.get();
            if (current >= limits.getMaxPendingRequests()) return false;
            if (pendingReservations.compareAndSet(current, current + 1)) return true;
        }
    }

    private boolean removePending(String requestId, CompletableFuture<RpcResult> future) {
        if (!pending.remove(requestId, future)) return false;
        pendingReservations.decrementAndGet();
        return true;
    }

    private static <T> CompletableFuture<T> failed(Throwable failure) {
        CompletableFuture<T> future = new CompletableFuture<>(); future.completeExceptionally(failure); return future;
    }

    private final class ResultHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override protected void channelRead0(ChannelHandlerContext context, ByteBuf frame) {
            byte[] bytes = new byte[frame.readableBytes()]; frame.readBytes(bytes);
            RpcResult result = codec.decodeResult(bytes);
            CompletableFuture<RpcResult> future = pending.get(result.getRequestId());
            if (future != null && removePending(result.getRequestId(), future)) future.complete(result);
        }

        @Override public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            failAll(cause); context.close();
        }
    }
}
