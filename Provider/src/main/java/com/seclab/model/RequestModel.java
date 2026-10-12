package com.seclab.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public String getRequestId() {
        return this.requestId;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public String getMethodName() {
        return this.methodName;
    }

    public Class<?>[] getParamTypes() {
        return this.paramTypes;
    }

    public Object[] getParamValues() {
        return this.paramValues;
    }

    public void setRequestId(final String requestId) {
        this.requestId = requestId;
    }

    public void setServiceName(final String serviceName) {
        this.serviceName = serviceName;
    }

    public void setMethodName(final String methodName) {
        this.methodName = methodName;
    }

    public void setParamTypes(final Class<?>[] paramTypes) {
        this.paramTypes = paramTypes;
    }

    public void setParamValues(final Object[] paramValues) {
        this.paramValues = paramValues;
    }

    @java.lang.Override
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof RequestModel)) return false;
        final RequestModel other = (RequestModel) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$requestId = this.getRequestId();
        final java.lang.Object other$requestId = other.getRequestId();
        if (this$requestId == null ? other$requestId != null : !this$requestId.equals(other$requestId)) return false;
        final java.lang.Object this$serviceName = this.getServiceName();
        final java.lang.Object other$serviceName = other.getServiceName();
        if (this$serviceName == null ? other$serviceName != null : !this$serviceName.equals(other$serviceName)) return false;
        final java.lang.Object this$methodName = this.getMethodName();
        final java.lang.Object other$methodName = other.getMethodName();
        if (this$methodName == null ? other$methodName != null : !this$methodName.equals(other$methodName)) return false;
        if (!java.util.Arrays.deepEquals(this.getParamTypes(), other.getParamTypes())) return false;
        if (!java.util.Arrays.deepEquals(this.getParamValues(), other.getParamValues())) return false;
        return true;
    }

    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof RequestModel;
    }

    @java.lang.Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $requestId = this.getRequestId();
        result = result * PRIME + ($requestId == null ? 43 : $requestId.hashCode());
        final java.lang.Object $serviceName = this.getServiceName();
        result = result * PRIME + ($serviceName == null ? 43 : $serviceName.hashCode());
        final java.lang.Object $methodName = this.getMethodName();
        result = result * PRIME + ($methodName == null ? 43 : $methodName.hashCode());
        result = result * PRIME + java.util.Arrays.deepHashCode(this.getParamTypes());
        result = result * PRIME + java.util.Arrays.deepHashCode(this.getParamValues());
        return result;
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "RequestModel(requestId=" + this.getRequestId() + ", serviceName=" + this.getServiceName() + ", methodName=" + this.getMethodName() + ", paramTypes=" + java.util.Arrays.deepToString(this.getParamTypes()) + ", paramValues=" + java.util.Arrays.deepToString(this.getParamValues()) + ")";
    }
}
