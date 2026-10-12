package io.smarthome.tinyrpc.codec;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;

import java.nio.charset.StandardCharsets;

public final class JsonLineCodec {
    static {
        ParserConfig.getGlobalInstance().setAutoTypeSupport(false);
    }

    private final int maxFrameBytes;

    public JsonLineCodec(RpcLimits limits) {
        this.maxFrameBytes = limits.getMaxFrameBytes();
    }

    public byte[] encodeRequest(RpcRequest request) {
        return encode(JSON.toJSONString(request));
    }

    public byte[] encodeResult(RpcResult result) {
        return encode(JSON.toJSONString(result));
    }

    public RpcRequest decodeRequest(byte[] frame) {
        return JSON.parseObject(decode(frame), RpcRequest.class);
    }

    public RpcResult decodeResult(byte[] frame) {
        return JSON.parseObject(decode(frame), RpcResult.class);
    }

    private byte[] encode(String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        if (payload.length + 1 > maxFrameBytes) throw new FrameLimitException(maxFrameBytes, payload.length + 1);
        byte[] frame = new byte[payload.length + 1];
        System.arraycopy(payload, 0, frame, 0, payload.length);
        frame[frame.length - 1] = '\n';
        return frame;
    }

    private String decode(byte[] frame) {
        if (frame == null || frame.length == 0 || frame.length > maxFrameBytes) {
            throw new FrameLimitException(maxFrameBytes, frame == null ? 0 : frame.length);
        }
        int length = frame[frame.length - 1] == '\n' ? frame.length - 1 : frame.length;
        return new String(frame, 0, length, StandardCharsets.UTF_8);
    }
}
