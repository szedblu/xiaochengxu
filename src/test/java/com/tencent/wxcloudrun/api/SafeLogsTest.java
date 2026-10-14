package com.tencent.wxcloudrun.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafeLogsTest {
    @Test void keepsDiagnosticStackAndCausesButRedactsCredentials() {
        RuntimeException inner = new RuntimeException("https://api.weixin.qq.com/sns/jscode2session?secret=private-secret&js_code=private-code");
        RuntimeException outer = new RuntimeException("password=private-password Authorization=Bearer private-token", inner);
        StringWriter text = new StringWriter(); SafeLogs.exception(outer).printStackTrace(new PrintWriter(text));
        String log = text.toString();
        assertTrue(log.contains("SafeLogsTest")); assertTrue(log.contains("Caused by"));
        assertFalse(log.contains("private-secret")); assertFalse(log.contains("private-code"));
        assertFalse(log.contains("private-password")); assertFalse(log.contains("private-token"));
        String summary = SafeLogs.summary(outer);
        assertFalse(summary.contains("\n")); assertFalse(summary.contains("private-secret"));
        assertFalse(summary.contains("private-code")); assertFalse(summary.contains("private-password"));
        assertFalse(summary.contains("private-token")); assertSame(inner, SafeLogs.rootCause(outer));
    }
}
