# 柒聿拾痕后端

微信云托管 Spring Boot 业务服务，使用 MySQL 持久化用户资料、好友、动态和点赞、打卡、日程、体重及记账数据。

部署、环境变量、接口及联调流程见 [部署说明](部署说明.md)。本项目使用 Java 17、Spring Boot 2.7.18；登录使用微信云托管网关注入的 OpenID，不使用手机号授权或演示验证码。

测试：`mvn -s build-settings.xml -gs build-settings.xml test`。

构建：`mvn -s build-settings.xml -gs build-settings.xml package`。

健康检查：`GET /api/health`。业务接口前缀：`/api`。
