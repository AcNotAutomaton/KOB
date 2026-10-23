# King Of Bots简称KOB

**演示地址**：http://localhost:8080/

一个策略类AI的对战平台，平台上可以发布对战项目，每个同学可以实现自己的AI，然后支持AI与AI的PK与演示。
项目成品参考[Botzone](https://www.botzone.org.cn/)———在线程序对战平台。

#### find . -name '*.java' | xargs cat | wc -l
一个查询Java文件代码总行数的命令

## Bot 执行环境与验证

Bot 编译、运行使用受限的 Linux 容器，需要先准备 Docker 并构建镜像：

```sh
docker build -t kob-bot-sandbox:17 system/bot-sandbox
```

Docker 或镜像不可用时会拒绝 Bot/人机匹配，真人对战仍可使用。
完整配置、容器集成测试和升级协议见 [Bot 部署说明](system/bot-sandbox/README.md)。
前端及三个后端服务需要一起更新。

后端在 `backendcloud/` 执行 `mvn test`；前端在 `web/` 执行 `npm test`、
`npm run lint -- --no-fix` 和 `npm run build`。

## 账号与数据库升级

生产环境设置 `KOB_PROFILE=prod`，并通过 `KOB_JWT_KEY` 注入至少32字节的独立随机密钥。
生产环境缺少密钥、使用短密钥或旧公开开发密钥时会拒绝启动。
开发/测试环境未设置密钥时使用本次进程的随机密钥，重启后需要重新登录。
切换密钥后原有登录凭证失效，这是预期行为。

已有数据库升级时执行 [用户名唯一索引迁移](system/migrations/20261023_unique_username.sql)。
迁移前应检查是否已有同名账号或等价的唯一索引；有重名时先人工处理，迁移不会删除账号。
新建数据库使用的 `system/kob.sql` 已包含该约束。密码长度按 UTF-8 编码限制为72字节。

对战中刷新或更换连接会恢复当前棋盘、蛇身及回合，旧对局结束前不能再次匹配。
结算通过 Spring 事务统一更新积分、次数与战绩；相关回归测试使用独立 H2 内存库。

不运行容器相关测试时，在 `backendcloud/` 执行：

```sh
mvn test "-Dtest=GameTest,WebSocketServerTest,MatchingPoolTest,JwtUtilTest,AccountValidationTest,PersistenceConsistencyTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```
