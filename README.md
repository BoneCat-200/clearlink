# Clearlink 短链接系统

## 简介

Clearlink 是一个短链接系统，提供三项能力：由原始链接生成短链接、通过短链接跳转至原始链接、以及跳转行为的访问统计。

系统由四个后端模块组成，支持两种部署形态：

- **管理端（admin，端口 8002）**：面向控制台的接口入口，负责用户、分组、回收站的管理。管理端自身不存储短链接数据，通过 OpenFeign（Nacos 服务发现）调用中台。
- **中台（project，端口 8001）**：短链接核心域，负责创建短链接、短链接跳转、回收站、网页标题抓取，以及访问统计的采集与落库。
- **网关（gateway，端口 8000）**：统一入口，按 Nacos 服务名路由，并在入口处完成 Token 校验与用户信息透传。
- **聚合服务（aggregation，端口 8003）**：同一个 JVM 内同时运行 admin 与 project 的部署壳。admin 通过 `aggregation.remote-url` 让 Feign 回指本机，适合线上单进程部署。

微服务形态下，网关把请求转发给 admin / project；聚合形态下，网关只转发 API，短链接跳转由 Nginx 直接交给聚合服务。两种形态共用同一套数据库与 Redis。

核心链路：创建短链接时生成 `domain/shortUri`；访问该短链接时重定向至原始链接；每次跳转经 Redis Stream 异步写入统计表，统计维度包括 PV、UV、UIP、地域、浏览器、操作系统、设备与网络。

## 功能特性

### 用户管理（管理端）

- 用户注册：用户名唯一性由 Redisson 布隆过滤器预判，并发注册由 Redisson 分布式锁保护；注册时自动创建默认分组。
- 用户登录：校验用户名与密码，成功返回 token，有效期 30 分钟，token 保存在 Redis 中。
- 修改用户信息、检查登录状态、登出。
- 手机号与邮箱通过 ShardingSphere AES 加密后入库，其中手机号在返回时脱敏。

### 分组管理（管理端）

- 创建分组，分组标识 gid 为随机 6 位字符串。
- 查询当前用户的分组列表。
- 修改分组名称。
- 删除分组，采用逻辑删除。
- 批量修改分组排序。

### 短链接管理

- 创建短链接：校验目标域名白名单，用号段发号器 + Base62 + 仿射混淆生成定长 6 位短码，写入短链表与跳转表，预热 Redis 跳转缓存。
- 分页查询：按分组查询，可按今日或累计的 PV、UV、UIP 排序。
- 修改短链接：支持跨分组迁移。
- 短链接跳转：以 HTTP 302 重定向至原始链接，并上报一次访问统计。
- 短链接不存在时渲染提示页面。

管理端提供上述接口并转发至中台，数据与业务逻辑均在中台实现。

### 回收站

- 移入回收站、分页查询、恢复，以及移除（逻辑删除）。

### 访问统计

- 采集与落库分离：跳转时由生产者写入 Redis Stream（`clearlink:stats-stream`），消费者读取后写入统计表。
- 单条短链接在指定日期范围内的统计：合计的 PV、UV、UIP，以及按天的访问量、地域分布、按小时的访问量、访问量最高的 IP、星期分布、浏览器、操作系统、访客类型、设备、网络共 10 个维度。
- 指定分组在指定日期范围内的统计：组内全部短链接在上述维度的合计值，不包含访客类型维度。
- 单条短链接、指定分组的访问记录分页查询。
- 地域维度通过高德地图逆地理接口将访问者 IP 转换为所在省与市。

### 网页标题抓取

- 按给定的 URL 抓取目标网页的标题。

### 可靠性措施

- 缓存穿透防护：布隆过滤器判定短链接不存在时直接返回 404，不查询数据库；另设空值缓存。
- 缓存击穿防护：Redisson 分布式锁配合锁内双重检查。
- 缓存有效期与短链接有效期对齐，缓存 TTL 不超过有效期的剩余时间。
- 统计消费端采用 Redis 标记消费状态的幂等处理，避免重复落库。
- 数据分片：`t_user` 与 `t_group` 按 `username` 分片，`t_link` 按 `gid` 分片，`t_link_goto` 按 `full_short_url` 分片，均为 HASH_MOD 算法、16 张物理表。

## 技术栈

| 类别 | 技术 | 版本 |
| --- | --- | --- |
| 语言与运行时 | Java | 17 |
| 应用框架 | Spring Boot | 3.0.7 |
| 微服务框架 | Spring Cloud | 2022.0.3 |
| 微服务组件 | Spring Cloud Alibaba | 2022.0.0.0-RC2 |
| 服务注册与发现 | Nacos | — |
| 服务调用 | OpenFeign | — |
| 持久层框架 | MyBatis-Plus | 3.5.3.1 |
| 分库分表与数据加密 | ShardingSphere-JDBC | 5.3.2 |
| 数据库 | MySQL | 8（mysql-connector-j） |
| 缓存与消息队列 | Redis，Redisson | Redisson 3.21.3 |
| 消息队列实现 | Redis Stream | — |
| 模板引擎 | Thymeleaf | — |
| HTML 解析 | jsoup | 1.15.3 |
| 工具库 | Hutool | 5.8.20 |
| JSON 序列化 | fastjson2 | 2.0.36 |
| 代码生成 | Lombok | — |

## 如何快速启动

### 环境要求

| 组件 | 要求 |
| --- | --- |
| JDK | 17 |
| 构建工具 | Maven |
| MySQL | `127.0.0.1:3306`，账号 `root`，密码 `123456`，数据库名 `link` |
| Redis | `127.0.0.1:6379`，密码 `123456` |
| Nacos | 2.x，`127.0.0.1:8848`，单机模式运行 |
| RocketMQ | 4.9.5，NameServer `127.0.0.1:9876`、Broker `127.0.0.1:10911`；统计消息默认走它，启动方式见 [deploy/rocketmq/README.md](deploy/rocketmq/README.md) |

两点说明：

- Nacos 为必需依赖。管理端与中台均需注册到 Nacos，服务名分别为 `clearlink-admin` 与 `clearlink-project`；Nacos 未启动时两个服务都无法启动。
- 仓库只提供号段表脚本 `deploy/sql/t_segment.sql`。启动前需保证 `link` 数据库已存在，且分片表（`t_user_0` 至 `t_user_15`、`t_group_0` 至 `t_group_15`、`t_link_0` 至 `t_link_15`、`t_link_goto_0` 至 `t_link_goto_15`）与各统计表已创建；建链还依赖 `t_segment` 中的 `short-link:uri` 种子行。

### 编译

```bash
mvn clean install -DskipTests
```

### 启动顺序

**微服务形态（开发默认）**

1. 启动 Nacos。
2. 启动中台（端口 8001）与管理端（端口 8002）。
3. 启动网关（端口 8000，默认 `dev` profile，路由到 admin / project）。

**聚合形态（线上部署）**

1. 启动 Nacos。
2. 启动聚合服务（端口 8003，`prod` profile）。
3. 启动网关（端口 8000，`aggregation` profile，路由到聚合服务）。
4. 按 [deploy/README.md](deploy/README.md) 配置 Nginx 与域名。

> 同一时间只启动一种形态：聚合服务与独立 project 共用同一个 Redis Stream 消费组，
> 同时启动会互相争抢统计消息。

admin 与 project 均未配置 `spring-boot-maven-plugin`，命令行启动方式为：先编译，再导出依赖 classpath，然后运行主类。也可以直接由 IDE 运行主类。

启动中台：

```bash
mvn -pl project clean compile
mvn -pl project -q dependency:build-classpath -Dmdep.outputFile=project/target/cp.txt
java -cp "project/target/classes;$(cat project/target/cp.txt)" com.gugucat.shortlink.project.ShortLinkApplication
```

启动管理端：

```bash
mvn -pl admin clean compile
mvn -pl admin -q dependency:build-classpath -Dmdep.outputFile=admin/target/cp.txt
java -cp "admin/target/classes;$(cat admin/target/cp.txt)" com.gugucat.shortlink.admin.ShortLinkAdminApplication
```

classpath 分隔符在 Windows 下为分号，在 Linux 与 macOS 下为冒号。

网关与聚合服务已配置 `spring-boot-maven-plugin`，可以直接运行打包产物：

```bash
mvn -pl gateway -am package -DskipTests
java -jar gateway/target/clearlink-gateway.jar

mvn -pl aggregation -am package -DskipTests
java -jar aggregation/target/clearlink-aggregation.jar --spring.profiles.active=prod
```

### 配置项

以下配置位于中台的 `application.yaml`，本地联调时需要关注：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `short-link.domain.default` | `127.0.0.1:8001` | 生成短链接时使用的默认域名。该值必须与浏览器访问短链接时使用的 host 和端口一致：跳转时完整短链接由请求的域名与端口拼接而成，配置不一致会导致缓存与数据库均无法命中。 |
| `short-link.stats.locale.amap-key` | 空 | 高德地图应用 key，用于将访问者 IP 转换为所在省市。通过环境变量 `AMAP_KEY` 注入。未注入时跳过逆地理调用，地域维度记为「未知」，其余维度正常统计。 |
| `short-link.goto-domain.white-list.enable` | `false` | 是否启用跳转目标域名白名单。启用后仅允许跳转至 `names` 与 `details` 中列出的域名。 |

## 使用说明

### 典型流程

1. 注册用户：`POST /api/clearlink/admin/v0.1/user`。
2. 登录获取 token：`POST /api/clearlink/admin/v0.1/user/login`。
3. 查询分组列表，取得分组标识 gid（注册时已创建默认分组）：`GET /api/clearlink/admin/v0.1/group`。
4. 创建短链接：`POST /api/clearlink/admin/v0.1/create`。
5. 访问返回的完整短链接，完成至原始链接的跳转。
6. 查询访问统计：`GET /api/clearlink/admin/v0.1/stats`。

### 请求约定

- 除用户注册、用户登录、用户名查重三个接口外，其余接口都需要在请求头中携带 `Token` 与 `Username`。网关的鉴权尚未实现，直连服务时同样按此约定传递请求头，管理端据此填充当前用户的上下文信息。
- 响应体统一为 `{"code": "0", "message": null, "data": {}, "requestId": null}`，成功时 `code` 为字符串 `"0"`，失败时为对应错误码。

### 请求示例

```bash
# 注册用户
curl -X POST http://127.0.0.1:8002/api/clearlink/admin/v0.1/user \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"123456","realName":"demo"}'

# 登录，返回 data.token
curl -X POST http://127.0.0.1:8002/api/clearlink/admin/v0.1/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"123456"}'

# 查询分组列表，返回 data[].gid
curl "http://127.0.0.1:8002/api/clearlink/admin/v0.1/group" \
  -H "Username: demo" -H "Token: <token>"

# 创建短链接
curl -X POST http://127.0.0.1:8002/api/clearlink/admin/v0.1/create \
  -H "Content-Type: application/json" \
  -H "Username: demo" -H "Token: <token>" \
  -d '{"originUrl":"https://example.com","gid":"<gid>","createdType":1,"validDateType":0}'

# 访问短链接，返回 302 重定向
curl -i "http://127.0.0.1:8001/<short-uri>"
```

## API 文档

- Apifox 在线文档：<https://xhg7vl37t5.apifox.cn>
- 本地文档：[docs/接口文档.md](docs/接口文档.md)，包含各接口的请求与响应字段表、请求参数汇总、错误码以及接口清单总表。

## 项目结构

```
clearlink
├── pom.xml                     父模块，统一管理依赖版本与编码
├── admin/                      管理端服务，端口 8002
│   └── src/main/
│       ├── java/com/gugucat/shortlink/admin/
│       │   ├── ShortLinkAdminApplication.java
│       │   ├── controller/     用户、分组、短链接、回收站、监控、URL 标题接口
│       │   ├── service/        接口定义与 impl 实现
│       │   ├── dao/            entity 实体与 mapper 接口
│       │   ├── dto/            本服务的请求与响应对象
│       │   ├── remote/         调用中台的 Feign 契约及其请求、响应对象
│       │   ├── common/         统一返回体、错误码、异常、用户上下文、基础实体、常量
│       │   ├── config/         分页拦截器、字段自动填充、Feign 拦截器、布隆过滤器
│       │   └── util/
│       └── resources/          application.yaml、shardingsphere-config.yaml
├── project/                    中台服务，端口 8001
│   └── src/main/
│       ├── java/com/gugucat/shortlink/project/
│       │   ├── ShortLinkApplication.java
│       │   ├── controller/     短链接、跳转、回收站、监控、URL 标题接口
│       │   ├── service/        接口定义与 impl 实现
│       │   ├── mq/             Redis Stream 生产者、消费者与幂等处理
│       │   ├── dao/            entity 实体与 mapper 接口
│       │   ├── dto/            请求、响应与内部业务对象
│       │   ├── common/         统一返回体、错误码、异常、用户上下文、基础实体、常量
│       │   ├── config/         分页拦截器、字段自动填充、布隆过滤器、Stream 消费容器、域名白名单
│       │   ├── initialize/     Stream 消费容器初始化
│       │   └── toolkit/        短码生成、缓存有效期与域名解析
│       └── resources/
│           ├── application.yaml
│           ├── shardingsphere-config.yaml
│           ├── mapper/LinkMapper.xml        自定义分页 SQL
│           └── templates/notfound.html      短链接不存在的提示页
├── aggregation/                聚合服务，端口 8003，单 JVM 运行 admin + project
│   └── src/main/
│       ├── java/com/gugucat/shortlink/aggregation/
│       │   └── AggregationServiceApplication.java
│       └── resources/
│           ├── application.yaml                 聚合配置（合并两个模块的配置）
│           ├── application-prod.yaml            线上域名与反向代理配置
│           └── shardingsphere-config-aggregation.yaml  合并后的分片与加密规则
├── gateway/                    网关服务，端口 8000
│   └── src/main/java/com/gugucat/shortlink/gateway/
│       ├── GatewayServiceApplication.java
│       ├── filter/              Token 校验过滤器
│       ├── config/              过滤器参数
│       └── dto/                 网关错误返回体
├── deploy/                     线上部署：Nginx 配置与部署说明
└── docs/
    └── 接口文档.md             接口契约文档
```
