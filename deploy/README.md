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

# 4. 创建短链接
curl -s -X POST http://clearlink.test/api/clearlink/admin/v0.1/create \
  -H 'Content-Type: application/json' \
  -H 'username: admin' -H 'token: <token>' \
  -d '{"originUrl":"https://www.baidu.com","gid":"<当前用户的分组 gid>"}'

# 5. 访问短链接：期望 302，Location 为原始链接
curl -s -i http://clearlink.test/<短码>

# 6. 运维端点（只暴露 health/info/prometheus）
curl -s http://clearlink.test/actuator/health
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
