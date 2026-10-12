package com.seclab.model;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class RequestModel {

    private static final Logger logger = LoggerFactory.getLogger(RequestModel.class);

    private String requestId;
    private String serviceName;
    private String methodName;
    private Class<?>[] paramTypes;
    private Object[] paramValues;

    // Validate required constructor arguments.
    public RequestModel(String requestId, String serviceName, String methodName, Class<?>[] paramTypes, Object[] paramValues) {

        this.requestId = requestId;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
        this.paramValues = paramValues;
    }
}
