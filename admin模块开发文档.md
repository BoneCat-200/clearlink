# admin 模块开发文档

> 记录 admin 模块的接口、开发规范与开发记录。
> 接口的完整契约（请求/响应字段表）见《接口文档.md》第 2 节，本文不重复。
> 相关信息以磁盘真实代码为准（包名 `com.gugucat.shortlink`，非 CLAUDE.md 中的 `com.nageoffer`）。
> 最近更新：2026-10-11

## 1. 模块概述

- **端口**：8002
- **源码根**：`admin/src/main/java/com/gugucat/shortlink/admin/`
- **职责**：用户管理、分组管理（短链管理 / 回收站 / 监控统计 待开发）
- **接口路径前缀**：`/api/clearlink/admin/v0.1`
- **技术栈**：Spring Boot 3.0.7 + MyBatis-Plus + ShardingSphere-JDBC + Redis/Redisson

## 2. 目录结构

```
common/{biz/user, constant, convention/{result,errorcode,exception}, database, enums, serialize, web}
config/         # MyMetaObjectHandler、RBloomFilterConfiguration、UserConfiguration
controller/     # UserController、GroupController
dao/{entity, mapper}
dto/{req, resp}
service/{XxxService, impl/}
util/           # RandomGIDUtils
```

## 3. 开发规范与代码风格（要点）

- 分层：`controller → service 接口 → serviceImpl → mapper`；Controller 只做接参 + 调 service + `Results.success(...)`。
- 类级 Javadoc 用一句中文；service 接口方法写 `@param`/`@return`；DO/DTO 字段用反引号 SQL 风格注释。
- Lombok：`@RequiredArgsConstructor` + `private final` 注入；实体 `@Data @Builder @NoArgsConstructor @AllArgsConstructor`；返回体 `@Data @Accessors(chain = true)`。
- MyBatis-Plus：`Wrappers.lambdaQuery(Entity.class).eq(Entity::getX, v)` 方法引用写法；实体 `@TableName` + `extends BaseDO`。
- 统一返回体 `Result<T>`（成功码 `"0"`）；业务异常 `throw new ClientException(...)`，由 `GlobalExceptionHandler` 转 `Result`。
- 权限边界：涉及用户数据的查询/修改一律带 `UserContext.getUsername()` 过滤。

> 完整规范与自查清单见项目技能 `.claude/skills/dev-feature/SKILL.md`。

## 4. 功能清单

### 4.1 用户模块（UserController）

| 方法     | 路径                                            | 说明                  | 状态  |
| ------ | --------------------------------------------- | ------------------- | --- |
| GET    | `/api/clearlink/admin/v0.1/user/{username}`   | 查询用户信息（手机号脱敏）       | ✅   |
| GET    | `/api/clearlink/admin/v0.1/user/has-username` | 用户名是否存在（布隆过滤器）      | ✅   |
| POST   | `/api/clearlink/admin/v0.1/user`              | 用户注册（Redisson 锁防并发） | ✅   |
| PUT    | `/api/clearlink/admin/v0.1/user`              | 修改用户信息              | ✅   |
| POST   | `/api/clearlink/admin/v0.1/user/login`        | 登录，返回 token         | ✅   |
| GET    | `/api/clearlink/admin/v0.1/user/login/check`  | 检查登录状态              | ✅   |
| DELETE | `/api/clearlink/admin/v0.1/user/logout`       | 登出                  | ✅   |

### 4.2 分组模块（GroupController）

| 方法     | 路径                                     | 说明                      | 状态  |
| ------ | -------------------------------------- | ----------------------- | --- |
| POST   | `/api/clearlink/admin/v0.1/group`      | 创建分组（随机 6 位 gid）        | ✅   |
| GET    | `/api/clearlink/admin/v0.1/group`      | 查询当前用户分组列表              | ✅   |
| PUT    | `/api/clearlink/admin/v0.1/group`      | 修改分组名                   | ✅   |
| DELETE | `/api/clearlink/admin/v0.1/group`      | 删除分组（逻辑删除 `del_flag=1`） | ✅   |
| POST   | `/api/clearlink/admin/v0.1/group/sort` | 分组排序（批量更新 `sortOrder`）  | ✅   |

### 4.3 短链接管理（ShortLinkController）

后管自身不存短链接数据，全部经 Feign（Nacos 服务发现 `clearlink-project`）转发给中台。
中台的实现见《project模块开发文档.md》。

| 方法   | 路径                                          | 说明                              | 状态  |
| ---- | ------------------------------------------- | ------------------------------- | --- |
| POST | `/api/clearlink/admin/v0.1/create`          | 创建短链接（转发中台）                      | ✅   |
| GET  | `/api/clearlink/admin/v0.1/page`            | 分页查询（`gid` / `orderTag` / `current` / `size`） | ✅   |
| POST | `/api/clearlink/admin/v0.1/update`          | 修改短链接（含跨分组迁移）                    | ✅   |

### 4.4 URL 标题（UrlTitleController）

| 方法  | 路径                                        | 说明            | 状态  |
| --- | ----------------------------------------- | ------------- | --- |
| GET | `/api/clearlink/admin/v0.1/title`         | 按 URL 获取网站标题（转发中台抓取） | ✅   |

### 4.5 回收站管理（RecycleBinController）


单条记录的写操作后管只做转发；分页查询需要先按当前用户算出分组集合，走 `RecycleBinService`。

| 方法   | 路径                                              | 说明                                  | 状态  |
| ---- | ----------------------------------------------- | ----------------------------------- | --- |
| POST | `/api/clearlink/admin/v0.1/recycle-bin/save`    | 移入回收站（转发中台）                          | ✅   |
| GET  | `/api/clearlink/admin/v0.1/recycle-bin/page`    | 分页查询（**gidList 由服务端按当前用户分组算出并覆盖客户端传值**） | ✅   |
| POST | `/api/clearlink/admin/v0.1/recycle-bin/recover` | 从回收站恢复（转发中台）                        | ✅   |
| POST | `/api/clearlink/admin/v0.1/recycle-bin/remove`  | 从回收站移除，逻辑删除（转发中台）                   | ✅   |

> **接口文档 2.4.2 的 `gidList` 参数实际由服务端覆盖**。文档把它列为请求参数，
> 但后管一律用当前登录用户自己的分组替换它 —— 否则任何登录用户传别人的 gid 就能看到别人的回收站。
> 实测：用无分组的账号带伪造 `gidList` 查询，返回空。

### 4.6 短链接监控（ShortLinkStatsController）

后管不查监控数据，只做转发：短链接与统计都在中台，后管手里只有用户态。

| 方法  | 路径                                        | 说明                       | 状态  |
| --- | ----------------------------------------- | ------------------------ | --- |
| GET | `/api/clearlink/admin/v0.1/stats`         | 单个短链接指定日期内的监控数据（转发中台）    | ✅   |
| GET | `/api/clearlink/admin/v0.1/stats/group`   | 指定分组指定日期内的监控数据（转发中台）     | ✅   |

**用户态由 Feign 拦截器自动带上**（`config/OpenFeignConfiguration`）：中台的监控接口要做归属校验
（这个 gid 是不是这个用户的），而后管调中台不带 Cookie、也不经过网关，
不显式透传 `username`/`userId`/`realName` 三个请求头，中台就无从知道当前用户是谁。
这是本模块第一个**依赖用户态才能工作**的中台接口，也是新增这个配置的原因。

### 4.7 其它（待开发）

访问日志分页（2.5.3/2.5.4）、批量创建 + Excel 导出（2.3.2）、
`GET /count`、`GET /actual/user/{username}` 仍未实现，见《接口文档.md》第 2 节。
网关（`gateway`）仍是空壳、没有路由与鉴权，当前所有实测都是直连 8002/8001。

## 5. 开发记录

| 日期         | 功能                | 涉及文件                                                                            | 说明                                                                                                                                                                    |
| ---------- | ----------------- | ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-10-16 | 联调中台分组短链接监控统计     | `remote/dto/req/ShortLinkGroupStatsReqDTO`、`remote`、`controller/ShortLinkStatsController` | `GET /admin/v0.1/stats/group`，见 §4.6。参数比单链接版本少 `fullShortUrl` 与 `enableStatus`：统计对象是整个分组，且分组统计一律只算正常状态的短链接。用户态仍由 `OpenFeignConfiguration` 透传 |
| 2026-10-16 | 联调中台单个短链接监控统计     | `config/OpenFeignConfiguration`、`remote/dto/req/ShortLinkStatsReqDTO`、`remote/dto/resp/ShortLinkStats*RespDTO`（9 个）、`remote`、`controller/ShortLinkStatsController` | `GET /admin/v0.1/stats`，见 §4.6。**新增 `OpenFeignConfiguration`**：把 `UserContext` 的三个字段写进 Feign 请求头，中台的归属校验才能知道当前用户。9 个响应 DTO 是与中台的契约拷贝，字段名不一致会被 Jackson 静默反序列化成 null |
| 2026-10-16 | 回收站：移入 / 分页 / 恢复 / 移除 | `remote/dto/req/RecycleBin*ReqDTO`、`remote/dto/req/ShortLinkRecycleBinPageReqDTO`、`controller/RecycleBinController`、`service/RecycleBinService(+Impl)`、`remote` | 四个接口见 §4.5。分页的 gidList 由 `GroupService.listGroup()` 按当前用户算出并覆盖客户端传值；用户无分组时返回空分页（参考实现抛 `ServiceException`，语义不对）。四个写操作的影响行数为 0 时抛可读错误 |
| 2026-10-16 | 后管联调中台建链         | `pom.xml`、`ShortLinkAdminApplication`、`remote/**`、`ShortLinkController`、`application.yaml` | `POST /admin/v0.1/create`，`@FeignClient(value = "clearlink-project")` 走 Nacos 服务发现；admin 加 openfeign + nacos-discovery + loadbalancer                                       |
| 2026-10-16 | 分页查询短链接          | `remote/dto/req/ShortLinkPageReqDTO`、`remote/dto/resp/ShortLinkPageRespDTO`、`remote`、`ShortLinkController` | `GET /admin/v0.1/page`，四个查询参数透传给中台                                                                                                                                |
| 2026-10-16 | 注册时创建默认分组        | `service/GroupService`、`service/impl/GroupServiceImpl`、`service/impl/UserServicelmpl` | `saveGroup` 增加 `(username, groupName)` 重载（注册免鉴权、`UserContext` 为空，归属人必须显式传）；`register` 补 `@Transactional`。**修复了原「注册不建默认分组」的缺陷**                            |
| 2026-10-16 | 修改短链接            | `remote/dto/req/ShortLinkUpdateReqDTO`、`remote`、`ShortLinkController`              | `POST /admin/v0.1/update`。Feign 方法声明为 `Result<Void>` 而非参考实现的 `void`：中台业务异常是 HTTP 200 + 错误码返回的，`void` 会把错误码吞掉、后管误报成功                                            |
| 2026-10-16 | 获取目标网站标题         | `remote`、`controller/UrlTitleController`                                        | `GET /admin/v0.1/title`，抓取在中台完成                                                                                                                                    |
| 2026-10-16 | 修复缺 token 头即 500  | `common/biz/user/UserTransmitFilter`                                             | 请求头没有 `token` 时拿 null 查 Redis Hash 会抛 `IllegalArgumentException` 并返回非统一返回体；改为判空后退化成「未登录」                                                                            |
| 2026-10-16 | 根 pom 补 UTF-8 编码   | 根 `pom.xml`                                                                       | 原先命令行 `mvn` 按 GBK 解析中文注释会编译失败，只能靠 `-D` 参数绕过；现在无需参数                                                                                                                |
| 2026-10-16 | 修改分组名             | `dto/req/GroupUpdateReqDTO`、`GroupController`、`GroupService`、`GroupServiceImpl` | `PUT /group`，按 `gid + username + del_flag=0` 更新 `name`（仅本人、未删除的分组）                                                                                                    |
| 2026-10-11 | 路径统一加 `/admin` 前缀 | `UserController`、`GroupController`                                              | 由 `/api/clearlink/v0.1/...` 改为 `/api/clearlink/admin/v0.1/...`，与接口文档一致                                                                                                |
| 2026-10-11 | 删除分组              | `GroupController`、`GroupService`、`GroupServiceImpl`                             | `DELETE /group?gid=`，逻辑删除（`del_flag=1`，用 `LambdaUpdateWrapper`），限当前用户未删除分组；`gid` 不存在时静默返回                                                                             |
| 2026-10-11 | 分组排序              | `GroupSortReqDTO`、`GroupController`、`GroupService`、`GroupServiceImpl`           | `POST /group/sort`，请求体为 `[{gid, sortOrder}]` 数组，逐条按 `gid + username + del_flag=0` 更新 `sortOrder`；加 `@Transactional(rollbackFor = Exception.class)` 保证批量原子性（模块内首个事务方法） |
| 2026-10-11 | 修复分组查询接口 500      | `LinkGroupRespDTO`                                                              | 补 `@Data`（原缺 getter 致 Jackson 序列化失败）                                                                                                                                  |
| 2026-10-11 | `t_group` 分表配置    | `shardingsphere-config.yaml`、`GroupDO`                                          | 参照 `t_user` 按 `username` HASH_MOD 分片（`t_group_0..15`，算法 `group_table_hash_mod`）；`@TableName("t_group_0")` 改为逻辑表名 `t_group`。实测：用户落 `t_user_4`，其分院落 `t_group_4`         |

## 6. 注意事项与已知问题

1. **`dto/resp` 包重命名**：原 `dto/reps`（错拼）已重命名为 `dto/resp`，新代码一律用 `resp`。若编译报「找不到 `dto.reps`」，说明有文件 import 未同步更新。
2. **Windows 编译编码（已修复）**：根 `pom.xml` 原先未配置 `project.build.sourceEncoding=UTF-8`，命令行 `mvn compile` 默认按 GBK 解析中文注释会报「不可映射的字符」。2026-10-16 已在根 pom 统一钉死 UTF-8，**不再需要** `-Dproject.build.sourceEncoding=UTF-8 -Dmaven.compiler.encoding=UTF-8` 参数。
3. **`t_group` 已分表**：`GroupDO` 使用逻辑表名 `@TableName("t_group")`，`shardingsphere-config.yaml` 中按 `username` 做 HASH_MOD 分片（物理表 `t_group_0..15`，算法 `group_table_hash_mod`，参数与 `t_user` 一致）。同一用户的分组与用户记录落在相同分片号。分组模块所有 SQL 均带 `username`（分片键），可精确路由单分片。
4. **Lombok `@Builder` 不含继承字段**：`delFlag`/`createTime`/`updateTime` 声明在父类 `BaseDO`，`GroupDO.builder().delFlag(1)` 会编译失败。要改这些字段须用 `Wrappers.lambdaUpdate(...).set(GroupDO::getDelFlag, 1)`。
5. **路径与文档不一致（待校正）**：
   - 登录检查：文档写 `GET /user/check-login`，代码为 `GET /user/login/check`。
   - 文档 2.1.6 的 `GET /actual/user/{username}`（不脱敏）当前未实现。
6. **用户模块既有缺陷（部分已修）**：
   - ✅ 已修：`register` 未创建「默认分组」（2026-10-16）
   - ⏳ 待修：`UserServicelmpl.checkLogin` 的 `hasKey(...) != null` 恒真；`logout` 删 key 后无条件抛「用户未登录」；登录 token TTL 代码为 10 分钟、文档写 30 分钟；`/user/has-username` 返回语义与文档相反（代码返回「是否已存在」，文档说 `true` 表示可注册）
   - ⏳ 待修：`register` 在 `tryLock()` 返回 false 时会抛异常，`finally` 里再 `unlock()` 一把没持有的锁 → `IllegalMonitorStateException`。只在同一用户名并发注册时触发
7. **`UserTransmitFilter` 缺 `token` 头即 500（已修复）**：原先无条件 `opsForHash().get(key, token)`，无 `token` 头时 hashKey 为 null，Redis 抛 `IllegalArgumentException`，接口返回 Spring 默认 500 体。2026-10-16 改为判空后退化成「未登录」。
   **注意**：这**不会**把未登录请求变成 401 —— 鉴权本来就该由网关的 `TokenValidate` 完成。当前网关仍是空壳，直连 8002 时未登录/伪造 token 的请求会按接口自身是否依赖 `UserContext` 退化成 200 或业务错误码，**不是 401**。
8. **`LinkGroupRespDTO` 序列化问题（已修复）**：曾因缺 `@Data`（无 getter）导致查询分组接口 Jackson 报 `No serializer found for class LinkGroupRespDTO`、返回 500；现已补 `@Data`。**新增 DTO 一律带 `@Data`**（见 skill 第 4 节）。
9. **实测环境**：MySQL `127.0.0.1:3306`（root/123456，库 `link`）、Redis `127.0.0.1:6379` 默认已启动；admin 未配 `spring-boot-maven-plugin`，用 `java -cp` 直跑主类（见 skill 第 5 节）。
10. **启动依赖 Nacos（2026-10-16 引入）**：admin 与 project 都注册到 Nacos，**Nacos 不在线两个服务都起不来**。
    本机安装位置 `F:\DevTools\tools\nacos\nacos-2.1.1`，单机启动：
    ```bash
    cmd //c "F:\DevTools\tools\nacos\nacos-2.1.1\bin\startup.cmd -m standalone"
    ```
    **坑**：Nacos 的 embedded Derby、Tomcat work 目录、access log 用的是**相对启动时 cwd** 的路径。
    若在项目源码目录里启动它，会在该目录下生成 `derby.log` / `logs/` / `work/`。
    启动前先把 shell 的 cwd 切到中立目录（或 cd 到 Nacos 自身目录）。
11. **`/page` 不做数据归属校验**：`gid` 完全由调用方给，接口从不看 `UserContext.getUsername()`。
    实测：用 A 用户登录去查一个它从未创建过的 `gid`，照样返回该分组的短链接。
    数据隔离目前只靠「gid 是 6 位随机串、猜不到」。要修的话最小改法是 admin 转发前先查一次
    `t_group` 确认 gid 属于当前用户。
12. **`UserErrorCodeEnum` 三个码全是 B 开头**（`B000200/B000201/B000202`，语义是「服务端出错」），
    但描述的都是客户端问题；接口文档 §6 给的是 `A000110/A000111`。属既有实现。
13. **框架层异常的 HTTP 状态码被吞**：`GlobalExceptionHandler` 里 `@ExceptionHandler(Throwable.class)`
    会把 Spring 抛的 `HttpRequestMethodNotSupportedException`（405）、
    `MissingServletRequestParameterException`（400）一并接住，转成 `HTTP 200 + B000001 系统执行出错`。
    好处是响应格式统一（本项目刻意约定），代价是状态码层面的信息全丢。参考实现同样如此。
14. **JBoss/MyBatis-Plus 在 JDK 17 下的 WARN**：查询时会打印
    `Unable to make field private final java.lang.Class java.lang.invoke.SerializedLambda.capturingClass accessible`。
    无害（MyBatis-Plus 有回退路径），要消除需给 JVM 加 `--add-opens java.base/java.lang.invoke=ALL-UNNAMED`。
15. **从命令行发中文请求体会被编码搞坏**：Git Bash 按 GBK 编码把中文字节发出去，
    Spring 按 UTF-8 解析，直接报 `JSON parse error: Invalid UTF-8 start byte 0xb5`。
    这与「`grep` 中文模式匹配不到」「`redis-cli` 中文 key 查不到」是同一个根因。
    **做功能验证时用 ASCII，中文只在最后的字节级核验（HEX / od）里出现。**
16. **回收站分页的 gidList 是服务端算的**：写测试数据时要注意，
    短链接必须建在该用户**真实**的分组 gid 下，自造一个 gid 建链在回收站里是查不到的
    （短链接列表接口的 gid 是客户端传的，两者行为不同，容易搞混）。
