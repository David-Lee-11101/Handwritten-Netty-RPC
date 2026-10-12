package io.smarthome.tinyrpc.spring;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import io.smarthome.tinyrpc.core.ServiceRegistry;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Explicit, allowlisted Spring bridge. It never resolves a class or method from a request. */
public final class SpringServiceRegistrar {
    private final ApplicationContext context;
    private final ServiceRegistry registry;

    public SpringServiceRegistrar(ApplicationContext context, ServiceRegistry registry) {
        if (context == null || registry == null) throw new IllegalArgumentException("context and registry are required");
        this.context = context; this.registry = registry;
    }

    public SpringServiceRegistrar register(String serviceId, String operation, String beanName,
                                           Class<?> interfaceType, String methodName, Class<?>... parameterTypes) {
        Object bean = context.getBean(beanName);
        if (!interfaceType.isInstance(bean)) throw new IllegalArgumentException("Bean does not implement the registered interface");
        final Method method;
        try { method = interfaceType.getMethod(methodName, parameterTypes); }
        catch (NoSuchMethodException failure) { throw new IllegalArgumentException("Registered method does not exist", failure); }
        registry.register(serviceId, operation, argumentsJson -> {
            JSONArray arguments = JSON.parseArray(argumentsJson);
            if (arguments.size() != parameterTypes.length) throw new IllegalArgumentException("Argument count mismatch");
            Object[] values = new Object[parameterTypes.length];
            for (int index = 0; index < values.length; index++) values[index] = arguments.getObject(index, parameterTypes[index]);
            try { return JSON.toJSONString(method.invoke(bean, values)); }
            catch (InvocationTargetException failure) {
                Throwable target = failure.getTargetException();
                if (target instanceof Exception) throw (Exception) target;
                throw new IllegalStateException("Registered method failed", target);
            }
        });
        return this;
    }
}
