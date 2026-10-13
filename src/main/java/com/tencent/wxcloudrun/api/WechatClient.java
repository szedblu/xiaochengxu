package com.tencent.wxcloudrun.api;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WechatClient {
    private final RestTemplate http;
    private final String appId;
    private final String secret;
    public WechatClient(RestTemplateBuilder builder,
            @Value("${app.wechat-app-id}") String appId,
            @Value("${app.wechat-app-secret}") String secret) {
        this.http = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        this.appId = appId;
        this.secret = secret;
    }
    public String exchange(String code) {
        if (secret.isBlank()) throw new ApiError(503, "服务端尚未配置微信登录");
        Map<?, ?> response;
        try {
            // 不信任客户端传入的 OpenID 或公网请求头，身份只来自微信服务端。
            response = http.getForObject("https://api.weixin.qq.com/sns/jscode2session?appid={appId}&secret={secret}&js_code={code}&grant_type=authorization_code",
                    Map.class, appId, secret, code);
        } catch (Exception e) { throw new ApiError(502, "微信登录暂时不可用，请重试"); }
        if (response == null || !(response.get("openid") instanceof String)
                || ((String) response.get("openid")).isBlank()
                || ((String) response.get("openid")).length() > 128
                || (response.get("errcode") != null && !"0".equals(String.valueOf(response.get("errcode")))))
            throw new ApiError(401, "微信登录凭证已失效，请重新登录");
        return (String) response.get("openid");
    }
}
