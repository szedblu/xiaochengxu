package com.tencent.wxcloudrun.api;

import java.time.Duration;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

@Component
public class WechatClient {
    private static final Logger LOG = LoggerFactory.getLogger(WechatClient.class);
    private final ObjectMapper json = new ObjectMapper();
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
        LOG.info("WeChat exchange started");
        try {
            // 不信任客户端传入的 OpenID 或公网请求头，身份只来自微信服务端。
            // 微信可能用 text/plain 返回 JSON，先读取文本再解析，避免 Map 消息转换器拒绝响应。
            String body = http.getForObject("https://api.weixin.qq.com/sns/jscode2session?appid={appId}&secret={secret}&js_code={code}&grant_type=authorization_code",
                    String.class, appId, secret, code);
            response = json.readValue(body, Map.class);
        } catch (Exception e) {
            // 异常 message 可能含带 secret 的请求 URL，不记录 message 或原始响应正文。
            LOG.error("WeChat exchange failed: exception={}, upstreamStatus={}", e.getClass().getSimpleName(),
                    e instanceof HttpStatusCodeException ? ((HttpStatusCodeException) e).getRawStatusCode() : "unknown", SafeLogs.exception(e));
            throw new ApiError(502, "微信登录连接失败，请稍后重试", e);
        }
        Object errorCode = response == null ? null : response.get("errcode");
        if (errorCode != null && !"0".equals(String.valueOf(errorCode))) {
            int number;
            try { number = Integer.parseInt(String.valueOf(errorCode)); }
            catch (NumberFormatException e) { LOG.warn("WeChat errcode format invalid", SafeLogs.exception(e)); number = -2; }
            LOG.warn("WeChat exchange rejected: errcode={}", number);
            switch (number) {
                case 40013: throw new ApiError(503, "服务端小程序 AppID 配置错误");
                case 40125: throw new ApiError(503, "服务端小程序 AppSecret 配置错误");
                case 45011: throw new ApiError(429, "登录请求过于频繁，请稍后重试");
                case -1: throw new ApiError(503, "微信服务繁忙，请稍后重试");
                default: throw new ApiError(401, "微信登录凭证已失效，请重新登录");
            }
        }
        if (response == null || !(response.get("openid") instanceof String)
                || ((String) response.get("openid")).isBlank()
                || ((String) response.get("openid")).length() > 128)
            throw new ApiError(401, "微信登录凭证已失效，请重新登录");
        LOG.info("WeChat exchange succeeded");
        return (String) response.get("openid");
    }
}
