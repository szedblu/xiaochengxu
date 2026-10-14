package com.tencent.wxcloudrun.api;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

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
    public static Throwable rootCause(Throwable original) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable root = original;
        while (root.getCause() != null && seen.add(root) && !seen.contains(root.getCause())) root = root.getCause();
        return root;
    }
    public static String summary(Throwable original) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<String> parts = new ArrayList<>();
        for (Throwable item = original; item != null && parts.size() < 12 && seen.add(item); item = item.getCause()) {
            parts.add(item.getClass().getSimpleName() + ": " + redact(item.getMessage()).replaceAll("[\\r\\n\\t]+", " "));
        }
        String result = String.join(" <- ", parts);
        return result.length() <= 2000 ? result : result.substring(0, 2000) + " [truncated]";
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
