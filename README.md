# 一起吃饭后端

微信云托管 Spring Boot / Java 17 / MySQL 服务，只包含一起吃饭的微信登录、好友、菜谱、共享三餐订单、图片和意见反馈。

旧笔记、动态、点赞、打卡、体重、记账、code2Session 业务代码和旧表结构已移除。所有新表使用 `cf_` 前缀，数据库名继续为 `qiyu_shihen`。

现有数据库执行工作区 `一起吃饭-数据库重建.sql`：删除原库全部表和数据，创建新表并导入18道示例菜谱与图片。请先停止旧服务，再执行脚本，最后发布新后端和前端。脚本没有自动执行。

部署步骤见 [部署说明](部署说明.md)。健康检查 `GET /api/health`；登录 `POST /api/auth/cloud`；业务 `/api/food/**`；图片上传 `/api/media/uploads`。

运行 `mvn -s build-settings.xml -gs build-settings.xml clean package` 构建并验证。前端验证在工作区根目录执行 `node tests/frontend-check.cjs`。
