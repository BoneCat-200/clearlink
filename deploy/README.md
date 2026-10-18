# 部署说明

本目录提供「网关 + 聚合服务」的生产化部署方式。默认按**本机模拟线上**编写，
真实云服务器上只需要替换域名、地址与进程管理方式。

## 一、部署形态

```
浏览器
  │  http://clearlink.test（80）
  ▼
Nginx
  ├── /api/**  → Spring Cloud Gateway（8000）→ 聚合服务（8003）
  └── 其余路径 → 聚合服务（8003），短链接跳转与 404 页面
```

- **聚合服务**（`clearlink-aggregation`）：同一个 JVM 内运行 admin 与 project。
  admin 的 Feign 通过 `aggregation.remote-url` 回指本机，不再需要独立的 project 实例。
- **网关**（`clearlink-gateway`）：读取 Redis 中的登录态校验 Token，校验通过后
  把 `username/userId/realName` 透传给下游；白名单接口直接放行。
- **短链接跳转不经过网关**：跳转路径是 `/{短码}`，由 Nginx 直接转发给聚合服务。

## 二、构建

```bash
mvn -f pom.xml -pl aggregation,gateway -am clean package -DskipTests
```

产物：

- `aggregation/target/clearlink-aggregation.jar`
- `gateway/target/clearlink-gateway.jar`

## 二点五、数据库初始化

首次部署先执行号段表脚本：

```bash
mysql -h127.0.0.1 -uroot -p123456 < deploy/sql/t_segment.sql
mysql -h127.0.0.1 -uroot -p123456 < deploy/sql/t_operation_log.sql
mysql -h127.0.0.1 -uroot -p123456 < deploy/sql/t_reconcile_report.sql
```

`t_segment` 是号段发号器的元数据，**单表、不参与分片**，重复执行不会覆盖已有进度。
建链默认使用号段发号器；如需临时回滚到旧的哈希实现，启动时加
`--short-link.code.generator=hash`。

## 三、启动

启动顺序：Nacos → MySQL/Redis（本机已默认启动）→ 数据库初始化 → RocketMQ（NameServer + Broker）→ 聚合服务 → 网关。

统计消息默认走 RocketMQ，Broker 启动方式见 [rocketmq/README.md](rocketmq/README.md)。
临时回滚到 Redis Stream（零外部依赖）时，启动参数加 `--short-link.mq.type=redis-stream`。

```bash
# 聚合服务（线上 profile：域名 + 感知反向代理转发头）
java -jar aggregation/target/clearlink-aggregation.jar \
  --spring.profiles.active=prod

# 网关（aggregation profile：路由到聚合服务）
java -jar gateway/target/clearlink-gateway.jar \
  --spring.profiles.active=aggregation
```

可配置项：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SHORT_LINK_DOMAIN` | `clearlink.test` | 短链接使用的公网域名（不带协议、不带末尾斜杠） |
| `AMAP_KEY` | 空 | 高德逆地理 key；为空时地区维度统一记为「未知」 |

安全相关配置（`admin` 与 `aggregation` 的 `application.yaml` 均已内置）：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `short-link.login-security.max-fail-count` | `5` | 连续登录失败多少次后锁定 |
| `short-link.login-security.fail-window-minutes` | `15` | 失败计数的统计窗口 |
| `short-link.login-security.lock-minutes` | `15` | 锁定时长 |
| `short-link.login-security.password-min-length/max-length` | `8/32` | 新密码长度范围 |
| `short-link.idempotent.enabled` | `true` | 写接口一次性 Token 开关 |
| `short-link.idempotent.token-ttl-minutes` | `10` | Token 有效期 |

> 写接口（建链、批量建链、修改短链、分组增删改、回收站、修改用户）
> 现在必须携带一次性幂等 Token。临时回滚可加启动参数
> `--short-link.idempotent.enabled=false`。

### 网关限流、熔断与超时

网关对每条 API 路由挂三层保护：`TokenValidate → UserRateLimit → CircuitBreaker`。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `short-link.gateway.rate-limit.admin.replenish-rate` | `20` | 管理端每秒补充令牌数 |
| `short-link.gateway.rate-limit.admin.burst-capacity` | `40` | 管理端令牌桶容量（允许的瞬时突发） |
| `short-link.gateway.rate-limit.project.replenish-rate` | `100` | 中台每秒补充令牌数 |
| `short-link.gateway.rate-limit.project.burst-capacity` | `200` | 中台令牌桶容量 |
| `short-link.gateway.http.connect-timeout-ms` | `2000` | 连接下游超时 |
| `short-link.gateway.http.response-timeout` | `5s` | 等待下游响应的超时 |
| `short-link.gateway.resilience.timeout-ms` | `4000` | Resilience4j 单次调用超时（先于响应超时触发） |
| `short-link.gateway.resilience.failure-rate-threshold` | `50` | 失败率达到多少百分比后打开熔断器 |
| `short-link.gateway.resilience.wait-duration-in-open-state-ms` | `10000` | 熔断器打开时长 |
| `short-link.gateway.resilience.minimum-number-of-calls` | `10` | 窗口中至少多少次调用才开始统计失败率 |

- 限流按「已登录用户 / 未登录 IP」分桶，超限返回 HTTP 429 +
  `{"code":"A000300","message":"请求过于频繁，请稍后再试",...}`。
- 熔断或超时返回 HTTP 503 + `{"code":"C000001","message":"下游服务暂时不可用，请稍后再试",...}`。
- `RedisRateLimiter` 对 Redis 脚本执行失败的处理是 **fail-open（放行）**；
  本实现只对「策略未注册」这类配置异常返回 503。要求 Redis 故障时拒绝请求的话需要自研限流器。

### 统计对账

每天 02:10 对账前一天的统计：比较 `t_link_stats_today.today_pv` 与
`t_link_access_stats` 按小时累加的 PV。**UV/UIP 是去重指标，不参与对账**。
报告写入 `t_reconcile_report`，同一业务日期用分布式锁保证同时只有一个任务。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `short-link.reconcile.cron` | `0 10 2 * * ?` | 定时对账 cron |
| `short-link.reconcile.max-detail-size` | `200` | 报告里最多保存多少条不一致明细 |
| `short-link.reconcile.allowed-operators` | `admin` | 允许手动触发对账的用户名，逗号分隔 |

### 缓存一致性（延时双删）

修改短链、移入/恢复/移除回收站后，先立即删除一次跳转缓存，再发送一条 RocketMQ
延时消息（默认等级 2 = 5 秒）删除第二次，用于清掉并发读在竞态窗口里回填的旧值。
延时消息在数据库事务提交后发送；消费端删除是幂等的。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `short-link.cache-invalidate.enabled` | `true` | 延时双删开关；关闭后只保留第一次立即删除 |
| `short-link.cache-invalidate.delay-level` | `2` | RocketMQ 延时等级，2 对应 5 秒 |
| `short-link.cache-invalidate.topic` | `clearlink-cache-invalidate-topic` | 失效消息 Topic |
| `short-link.cache-invalidate.producer-group` | `clearlink-cache-invalidate-producer-group` | 生产者组 |
| `short-link.cache-invalidate.consumer-group` | `clearlink-cache-invalidate-consumer-group` | 消费者组 |
| `short-link.cache-invalidate.max-reconsume-times` | `8` | 删除失败最大重试次数 |

- RocketMQ 不可用时，第一次立即删除仍然生效，发送失败记录日志与
  `clearlink_cache_invalidate_send_fail_total` 指标；不会影响业务成功与否。
- 只想保留第一次删除时，加 `--short-link.cache-invalidate.enabled=false`。

`prod` profile 的关键配置见
`aggregation/src/main/resources/application-prod.yaml`：

- `server.forward-headers-strategy: native`：读取 Nginx 的 `X-Forwarded-Host/Port`，
  保证短链接按公网域名拼接，而不是内网 8003 端口；
- `short-link.domain.default`：建链时写入的域名，必须与浏览器地址栏一致。

## 四、Nginx

配置文件：`deploy/nginx/nginx.conf`。

```bash
# 在仓库根目录执行
nginx -p F:/project/clearlink/deploy/nginx/ -c nginx.conf

# 停止
nginx -p F:/project/clearlink/deploy/nginx/ -c nginx.conf -s quit
```

Nginx 监听 80 端口，`server_name` 为 `clearlink.test`。

## 五、域名

本地没有公网 DNS，用 hosts 把域名指向本机：

```
127.0.0.1 clearlink.test
```

Windows 文件位置：`C:\Windows\System32\drivers\etc\hosts`（需要管理员权限）。
真实服务器上则是在 DNS 服务商处添加 A 记录，指向服务器公网 IP。

## 六、验证

```bash
# 1. 登录（白名单接口，不需要 token）
curl -s -X POST http://clearlink.test/api/clearlink/admin/v0.1/user/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'

# 2. 未登录访问受保护接口：期望 401
curl -s -i http://clearlink.test/api/clearlink/admin/v0.1/group

# 3. 带 token 调用业务接口
curl -s "http://clearlink.test/api/clearlink/admin/v0.1/group" \
  -H 'username: admin' -H 'token: <登录返回的 token>'

# 4. 写接口先取一次性幂等 Token（有效期 10 分钟）
curl -s http://clearlink.test/api/clearlink/admin/v0.1/idempotent/token \
  -H 'username: admin' -H 'token: <token>'

# 5. 创建短链接：把上一步的 token 放进 Idempotency-Token 头
#    同一个 Token 再发一次会返回 A000201，不会重复建链
curl -s -X POST http://clearlink.test/api/clearlink/admin/v0.1/create \
  -H 'Content-Type: application/json' \
  -H 'username: admin' -H 'token: <token>' \
  -H 'Idempotency-Token: <幂等 Token>' \
  -d '{"originUrl":"https://www.baidu.com","gid":"<当前用户的分组 gid>"}'

# 6. 访问短链接：期望 302，Location 为原始链接
curl -s -i http://clearlink.test/<短码>

# 7. 运维端点（只暴露 health/info/prometheus）
curl -s http://clearlink.test/actuator/health

# 8. 限流验证：把 admin 额度临时调成 1/s、burst 1 启动网关后，
#    连续两次请求，第二次应返回 HTTP 429 与 code=A000300
curl -s -i "http://clearlink.test/api/clearlink/admin/v0.1/group" \
  -H 'username: admin' -H 'token: <token>'

# 9. 熔断验证：停掉聚合服务后持续请求，应返回 HTTP 503 与 code=C000001；
#    恢复聚合服务并等待熔断打开时长（默认 10s）后自动恢复
curl -s -i "http://clearlink.test/api/clearlink/admin/v0.1/group" \
  -H 'username: admin' -H 'token: <token>'

# 10. 熔断状态与失败率（Prometheus 指标）
curl -s http://clearlink.test/actuator/prometheus | grep resilience4j_circuitbreaker_state

# 11. 手动触发统计对账（仅白名单用户，默认 admin）
curl -s -X POST "http://clearlink.test/api/clearlink/v0.1/reconcile/run?bizDate=2026-10-17" \
  -H 'username: admin' -H 'token: <token>'

# 12. 查询对账报告
curl -s "http://clearlink.test/api/clearlink/v0.1/reconcile/page?current=1&size=10&bizDate=2026-10-17" \
  -H 'username: admin' -H 'token: <token>'

# 13. 延时双删验证：
#     修改一条已缓存的短链后，手动把旧值写回缓存（模拟并发读回填），
#     5 秒后缓存应被延时消息删除
redis-cli -h 127.0.0.1 -a 123456 SET "clearlink:goto:clearlink.test/<短码>" "旧链接" EX 600
# 等待 6~8 秒后检查，期望返回 0
redis-cli -h 127.0.0.1 -a 123456 EXISTS "clearlink:goto:clearlink.test/<短码>"
```

## 七、真实服务器部署要点

1. **收口端口**：聚合服务 8003、网关 8000 只监听内网地址，安全组/防火墙不要对公网开放；
   公网只开放 80/443。
2. **数据库与中间件**：修改 `shardingsphere-config-aggregation.yaml` 中的 MySQL 地址，
   以及 `application.yaml` 中的 Redis、Nacos 地址。
3. **进程管理**：用 systemd 或容器托管两个 jar，配置重启策略与日志轮转。
4. **HTTPS**：在 Nginx 上加 443 证书，并把 `X-Forwarded-Proto` 设为 `$scheme`（配置已带）。
5. **安全**：不要开启 `management.endpoints.web.exposure.include: '*'`；
   聚合服务与网关都不应直接暴露在公网。

## 八、回滚

停止网关与聚合服务，改回微服务形态即可：

```bash
java -jar admin/target/...   # 或原来的启动方式
java -jar project/target/...
java -jar gateway/target/clearlink-gateway.jar --spring.profiles.active=dev
```

聚合部署没有改动任何表结构与 Redis key，回滚不需要迁移数据。
