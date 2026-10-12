package com.seclab.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public String getResponseId() {
        return this.responseId;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public String getMethodName() {
        return this.methodName;
    }

    public String getCode() {
        return this.code;
    }

    public String getData() {
        return this.data;
    }

    public void setResponseId(final String responseId) {
        this.responseId = responseId;
    }

    public void setServiceName(final String serviceName) {
        this.serviceName = serviceName;
    }

    public void setMethodName(final String methodName) {
        this.methodName = methodName;
    }

    public void setCode(final String code) {
        this.code = code;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof ResponseModel)) return false;
        final ResponseModel other = (ResponseModel) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$responseId = this.getResponseId();
        final java.lang.Object other$responseId = other.getResponseId();
        if (this$responseId == null ? other$responseId != null : !this$responseId.equals(other$responseId)) return false;
        final java.lang.Object this$serviceName = this.getServiceName();
        final java.lang.Object other$serviceName = other.getServiceName();
        if (this$serviceName == null ? other$serviceName != null : !this$serviceName.equals(other$serviceName)) return false;
        final java.lang.Object this$methodName = this.getMethodName();
        final java.lang.Object other$methodName = other.getMethodName();
        if (this$methodName == null ? other$methodName != null : !this$methodName.equals(other$methodName)) return false;
        final java.lang.Object this$code = this.getCode();
        final java.lang.Object other$code = other.getCode();
        if (this$code == null ? other$code != null : !this$code.equals(other$code)) return false;
        final java.lang.Object this$data = this.getData();
        final java.lang.Object other$data = other.getData();
        if (this$data == null ? other$data != null : !this$data.equals(other$data)) return false;
        return true;
    }

    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof ResponseModel;
    }

    @java.lang.Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $responseId = this.getResponseId();
        result = result * PRIME + ($responseId == null ? 43 : $responseId.hashCode());
        final java.lang.Object $serviceName = this.getServiceName();
        result = result * PRIME + ($serviceName == null ? 43 : $serviceName.hashCode());
        final java.lang.Object $methodName = this.getMethodName();
        result = result * PRIME + ($methodName == null ? 43 : $methodName.hashCode());
        final java.lang.Object $code = this.getCode();
        result = result * PRIME + ($code == null ? 43 : $code.hashCode());
        final java.lang.Object $data = this.getData();
        result = result * PRIME + ($data == null ? 43 : $data.hashCode());
        return result;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "ResponseModel(responseId=" + this.getResponseId() + ", serviceName=" + this.getServiceName() + ", methodName=" + this.getMethodName() + ", code=" + this.getCode() + ", data=" + this.getData() + ")";
    }
}
