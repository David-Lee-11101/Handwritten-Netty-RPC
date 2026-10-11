package com.seclab.nettyClient;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson.JSON;
import com.seclab.model.ResponseModel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientHandlerLoggingTest {

    @Test
    public void responseLogContainsCorrelationMetadataButNotPayload() {
        Logger logger = (Logger) LoggerFactory.getLogger(ClientHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        EmbeddedChannel channel = new EmbeddedChannel(new ClientHandler());
        try {
            ResponseModel response = new ResponseModel(
                    "unknown-42", "service", "method", "200", "private-payload-marker");
            channel.writeInbound(JSON.toJSONString(response));
            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertTrue(logs.contains("requestId=unknown-42"));
            assertTrue(logs.contains("status=200"));
            assertFalse(logs.contains("private-payload-marker"));
        } finally {
            channel.finishAndReleaseAll();
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
