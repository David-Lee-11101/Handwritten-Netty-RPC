package io.smarthome.tinyrpc.codec;

import io.smarthome.tinyrpc.core.RpcRequest;
import io.smarthome.tinyrpc.core.RpcResult;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class IdentifierValidationTest {
    @Test public void preservesAsciiIdentifierBoundaryAtConstructionAndAfterDecoding() {
        char[] maximum = new char[128]; Arrays.fill(maximum, 'a');
        for (String valid : new String[]{"abc", "request.value-01:unit", new String(maximum)}) {
            RpcRequest request = new RpcRequest(valid, valid, valid, valid, valid, "[]", 2000);
            request.validate(1000); RpcResult.success(valid, "true", 0).validate();
        }
        for (String invalid : new String[]{null, "", "ab", "1request", "request value", "request/one", "request\nvalue", "éclair", new String(maximum) + "a"}) {
            try { new RpcRequest(invalid, "session.valid", "request.valid", "service.valid", "operation.valid", "[]", 2000); fail("Invalid constructor ID"); }
            catch (IllegalArgumentException expected) { }
            RpcRequest decoded = new RpcRequest("trace.valid", "session.valid", "request.valid", "service.valid", "operation.valid", "[]", 2000);
            decoded.setRequestId(invalid);
            try { decoded.validate(1000); fail("Invalid decoded ID"); } catch (IllegalArgumentException expected) { }
            try { RpcResult.success(invalid, "true", 0).validate(); fail("Invalid result ID"); } catch (IllegalArgumentException expected) { }
        }
    }
}
