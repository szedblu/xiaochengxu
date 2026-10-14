package com.tencent.wxcloudrun.api;

import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 仅适用于微信云托管网关注入并保护 X-WX-* 头的调用链路。 */
@Component
public class CloudIdentity {
    private static final Logger LOG = LoggerFactory.getLogger(CloudIdentity.class);
    private final String expectedAppId;
    private final String expectedEnv;
    public CloudIdentity(@Value("${app.wechat-app-id}") String appId, @Value("${app.cloud-env}") String env) {
        this.expectedAppId = appId; this.expectedEnv = env;
    }
    public String require(HttpServletRequest request) {
        String source = request.getHeader("X-WX-SOURCE");
        String env = request.getHeader("X-WX-ENV");
        String fromApp = request.getHeader("X-WX-FROM-APPID");
        String appId = fromApp == null ? request.getHeader("X-WX-APPID") : fromApp;
        String openid = fromApp == null ? request.getHeader("X-WX-OPENID") : request.getHeader("X-WX-FROM-OPENID");
        LOG.info("Cloud login identity headers: source={}, appId={}, env={}, openidPresent={}",
                SafeLogs.redact(source), SafeLogs.redact(appId), SafeLogs.redact(env), openid != null && !openid.isBlank());
        // 平台没有承诺固定来源枚举；校验头存在，身份仍由 AppID、环境和 OpenID 共同校验。
        if (source == null || source.isBlank())
            throw new ApiError(401, "请通过微信小程序 callContainer 登录");
        if (!expectedAppId.equals(appId) || !expectedEnv.equals(env))
            throw new ApiError(403, "小程序或云托管环境不匹配，请检查云资源关联配置");
        if (openid == null || !openid.matches("[A-Za-z0-9_-]{1,128}"))
            throw new ApiError(401, "未获取到微信身份，请在开发者工具中使用真实 AppID 或真机重试");
        LOG.info("Cloud login identity verified");
        return openid;
    }
}
