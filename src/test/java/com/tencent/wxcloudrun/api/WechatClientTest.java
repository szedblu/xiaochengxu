package com.tencent.wxcloudrun.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.startsWith;

class WechatClientTest {
    private MockRestServiceServer server;
    private WechatClient client(String secret) {
        RestTemplateBuilder builder = new RestTemplateBuilder().customizers(http -> server = MockRestServiceServer.bindTo(http).build());
        return new WechatClient(builder, "test-app-id", secret);
    }
    @Test void exchangesCodeOnlyAgainstWechatAndReturnsOnlyOpenid() {
        WechatClient client = client("test-secret");
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andRespond(withSuccess("{\"openid\":\"verified-openid\",\"session_key\":\"private-session-key\"}", MediaType.APPLICATION_JSON));
        assertEquals("verified-openid", client.exchange("wechat-code")); server.verify();
    }
    @Test void rejectsWechatErrorsAndBlankIdentity() {
        WechatClient client = client("test-secret");
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/")))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.APPLICATION_JSON));
        assertEquals(401, assertThrows(ApiError.class, () -> client.exchange("invalid")).status); server.verify();
        server.reset();
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/")))
                .andRespond(withSuccess("{\"openid\":\"\"}", MediaType.APPLICATION_JSON));
        assertEquals(401, assertThrows(ApiError.class, () -> client.exchange("blank")).status); server.verify();
    }
    @Test void acceptsWechatJsonReturnedAsPlainText() {
        WechatClient client = client("test-secret");
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/")))
                .andRespond(withSuccess("{\"openid\":\"verified-plain-text-openid\"}", MediaType.TEXT_PLAIN));
        assertEquals("verified-plain-text-openid", client.exchange("wechat-code")); server.verify();
    }
    @Test void exposesActionableConfigurationErrorsWithoutCredentials() {
        WechatClient client = client("private-value");
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/")))
                .andRespond(withSuccess("{\"errcode\":40125,\"errmsg\":\"invalid secret\"}", MediaType.TEXT_PLAIN));
        ApiError error = assertThrows(ApiError.class, () -> client.exchange("code"));
        assertEquals(503, error.status); assertTrue(error.getMessage().contains("AppSecret"));
        assertFalse(error.getMessage().contains("private-value")); server.verify();
    }
    @Test void reportsMissingConfigurationAndUpstreamFailureWithoutSecrets() {
        WechatClient noSecret = client("");
        assertEquals(503, assertThrows(ApiError.class, () -> noSecret.exchange("code")).status);
        WechatClient client = client("private-value");
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/"))).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        ApiError error = assertThrows(ApiError.class, () -> client.exchange("code"));
        assertEquals(502, error.status); assertFalse(error.getMessage().contains("private-value")); server.verify();
    }
    @Test void reportsDnsCauseOnFirstLogLineWithoutCredentials() {
        WechatClient client = client("private-value");
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(WechatClient.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> events = new ch.qos.logback.core.read.ListAppender<>();
        events.start(); logger.addAppender(events);
        try {
            server.expect(requestTo(startsWith("https://api.weixin.qq.com/")))
                    .andRespond(request -> { throw new java.net.UnknownHostException("api.weixin.qq.com"); });
            ApiError error = assertThrows(ApiError.class, () -> client.exchange("private-code"));
            assertTrue(error.getMessage().contains("DNS"));
            String line = events.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.startsWith("WeChat exchange failed")).findFirst().orElseThrow();
            assertTrue(line.contains("rootCause=UnknownHostException")); assertTrue(line.contains("api.weixin.qq.com"));
            assertFalse(line.contains("private-value")); assertFalse(line.contains("private-code")); assertFalse(line.contains("\n"));
            server.verify();
        } finally { logger.detachAppender(events); events.stop(); }
    }
    @Test void distinguishesTlsFailureFromInvalidLoginCode() {
        WechatClient client = client("private-value");
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/")))
                .andRespond(request -> { throw new javax.net.ssl.SSLHandshakeException("PKIX path building failed"); });
        ApiError error = assertThrows(ApiError.class, () -> client.exchange("private-code"));
        assertEquals(502, error.status); assertTrue(error.getMessage().contains("TLS")); server.verify();
    }
}
