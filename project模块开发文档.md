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

### 4.2 回收站管理（RecycleBinController）

| 方法   | 路径                                          | 说明                                    | 状态  |
| ---- | ------------------------------------------- | ------------------------------------- | --- |
| POST | `/api/clearlink/v0.1/recycle-bin/save`      | 移入回收站（`enable_status 0→1`，删跳转缓存）       | ✅   |
| GET  | `/api/clearlink/v0.1/recycle-bin/page`      | 分页查询（`gidList` 必填，`enable_status=1`） | ✅   |
| POST | `/api/clearlink/v0.1/recycle-bin/recover`   | 恢复（`enable_status 1→0`，删空值缓存）          | ✅   |
| POST | `/api/clearlink/v0.1/recycle-bin/remove`    | 移除（`del_flag=1` + `del_time`，逻辑删除）     | ✅   |

### 4.3 URL 标题（UrlTitleController）

| 方法  | 路径                          | 说明             | 状态  |
| --- | --------------------------- | -------------- | --- |
| GET | `/api/clearlink/v0.1/title` | 按 URL 获取网站标题   | ✅   |

### 4.4 短链接监控（ShortLinkStatsController）

统计是**采集与落库分离**的两段，中间用 Redis Stream 传递：

| 阶段 | 触发点 | 落点 |
| --- | --- | --- |
| 采集 | 跳转成功时（三条分支各一次） | `ShortLinkServiceImpl.buildLinkStatsRecordAndSetUser` → `ShortLinkStatsSaveProducer` |
| 落库 | 消费者监听 `clearlink:stats-stream` | `ShortLinkStatsSaveConsumer.actualSaveShortLinkStats` |
| 读取 | HTTP 查询 | `ShortLinkStatsServiceImpl.oneShortLinkStats`（11 条聚合查询） |

| 方法  | 路径                            | 说明                                  | 状态  |
| --- | ----------------------------- | ----------------------------------- | --- |
| GET | `/api/clearlink/v0.1/stats`   | 单个短链接指定日期内的监控数据（总计 + 10 个维度）        | ✅   |

**分组短链接监控（`/stats/group`）与访问日志分页（`/stats/access-record`）尚未实现**。

### 4.5 未实现

批量创建（3.1.3）、分组短链接监控（3.3.2）、访问日志分页（3.3.3/3.3.4）、`GET /count`（3.1.6）、
`/create/by-lock` 教学对比接口（3.1.2）。

**`/create/by-lock` 是刻意留白的**：它是参考实现用来做「布隆过滤器 vs 分布式锁」性能对比的教学接口，
计划在后续重构（短码生成方案改造）中与布隆版一起替换为号段发号器，此时复现它意义不大。

## 5. 核心机制

### 5.1 分片

| 逻辑表 | 分片键 | 物理表 | 为什么选这个键 |
| --- | --- | --- | --- |
| `t_link` | `gid` | `t_link_0..15` | 分页查询按 gid 过滤，可精确路由单表 |
| `t_link_goto` | `full_short_url` | `t_link_goto_0..15` | 跳转时只有短码、没有 gid，拼出完整短链接后才是唯一可用的键 |
| `t_group` | `username` | `t_group_0..15` | 按用户隔离分组；**规则必须与 admin 模块完全一致**，否则同一行分组数据两边路由不到同一张物理表 |

算法 `HASH_MOD`，`sharding-count: 16`，即 `Math.abs(分片键.hashCode()) % 16`。
**所有 `t_link` 查询都必须带 `gid`**，否则路由不到单表。

`t_group` 在中台只用于校验「这个分组是不是这个用户的」（分组归属方仍然是 admin 模块）。
实测验证：`username = admin` 经中台查询路由到 `t_group_15`，与 admin 写入的位置一致。

**8 张统计表全部未分表**（都在 `ds_0`），与 `t_link`/`t_link_goto` 不同：
它们按 `full_short_url` 关联查询，没有分片键，分表反而会让读取侧的聚合更麻烦。

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

### 5.4 回收站的状态机

「在不在回收站」用 `enable_status` 表示，`del_flag` 留给「彻底删除」：

```
  正常               移入回收站              移除（逻辑删除）
enable_status=0  ──→  enable_status=1  ──→  del_flag=1
del_flag=0           del_flag=0             del_time=时间戳
                     del_time=0
        ←── 恢复 ──
```

**三条写操作互为镜像的守卫条件**，这让状态机是单向的、没有回头路：

| 操作 | WHERE 要求 | 不满足时 |
| --- | --- | --- |
| 移入回收站 | `enable_status = 0` | 拒绝（已在回收站 / 不存在 / 已彻底删除） |
| 恢复 | `enable_status = 1` | 拒绝 |
| 移除 | `enable_status = 1` | 拒绝（正常列表里的短链接不能被直接移除） |

**跳转与列表对这个字段是「天然对齐」的**，所以移入/移除/恢复不需要改动前面任何模块：

| 链路 | 条件 | 效果 |
| --- | --- | --- |
| 跳转 `restoreUrl` | `enable_status = 0 AND del_flag = 0` | 回收站与已删除的都不能跳 |
| 短链接列表 `pageLink` | `enable_status = 0` | 回收站里的不出现在正常列表 |
| 回收站列表 `pageRecycleBinLink` | `enable_status = 1 AND del_flag = 0` | 已彻底删除的不出现在回收站 |

### 5.5 缓存失效的触发点

缓存失效**靠写链路主动删除**，不是靠读链路校验：

- 建链：写缓存
- 修改短链接：原始链接 / 有效期变了 → 删跳转缓存；由「过期」变为「有效」→ 额外删空值缓存
- 跨分组迁移：短码与原链都没变，缓存内容仍然正确，不需要删（除非同时改了原链/有效期）
- **移入回收站**：删跳转缓存（不删的话，用户还能访问一个「已删除」的链接）
- **从回收站恢复**：删空值缓存（回收站期间若被访问过，会留下 30 分钟的「不存在」标记）
- **从回收站移除**：不需要处理。跳转缓存在移入时已删；此后若有人访问过，
  写下的空值缓存恰好是正确答案（这条短链接确实已经不存在了）

> **规律**：任何一个改变了「这条短链接是否存在 / 是否存在有效」的写操作，
> 都要负责把对应的缓存擦掉。空值缓存这一条已经出现两次
> （修改时限期续期、回收站恢复），说明引入空值缓存时那张清单只列了一半。

### 5.6 统计链路的写入侧

一次跳转产生一条消息，写 9 处数据：

```
跳转成功
  └─ buildLinkStatsRecordAndSetUser
       ├─ UV 首次？  Cookie 里的 uv + Redis Set clearlink:stats:uv:{fullShortUrl}
       ├─ UIP 首次？ 真实 IP      + Redis Set clearlink:stats:uip:{fullShortUrl}
       └─ 操作系统 / 浏览器 / 设备 / 网络（从 UA 与 IP 解析）
  └─ XADD clearlink:stats-stream   （消费组 clearlink:stats-stream:only-group）
  └─ ShortLinkStatsSaveConsumer.onMessage
       ├─ 幂等判定（clearlink:idempotent:{recordId}，SETNX 三态，TTL 2 分钟）
       └─ actualSaveShortLinkStats（整段包在「分组变更」读锁里）
            t_link_access_stats   (full_short_url, date, hour)  累加 pv/uv/uip
            t_link_locale_stats   (full_short_url, date, adcode, province)  累加 cnt
            t_link_os_stats       (full_short_url, date, os)     累加 cnt
            t_link_browser_stats  (full_short_url, date, browser) 累加 cnt
            t_link_device_stats   (full_short_url, date, device)  累加 cnt
            t_link_network_stats  (full_short_url, date, network) 累加 cnt
            t_link_stats_today    (full_short_url, date)         累加 today_pv/uv/uip
            t_link_access_logs    直接 insert 一行（明细，没有唯一键）
            t_link                total_pv/uv/uip 自增
```

**三处设计要点**：

1. **累加语句必须是「一条 SQL 完成 upsert」**（`INSERT ... ON DUPLICATE KEY UPDATE`）。
   改成「先查再写」在并发下会丢更新，而这些表本来就是拿唯一键换取累加能力的。
2. **整段落库包在 `RReadWriteLock` 的读锁里**，锁名 `clearlink:lock:update-gid:{fullShortUrl}`，
   与「跨分组迁移」的写锁互斥。原因：统计写 `t_link` 必须带 `gid`（分片键），
   而换分组会改写 `t_link_goto.gid`。不加锁就可能读到旧 gid，
   把更新路由到**错误的物理表并静默丢数据**。
3. **统计是旁路，任何失败都不能影响跳转**：生产端异常只记日志（跳转照常 302），
   消费端地区查询失败只把地区降级为「未知」，其余维度照常写入。

### 5.7 监控统计的读取侧

`GET /stats` 一次返回整个看板，实现是 **11 条聚合查询 + 内存拼装**，不做预聚合：

| # | 数据 | 来源 |
| --- | --- | --- |
| 1 | 每日 pv/uv/uip | `t_link_access_stats` 按 date 汇总 |
| 2 | 总计 pv/uv/uip | `t_link_access_logs` 重算（`COUNT` / `COUNT(DISTINCT)`） |
| 3 | 地区分布 | `t_link_locale_stats` 按 province 汇总 |
| 4 | 小时分布 | `t_link_access_stats` 按 hour 汇总，固定补 24 个 |
| 5 | 星期分布 | `t_link_access_stats` 按 weekday 汇总，固定补 7 个 |
| 6 | 高频 IP | `t_link_access_logs` 按 ip 分组，取 Top 5 |
| 7 | 浏览器分布 | `t_link_browser_stats` |
| 8 | 操作系统分布 | `t_link_os_stats` |
| 9 | 设备分布 | `t_link_device_stats` |
| 10 | 网络分布 | `t_link_network_stats` |
| 11 | 新老访客 | `t_link_access_logs` 子查询按访客分组后汇总 |

三个容易踩的点：

- **总计为什么从明细表重算，而不是复用累加表**：累加表按「天 + 小时」分行，
  跨天查询时要再 SUM 一遍，而 `uv`/`uip` 这种去重指标跨天 SUM 是**错的**
  （同一个人两天都来过会被算两次）。明细表的 `COUNT(DISTINCT)` 才是对的。
- **新老访客的判断刻意不带查询区间**：要区分「新」「老」必须看该访客的全部历史，
  只在区间内分组会把他一律算成新访客。
- **所有明细表的查询都必须带 gid**：`t_link` 是分片表，不带分片键路由不到单表。
  这也是接口要求传 `gid` 而不是只传短链接的原因。

**固定长度的三个数组是对前端的契约**：`hourStats` 恒为 24 个（下标即小时）、
`weekdayStats` 恒为 7 个（下标 0 对应周一）、`uvTypeStats` 恒为 2 条（newUser/oldUser）。
没有数据的档位补 0 而不是省略，前端因此不需要自己补空。

**归属校验**：`checkGroupBelongToUser` 用 `gid + username` 查 `t_group`，
校验失败抛异常。用户态来自后管经 Feign 透传的请求头（见 5.8）。

### 5.8 中台的用户态

中台自己不做鉴权，也不查用户表。「当前用户是谁」完全来自请求头：

```
后管 UserTransmitFilter（从登录态解析） → UserContext
       ↓ OpenFeignConfiguration 写请求头 username/userId/realName
中台 UserTransmitInterceptor（读请求头） → UserContext → checkGroupBelongToUser
```

**为什么必须显式透传**：后管调中台是服务间调用，不带 Cookie、也不经过网关。
这是中台第一个依赖用户态才能工作的接口，在此之前中台完全不需要知道调用者是谁。

两处的实现形式刻意不同（后管用 Filter、中台用 Interceptor）：中台要做归属校验的
只有 Controller 这一层，Filter 会对所有请求包括跳转生效，没有必要。

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
| 2026-10-16 | 回收站：移入                     | `POST /recycle-bin/save`；`enable_status 0→1` + 删跳转缓存                                                                                    |
| 2026-10-16 | 回收站：分页查询                   | `GET /recycle-bin/page`；`pageRecycleBinLink` 按 `gidList` + `enable_status=1`；gidList 为空时提前拦下（否则 `<foreach>` 生成语法错误的 `IN ()`）                   |
| 2026-10-16 | 回收站：排序改为倒序                 | `ORDER BY t.update_time` → `DESC`，让刚移入的排最前（参考实现是升序，刚删的排最后）                                                                                |
| 2026-10-16 | 回收站：恢复                     | `POST /recycle-bin/recover`；`enable_status 1→0` + 删空值缓存                                                                                 |
| 2026-10-16 | 回收站：移除（逻辑删除）               | `POST /recycle-bin/remove`；`del_flag=1` + `del_time=时间戳`，不真删、不碰统计表。至此状态机闭合                                                             |
| 2026-10-16 | 四条写操作统一「影响行数 0 即报错」        | 移入/恢复/移除在匹配不到记录时抛 `ClientException`（参考实现静默返回成功，用户会以为操作生效了）                                                                            |
| 2026-10-16 | 跳转时采集访问信息并入队               | 加 fastjson2；`LinkUtil` 补 `getActualIp`/`getOs`/`getBrowser`/`getDevice`/`getNetwork`；新增 `ShortLinkStatsRecordDTO` 与生产者；**修复 `getOs` 判断顺序缺陷**（详见 7.11）                |
| 2026-10-16 | 消费统计消息并落入监控表               | `RedisStreamConfiguration`（单线程 + 同步队列 + 丢弃最旧）＋消费组初始化任务＋幂等处理器＋消费者；8 个统计 DO/Mapper＋`LinkStatsTodayService`；`ShortLinkMapper.incrementStats`；project 补 `t_group` 分片规则；`short-link.stats.locale.amap-key` 走环境变量注入 |
| 2026-10-16 | 高德调用加超时并降级                   | 3 秒超时；失败/未配 key 时地区记「未知」而其余 7 张表照常写；省/市/编码三字段各自判空（参考实现只看 province，会让 `city`/`adcode` 的 `[]` 原样入库）                                        |
| 2026-10-16 | 修复合规：统计缺跳转记录时不再抛异常          | `t_link_goto` 查不到记录时 WARN 并跳过。抛异常会在单线程消费者上反复失败刷日志，而重试多少次都查不出不存在的记录                                                                     |
| 2026-10-16 | 中台用户态                        | 新增 `common/biz/user`（`UserContext`/`UserInfoDTO`/`UserTransmitInterceptor`）与 `config/UserConfiguration`。中台此前没有任何用户态，归属校验无从谈起                                  |
| 2026-10-16 | 单个短链接监控统计接口                  | `GET /stats`；7 个统计 Mapper 补 11 条聚合查询；9 个响应 DTO；`checkGroupBelongToUser` 做归属校验。**修复日期右边界把当天数据排除在外的缺陷**（详见 7.11）                          |
| 2026-10-16 | 监控接口补入参校验与空值兜底                | 参数缺失 / 日期格式错 / 开始晚于结束抛可读错误；总计查询返回 null 时降级为 0 而不是抛空指针（两张表不同源且落库无事务，存在中间态）                                                    |

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

`/page` 的 `gid` 完全由调用方给，不校验它是否属于当前用户（`restoreUrl` 不涉及用户态）。
数据隔离目前只靠「gid 是 6 位随机串、猜不到」。
（监控统计接口已加归属校验，见 5.7；分页查询本身仍然没有。）

### 7.6 老行永久留在原表

跨分组迁移把旧行置 `del_flag=1`，但**没有任何清理机制**（无 TTL、无定时任务），
会随迁移次数单调增长。参考实现同样如此。

### 7.7 跳转锁没有等待超时

`restoreUrl` 里用 `lock.lock()` 无条件阻塞等待。持锁线程卡住时请求会一直等。
参考实现同样如此。

### 7.8 提示页依赖外链图片

`notfound.html` 的插图指向 `p3-live.byteimg.com`（参考实现自带）。
该主机不可达时页面会显示破图，文字部分不受影响。

### 7.9 回收站相关

- **`enable_status` 语义重载**：它同时表达「是否启用」和「是否在回收站」。
  一旦将来需要「停用但不进回收站」，这个字段就表达不了了。参考实现就是这么设计的。
- **回收站里的短链接不能被修改**：`updateShortLink` 的查询条件带 `enable_status = 0`，
  对回收站里的链接调修改接口会得到「短链接记录不存在」。实测确认过（改完那条的 `describe` 未被改动）。
- **回收站查询的成本与用户分组数线性相关**：`t_link` 按 `gid` 分片，而一个用户的多个分组会落在
  随机的不同分片上（gid 是随机串）。所以单 `gid` 的列表查询恒定打 1 张表，
  而回收站的 `gid IN (...)` 有几个分组就查几张表。实测：单分组时只有 `t_link_13`，
  加第二个分组后同时打到 `t_link_13` 和 `t_link_10`。
- **没有自动清理**：`del_flag=1` 的记录永久留在表里（也没有 TTL 或定时任务）。
  与跨分组迁移产生的旧行是同一类问题。

### 7.10 启动与实测环境

- **Nacos 是硬依赖**：admin 与 project 都注册到 Nacos，Nacos 不在线两个服务都起不来。
  本机 `F:\DevTools\tools\nacos\nacos-2.1.1`，单机启动 `startup.cmd -m standalone`。
  **坑**：Nacos 的 Derby / Tomcat work / access log 用相对启动时 cwd 的路径，
  在项目源码目录里启动它会生成 `derby.log` / `logs/` / `work/`，启动前先切到中立目录。
- **实测配置**：`short-link.domain.default: 127.0.0.1:8001`（必须与浏览器访问短链接时的 host:port 一致）、
  `short-link.goto-domain.white-list.enable: false`（本地联调放行任意原链）。
- **建表**：`t_link_0..15`、`t_link_goto_0..15`、各统计表在数据库中已存在，本模块不负责建表。
- **统计落库的实测方式**：短链接访问时带上 `-H "X-Forwarded-For: <公网IP>"`
  就能让整条链路（含高德逆地理）按真实 IP 走，否则本机 `127.0.0.1` 只会得到「未知」地区。

### 7.11 统计相关

- **已修复：`getOs` 的判断顺序让移动端数据全归错档**。参考实现先判 `mac` / `linux`，
  但 iPhone 的 UA 是 `(iPhone; CPU iPhone OS 17_0 like Mac OS X)`（含 `mac`），
  Android 的是 `(Linux; Android 13; ...)`（含 `linux`），于是 iOS 被记成 Mac OS、
  Android 被记成 Linux —— **这两个维度实际上收不到任何移动端数据**。
  改为「具体在前」：`windows → android → iphone/ipad → mac → linux`。
- **UV/UIP 两个 Redis Set 永不设 TTL**（实测 `TTL = -1`），成员随独立访客数持续增长。
  不能简单加 TTL —— 那会让老访客重新变成新访客，**UV 被系统性高估**；
  正解是按天分桶或用 HyperLogLog。
- **设备识别只有 PC / Mobile 两档**；网络类型是「`192.168.`/`10.` 开头算 WIFI，其余算 Mobile」
  的粗略猜测，真实网络类型需要 IP 库或三方服务。
- **`autoAck` 与「抛异常重投」是冲突的**：容器用 `ReadOffset.lastConsumed()`（只读新消息）
  且 `autoAcknowledge(true)`，所以消息既不会进 pending、也不会被重投。
  幂等处理器里那条 `throw new ServiceException("消息未完成流程，需要消息队列重试")`
  **在当前配置下不可能真正触发重试**，实际起作用的只是「重复投递抑制」。
  这一点在后续 MQ 重构（换成 RocketMQ）时要重新处理。
- **重复投递时消息记录不会被删除**：消费成功的正常路径会 `XDEL` 掉记录以控制 `XLEN`，
  但「已处理完成」的早退分支直接 return，记录会 ACK 后仍留在 Stream 里。
  当前部署形态（单消费者 + `lastConsumed`）不会产生重复投递，所以没有实际影响；
  一旦换成会重投的 MQ，这里要补上删除。
- **`t_link_access_logs` 是增长最快的表**（一次跳转一行），且没有任何清理机制。
- **地区维度在查询失败时记「未知」而不是跳过**，是为了让地区维度的计数合计等于 PV；
  参考实现在高德返回非 10000 时直接不写这一行，会让地区分布静默少计。

### 7.12 监控统计读取侧

- **已修复：日期右边界只给到日期，当天的数据会被全部排除在外**。
  库里的时间列两类混用：`t_link_access_stats.date` 是 `DATE`，
  而 `t_link_access_logs.create_time` 是 `DATETIME`。
  拿 `'yyyy-MM-dd'` 做 `BETWEEN` 的右边界时，`DATE` 列按当天零点比较（当天算在内），
  `DATETIME` 列也按当天零点比较 —— 当天 00:00 之后的访问全部落在区间外。
  实测（同一个区间、同一批数据）：按天分布查出 `pv=5`，而总计查询命中 **0 行**。
  更严重的是总计查询带 `GROUP BY`，命中 0 行时 mapper 返回 `null`，
  参考实现紧接着就 `.getPv()`，等于「查看今日数据」这个最常用的查询直接 500。
  修法是把右边界补到 `23:59:59`（已实测：两类列的比较都变成闭区间）。
- **总计与按天分布的数字可能对不上**，因为它们读的是**两张不同的表**
  （总计读 `t_link_access_logs`，按天读 `t_link_access_stats`），
  而消费者的落库**没有加事务**。两张表之间出现中间态时（例如累加表已写、明细表未写），
  总计会降级成 0、按天分布仍然是正常值。空值兜底只保证不 500，不保证两个数自洽。
- **`/stats` 一次打 11 条 `GROUP BY` 查询，没有预聚合**。当前可接受
  （都落在按 `full_short_url` 索引的表上，区间通常是小时级），
  但不适合「全部短链接横向对比」那类查询。
- **日期区间没有长度上限**：`startDate` 到 `endDate` 跨 100 年就会生成 3 万多条
  `daily` 记录。参考实现同样没有限制。
- **归属校验失败返回的是服务端错误码**：未登录/不属于本人都是 `ServiceException`
  → `B000001`（系统执行出错），而它实际上是鉴权失败。按错误码没法把它和真正的服务端故障
  区分开，只能看 message。参考实现就是这么定的，本仓库保持一致未改。
- **响应里的占比是 0~1 的小数并保留两位**（`1/3` 会得到 `0.33`），不是百分数。
  统计口径小的维度（例如只有 3 次访问）四舍五入后占比之和可能不等于 1。
