package com.seclab.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class ResponseModel {

    private static final Logger logger = LoggerFactory.getLogger(ResponseModel.class);

    private String responseId;
    private String serviceName;
    private String methodName;
    private String code;
    private String data;

    // Validate required constructor arguments.
    public ResponseModel(String responseId, String serviceName, String methodName, String code, String data) {
        this.responseId = responseId;
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.code = code;
        this.data = data;
    }
}
