# Bot 执行环境

Bot 的编译和执行全部在一次性 Linux 容器中完成，服务进程不会编译或加载用户代码。
容器不挂载宿主机目录、不继承服务的环境变量、不连接网络，以非 root 用户运行，
根文件系统只读，并限制 CPU、内存、进程数、临时目录和输出大小。
每次执行（含编译）最多 4 秒，超时或关闭服务时强制删除容器及其中的子进程。
Docker/镜像不可用时拒绝 Bot 匹配，真人对战不受影响；没有本机 JVM 回退模式。

在仓库根目录执行：

```sh
docker build -t kob-bot-sandbox:17 system/bot-sandbox
docker image inspect kob-bot-sandbox:17
```

容器集成测试：先设置环境变量 `KOB_RUN_SANDBOX_TESTS=true`，然后在 `backendcloud/`
执行 `mvn -pl botrunningsystem test`。测试覆盖正常 Bot、宿主机文件/网络不可达与死循环超时。
不设置此变量时，这些需要 Docker 的测试会明确跳过，其余单元测试照常运行。

运行 botrunningsystem 的账户需要能够使用 Docker Engine 的 Linux 容器。
保持 Docker 默认 seccomp 隔离，勿挂载 Docker socket、宿主机目录或开放宿主机网络。
生产环境建议把执行服务放在专用执行节点；使用 rootless Docker 时，资源限额需要
cgroup v2 和 systemd 支持。参见 [Docker 官方容器参数说明](https://docs.docker.com/engine/containers/run/)
和 [rootless 资源限额要求](https://docs.docker.com/engine/security/rootless/tips/)。

可通过 Spring 属性 `kob.bot.sandbox-command`（默认 `docker`）和
`kob.bot.sandbox-image`（默认 `kob-bot-sandbox:17`）指定管理员维护的命令和镜像。
`GET http://127.0.0.1:3002/bot/health/` 返回是否能找到镜像，接口仅允许本机调用。

前端与三个后端服务需要一起更新：匹配请求使用独立的 `mode`、`bot_id`、`match_id`；
Bot 任务和回调必须带上 `game_id` 与 `round`，旧版本回调不会被接受。
