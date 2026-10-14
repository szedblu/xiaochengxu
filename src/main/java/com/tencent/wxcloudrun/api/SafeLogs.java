package com.tencent.wxcloudrun.api;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 保留异常类型、堆栈和因果链，清除可能出现在请求 URL 中的凭据。 */
public final class SafeLogs {
    private SafeLogs() {}
    public static String redact(String text) {
        if (text == null) return "";
        return text.replaceAll("(https?://[^\\s?]+)\\?[^\\s]+", "$1?[REDACTED]")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+", "Bearer [REDACTED]")
                .replaceAll("(?i)((?:secret|password|token|js_code|session_key|authorization)[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s&,\\\"']+", "$1[REDACTED]");
    }
    public static Throwable exception(Throwable original) {
        return copy(original, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }
    private static Throwable copy(Throwable original, Set<Throwable> seen, int depth) {
        if (original == null || depth > 12 || !seen.add(original)) return null;
        RuntimeException safe = new RuntimeException(original.getClass().getName() + ": " + redact(original.getMessage()));
        safe.setStackTrace(original.getStackTrace());
        Throwable cause = copy(original.getCause(), seen, depth + 1);
        if (cause != null) safe.initCause(cause);
        for (Throwable suppressed : original.getSuppressed()) {
            Throwable item = copy(suppressed, seen, depth + 1);
            if (item != null) safe.addSuppressed(item);
        }
        return safe;
    }
}
