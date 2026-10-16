package com.tencent.wxcloudrun.api;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiResponse {
    private ApiResponse() {}
    public static Map<String,Object> ok(Object data) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("code",0);result.put("data",data);return result;
    }
}
