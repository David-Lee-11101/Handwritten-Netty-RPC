package com.seclab.model;

import com.alibaba.fastjson.JSON;
import com.seclab.pojo.User;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyModelCompatibilityTest {
    @Test public void preservesBeanJsonEqualityAndDeepArraySemanticsWithoutAnnotationProcessor() {
        RequestModel first = new RequestModel("request.001", "service.001", "read", new Class<?>[]{String.class}, new Object[]{new int[]{1, 2}});
        RequestModel second = new RequestModel("request.001", "service.001", "read", new Class<?>[]{String.class}, new Object[]{new int[]{1, 2}});
        assertEquals(first, second); assertEquals(first.hashCode(), second.hashCode());
        second.setParamValues(new Object[]{new int[]{2, 1}}); assertNotEquals(first, second);
        ResponseModel response = new ResponseModel("request.001", "service.001", "read", "200", "true");
        assertEquals(response, JSON.parseObject(JSON.toJSONString(response), ResponseModel.class));
        User user = new User("user.001", "example", "42"); user.setAge("43");
        assertEquals(new User("user.001", "example", "43"), user);
        assertEquals("43", user.getAge());
    }
}
