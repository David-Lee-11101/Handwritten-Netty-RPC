package com.seclab.nettyServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson.JSON;
import com.seclab.model.RequestModel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.StaticApplicationContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerChannelInboundHandlerLoggingTest {

    @Test
    public void completionLogContainsRequestMetadataButNotReturnPayload() {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        String serviceName = LoggingEchoService.class.getName();
        String simpleName = serviceName.substring(serviceName.lastIndexOf('.') + 1);
        String beanName = simpleName.substring(0, 1).toLowerCase() + simpleName.substring(1);
        applicationContext.getBeanFactory().registerSingleton(beanName, new LoggingEchoServiceImpl());

        ServerChannelInboundHandler handler = new ServerChannelInboundHandler();
        handler.setApplicationContext(applicationContext);
        Logger logger = (Logger) LoggerFactory.getLogger(ServerChannelInboundHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        EmbeddedChannel channel = new EmbeddedChannel(handler) {
            @Override
            protected SocketAddress remoteAddress0() {
                return new InetSocketAddress("127.0.0.1", 12345);
            }
        };
        try {
            RequestModel request = new RequestModel(
                    "request-42", serviceName, "echo",
                    new Class<?>[]{String.class}, new Object[]{"private-payload-marker"});
            channel.writeInbound(JSON.toJSONString(request));
            Object response = channel.readOutbound();
            assertTrue(String.valueOf(response).contains("private-payload-marker"));

            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertTrue(logs.contains("requestId=request-42"));
            assertTrue(logs.contains("status=200"));
            assertFalse(logs.contains("private-payload-marker"));
        } finally {
            channel.finishAndReleaseAll();
            logger.detachAppender(appender);
            appender.stop();
            applicationContext.close();
        }
    }

    public interface LoggingEchoService {
        String echo(String value);
    }

    public static class LoggingEchoServiceImpl implements LoggingEchoService {
        @Override
        public String echo(String value) {
            return value;
        }
    }
}
