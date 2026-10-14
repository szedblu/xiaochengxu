package com.tencent.wxcloudrun.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

public final class Input {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private Input() {}
    public static LocalDate today() { return LocalDate.now(ZONE); }
    public static String text(Map<String, Object> data, String key, int max, boolean required) {
        Object value = data.get(key);
        if (value != null && !(value instanceof String)) throw new ApiError(400, key + " 格式不正确");
        String s = value == null ? "" : ((String) value).trim();
        if ((required && s.isEmpty()) || s.length() > max) throw new ApiError(400, key + " 内容长度不正确");
        return s;
    }
    public static BigDecimal decimal(Object value, double min, double max, int scale) {
        try {
            BigDecimal n = new BigDecimal(String.valueOf(value));
            if (n.compareTo(BigDecimal.valueOf(min)) < 0 || n.compareTo(BigDecimal.valueOf(max)) > 0)
                throw new NumberFormatException();
            return n.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (RuntimeException e) { throw new ApiError(400, "数值超出范围或小数位不正确", e); }
    }
    public static LocalDate date(Object value) {
        try {
            LocalDate d = LocalDate.parse(String.valueOf(value));
            if (d.getYear() < 1900 || d.getYear() > 2200) throw new IllegalArgumentException();
            return d;
        } catch (RuntimeException e) { throw new ApiError(400, "日期格式不正确", e); }
    }
}
