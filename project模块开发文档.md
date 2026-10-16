# project 模块开发文档

> 记录 project（短链接中台）模块的接口、核心机制、开发记录与已知问题。
> 接口的完整契约（请求/响应字段表）见《接口文档.md》第 3、4 节，本文不重复。
> 相关信息以磁盘真实代码为准（包名 `com.gugucat.shortlink.project`）。
> 首次建立：2026-10-16（本模块此前只有 `application.yaml` 空壳）

## 1. 模块概述

- **端口**：8001
- **源码根**：`project/src/main/java/com/gugucat/shortlink/project/`
- **职责**：短链接核心域——建链、跳转、修改、分页查询、URL 标题/图标抓取
- **接口路径前缀**：`/api/clearlink/v0.1`；**短链接跳转与提示页不带 `/api` 前缀**
- **技术栈**：Spring Boot 3.0.7 + MyBatis-Plus + ShardingSphere-JDBC + Redis/Redisson + Thymeleaf + jsoup + Nacos
- **与后管的关系**：admin 通过 Feign（Nacos 服务发现 `clearlink-project`）调用本模块；admin 自身不存短链接数据

## 2. 目录结构

```
common/{constant, convention/{result,errorcode,exception}, database, enums, web}
config/         # DataBaseConfiguration(分页拦截器)、MyMetaObjectHandler、RBloomFilterConfiguration、GotoDomainWhiteListConfiguration
controller/     # ShortLinkController、ShortLinkNotfoundController、UrlTitleController
dao/{entity, mapper}
dto/{req, resp}
service/{ShortLinkService, UrlTitleService, impl/}
toolkit/        # HashUtil(短码生成)、LinkUtil(缓存有效期/过期判断/域名解析)
resources/
  mapper/LinkMapper.xml      # pageLink 自定义 SQL
  templates/notfound.html    # 短链接不存在的提示页
```

## 3. 开发规范与代码风格（要点）

与 admin 模块一致，详见《admin模块开发文档.md》第 3 节与项目技能 `.claude/skills/dev-feature/SKILL.md`。
本模块补充两点：

- **不引入 `UserContext` / `UserTransmitInterceptor`**：本次实现的所有接口都不需要用户态。
  admin 侧也不做 `OpenFeignConfiguration` 透传用户名 —— 传过来没人读，属于「没人读的配置」。
- **外部服务调用必须设超时**：目标网站是不可控的外部依赖，`Jsoup.connect` 默认无限等待。

## 4. 功能清单

### 4.1 短链接管理（ShortLinkController）

| 方法   | 路径                              | 说明                                    | 状态  |
| ---- | ------------------------------- | ------------------------------------- | --- |
| POST | `/api/clearlink/v0.1/create`    | 创建短链接（白名单校验 → 生成短码 → 双表插入 → 缓存预热）     | ✅   |
| GET  | `/api/clearlink/v0.1/page`      | 分页查询（`gid` 必填 / `orderTag` / `current` / `size`） | ✅   |
| POST | `/api/clearlink/v0.1/update`    | 修改短链接（含跨分组迁移）                         | ✅   |
| GET  | `/{short-uri}`                  | 短链接跳转原始链接（302）                        | ✅   |
| GET  | `/page/notfound`                | 短链接不存在提示页（Thymeleaf 渲染）                | ✅   |

### 4.2 URL 标题（UrlTitleController）

| 方法  | 路径                          | 说明             | 状态  |
| --- | --------------------------- | -------------- | --- |
| GET | `/api/clearlink/v0.1/title` | 按 URL 获取网站标题   | ✅   |

### 4.3 未实现

批量创建（3.1.3）、回收站（3.2）、监控统计（3.3）、`GET /count`（3.1.6）、
`/create/by-lock` 教学对比接口（3.1.2）。

**`/create/by-lock` 是刻意留白的**：它是参考实现用来做「布隆过滤器 vs 分布式锁」性能对比的教学接口，
计划在后续重构（短码生成方案改造）中与布隆版一起替换为号段发号器，此时复现它意义不大。

## 5. 核心机制

### 5.1 分片

| 逻辑表 | 分片键 | 物理表 | 为什么选这个键 |
| --- | --- | --- | --- |
| `t_link` | `gid` | `t_link_0..15` | 分页查询按 gid 过滤，可精确路由单表 |
| `t_link_goto` | `full_short_url` | `t_link_goto_0..15` | 跳转时只有短码、没有 gid，拼出完整短链接后才是唯一可用的键 |

算法 `HASH_MOD`，`sharding-count: 16`，即 `Math.abs(分片键.hashCode()) % 16`。
**所有 `t_link` 查询都必须带 `gid`**，否则路由不到单表。

跳转必然是「两次查询」：先查 `t_link_goto` 拿 gid，再查 `t_link`。这是分片设计带来的固有代价。

### 5.2 短码生成

```
原始链接 + UUID → MurmurHash32 → Base62（6~7 位）
```

- 拼 UUID 是为了让同一原始链接每次生成的短码都不同，避免第二次必撞唯一索引
- 判重不查库，交给布隆过滤器 `shortUriCreateCachePenetrationBloomFilter`；
  它只会说「可能存在」，所以真正的唯一性由 `t_link` 的唯一索引 `UNIQUE(full_short_url, del_time)` 兜底
- 撞索引时把该短链接补进布隆再抛业务异常

### 5.3 跳转链路的四层防护

| 风险 | 手段 | 落点 |
| --- | --- | --- |
| 缓存穿透 | ① 布隆过滤器判「不存在」直接 404 不查库 ② 空值缓存 `clearlink:is-null:goto:*`（30 分钟） | `restoreUrl` |
| 缓存击穿 | Redisson 锁 `clearlink:lock:goto:*` + 锁内双重检查 | `restoreUrl` |
| 缓存预热 | 建链成功即写 `clearlink:goto:*` | `createShortLink` |
| 是否过期 | 缓存 TTL 严格取「有效期剩余毫秒」，缓存命中分支因此可以无条件放行 | `LinkUtil.getLinkCacheValidTime` |

**关键约束**：缓存的 TTL 必须被有效期「封顶」，`缓存还在` 才等价于 `还没过期`。
`LinkUtil.getLinkCacheValidTime` 对已过期的链接返回**非正数**，调用方据此不写缓存
（给 Redis 传非正过期时间会直接报错）。

### 5.4 缓存失效的触发点

缓存失效**靠写链路主动删除**，不是靠读链路校验：

- 建链：写缓存
- 修改短链接：原始链接 / 有效期变了 → 删跳转缓存；由「过期」变为「有效」→ 额外删空值缓存
- 跨分组迁移：短码与原链都没变，缓存内容仍然正确，不需要删（除非同时改了原链/有效期）

**未实现的部分**：回收站（移入回收站会置 `del_flag=1`，届时必须删缓存）尚未实现。

## 6. 开发记录

| 日期         | 功能                         | 说明                                                                                                                                     |
| ---------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-10-16 | 模块骨架 + 分表配置                | 新增启动类、`application.yaml`、`shardingsphere-config.yaml`；`t_link` 按 gid、`t_link_goto` 按 full_short_url 各分 16 表                          |
| 2026-10-16 | 创建短链接                      | `POST /create`；补 common 层、config 层、dao、dto、toolkit；缓存预热                                                                                 |
| 2026-10-16 | 分页查询短链接                    | `GET /page`；补 `DataBaseConfiguration` 的分页拦截器；`LinkMapper.xml` 的 `pageLink` 关联 `t_link_stats_today` 取今日 PV/UV/UIP；响应 domain 补 `http://` 前缀 |
| 2026-10-16 | 修改短链接（含跨分组迁移）              | `POST /update`；同分组原地更新、跨分组「旧行逻辑删除 + 新行插入 + 换 goto 的 gid」，配 `RReadWriteLock` 写锁                                                  |
| 2026-10-16 | 跳转原始链接                     | `GET /{short-uri}`；缓存命中 → 302，未命中回源后回填                                                                                                   |
| 2026-10-16 | 跳转的缓存防护与过期判断               | 布隆 + 空值缓存防穿透、Redisson 锁 + 双重检查防击穿；**修复 `getLinkCacheValidTime` 的 TTL 缺陷**（详见 7.1）                                                   |
| 2026-10-16 | 不存在时跳转提示页                  | `GET /page/notfound` + Thymeleaf；所有不可跳转分支统一收敛到 `noFound()`                                                                                |
| 2026-10-16 | 获取目标网站标题                   | `GET /title`；jsoup + 5 秒超时；异常分类转可读错误码                                                                                                   |
| 2026-10-16 | 创建/修改时抓取目标网站图标             | 解析 `<link rel~=(?i)^(shortcut )?icon]` 的 `abs:href` 存 `t_link.favicon`；分页接口返回该字段                                                         |
| 2026-10-16 | 修复缺分片键时报服务端错误              | 建链缺 `gid` 时 ShardingSphere 抛「分片条件缺失」被兜成 `B000001`，改为进库前拦下并抛 `ClientException`                                                          |
| 2026-10-16 | 修复原始链接为空仍能建链               | 空串/空格/null 都能建出跳转不到的垃圾记录，改为进库前拦下                                                                                                      |

## 7. 注意事项与已知问题

### 7.1 已修复：缓存 TTL 把「已过期」算成「很久以后才过期」

`LinkUtil.getLinkCacheValidTime` 原先用 hutool 的 `DateUtil.between(now, validDate, MS)`，
而**它返回的是绝对差值**：给 2020-01-01 会算出 `+214311977056 ms`（≈6.79 年，正数）。
一个已过期的短链接于是被缓存成「6.79 年后才过期」，叠加跳转的缓存命中分支不校验有效期，
结果是**过期链接一直能跳**。

改用 `validDate.getTime() - System.currentTimeMillis()`，已过期返回非正数，调用方据此不写缓存。
参考实现同样有此缺陷，本仓库已修正。

### 7.2 `favicon` 抓取发生在事务内

`getFavicon` 在 `createShortLink`（`@Transactional`）内部发起 HTTP 请求，
意味着**一次外部网络调用期间数据库事务是开着的**。稳态耗时约 200ms（首次调用含冷启动约 5s），
高并发下可能占用连接池。参考实现同样如此。

要彻底解决需要把「抓取元数据」从「持久化」中拆出来（进事务前抓取，或提交后异步补写）。

### 7.3 抓取外部站点的可用性差异

`HttpURLConnection`（jsoup 底层用它）对同一个域名只取 DNS 返回的**第一个地址**，不做多地址重试。
实测 `https://example.com`（4 个地址、Cloudflare）间歇性超时，而同机 `curl` 每次 0.2 秒成功。

已排除的两个猜测：① User-Agent 被 Cloudflare 拦（用 jsoup 的 UA 走 curl 稳定成功）；
② Java 优先走 IPv6（加 `-Djava.net.preferIPv4Stack=true` 后仍间歇）。

**当前结论是推断**：本机到该域名某条地址路径不稳定，`curl` 会在多地址间重试。
要解决需换成支持 Happy Eyeballs 的 HTTP 客户端（JDK 11+ 的 `HttpClient`），改动量较大。

### 7.4 抓取失败的两种处理口径（刻意不同）

| 抓取目标 | 失败时 | 为什么 |
| --- | --- | --- |
| 网站标题（`/title`） | 抛 `ClientException` | 该接口的唯一职责就是取标题，取不到必须让调用方知道 |
| 网站图标（建链/修改内联） | 返回 `null` + WARN 日志 | 图标只是界面锦上添花，不能让建链失败 |

### 7.5 分页查询不做数据归属校验

`/page` 的 `gid` 完全由调用方给，不校验它是否属于当前用户（`restoreUrl` 不涉及用户态，
`project` 侧也没有 `t_group` 的分片规则）。数据隔离目前只靠「gid 是 6 位随机串、猜不到」。

### 7.6 老行永久留在原表

跨分组迁移把旧行置 `del_flag=1`，但**没有任何清理机制**（无 TTL、无定时任务），
会随迁移次数单调增长。参考实现同样如此。

### 7.7 跳转锁没有等待超时

`restoreUrl` 里用 `lock.lock()` 无条件阻塞等待。持锁线程卡住时请求会一直等。
参考实现同样如此。

### 7.8 提示页依赖外链图片

`notfound.html` 的插图指向 `p3-live.byteimg.com`（参考实现自带）。
该主机不可达时页面会显示破图，文字部分不受影响。

### 7.9 启动与实测环境

- **Nacos 是硬依赖**：admin 与 project 都注册到 Nacos，Nacos 不在线两个服务都起不来。
  本机 `F:\DevTools\tools\nacos\nacos-2.1.1`，单机启动 `startup.cmd -m standalone`。
  **坑**：Nacos 的 Derby / Tomcat work / access log 用相对启动时 cwd 的路径，
  在项目源码目录里启动它会生成 `derby.log` / `logs/` / `work/`，启动前先切到中立目录。
- **实测配置**：`short-link.domain.default: 127.0.0.1:8001`（必须与浏览器访问短链接时的 host:port 一致）、
  `short-link.goto-domain.white-list.enable: false`（本地联调放行任意原链）。
- **建表**：`t_link_0..15`、`t_link_goto_0..15`、各统计表在数据库中已存在，本模块不负责建表。
