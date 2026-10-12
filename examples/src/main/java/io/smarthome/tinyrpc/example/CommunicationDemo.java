package io.smarthome.tinyrpc.example;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.smarthome.tinyrpc.core.RpcLimits;
import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;
import io.smarthome.tinyrpc.netty.TinyRpcClient;
import io.smarthome.tinyrpc.netty.TinyRpcServer;
import io.smarthome.tinyrpc.telemetry.TransportObservation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public final class CommunicationDemo {
    private CommunicationDemo() { }

    public static void main(String[] args) throws Exception {
        List<TransportObservation> observations = new CopyOnWriteArrayList<>();
        RpcLimits limits = RpcLimits.defaults();
        try (TinyRpcServer server = TinyRpcServer.builder().limits(limits).telemetry(observations::add)
                .register("temperature.sensor", "read", ignored -> "{\"type\":\"number\",\"value\":21.5}")
                .build().bind("127.0.0.1", 0);
             TinyRpcClient client = TinyRpcClient.connect("127.0.0.1", server.port(), limits, observations::add)) {
            RpcRequest request = new RpcRequest("trace-demo-001", "session-demo-001", "request-demo-001",
                    "temperature.sensor", "read", "[]", System.currentTimeMillis() + 2000);
            RpcResult result = client.invoke(request).get(2, TimeUnit.SECONDS);
            JSONObject output = new JSONObject(true);
            output.put("schemaVersion", "1.0.0");
            output.put("invocationResult", result);
            JSONObject trace = new JSONObject(true);
            trace.put("schemaVersion", "1.0.0"); trace.put("traceId", request.getTraceId());
            trace.put("sourceKind", "instrumented-rpc"); trace.put("observations", observations);
            output.put("trafficTrace", trace);
            System.out.println(JSON.toJSONString(output));
        }
    }
}
