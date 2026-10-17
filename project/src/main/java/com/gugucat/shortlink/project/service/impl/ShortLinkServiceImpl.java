package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.project.common.constant.RedisKeyConstant;
import com.gugucat.shortlink.project.common.constant.ShortLinkConstant;
import com.gugucat.shortlink.project.common.biz.trace.TraceContext;
import com.gugucat.shortlink.project.common.biz.user.UserContext;
import com.gugucat.shortlink.project.common.convention.exception.AbstractException;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.common.convention.exception.ServiceException;
import com.gugucat.shortlink.project.common.enums.ValidDateTypeEnum;
import com.gugucat.shortlink.project.config.CreateDomainConfiguration;
import com.gugucat.shortlink.project.config.GotoDomainWhiteListConfiguration;
import com.gugucat.shortlink.project.dao.entity.GroupDO;
import com.gugucat.shortlink.project.dao.entity.LinkChangeLogDO;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import com.gugucat.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.gugucat.shortlink.project.dao.mapper.LinkChangeLogMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkGroupMapper;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkMapper;
import com.gugucat.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkBaseInfoRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkBatchCreateFailureRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkBatchCreateRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.gugucat.shortlink.project.generator.ShortCodeGenerator;
import com.gugucat.shortlink.project.metrics.LinkMetrics;
import com.gugucat.shortlink.project.mq.ShortLinkStatsMessageProducer;
import com.gugucat.shortlink.project.service.ShortLinkService;
import com.gugucat.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 短链接接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final LinkChangeLogMapper linkChangeLogMapper;
    private final LinkGroupMapper linkGroupMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ShortLinkStatsMessageProducer shortLinkStatsMessageProducer;
    private final GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;
    private final CreateDomainConfiguration createDomainConfiguration;
    // 短码生成算法通过接口注入：默认号段发号器，可通过配置切回旧的哈希实现
    private final ShortCodeGenerator shortCodeGenerator;
    private final LinkMetrics linkMetrics;

    @Value("${short-link.domain.default}")
    private String createShortLinkDefaultDomain;

    /**
     * 抓取目标网站图标的超时时间，单位毫秒
     */
    private static final int FAVICON_FETCH_TIMEOUT_MS = 5000;

    /**
     * 单次批量创建的最大条数
     * <p>
     * 不设上限的话，一次请求能把几十万条塞进来：每条都要抓一次目标网站图标、
     * 写两次库、写一次缓存，请求会长时间占着线程，也容易把下游打挂。
     */
    private static final int MAX_BATCH_CREATE_SIZE = 100;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        // gid 是 t_link 的分片键。缺了它 ShardingSphere 无法路由，会抛
        // 「Please check your sharding conditions ... routing to multiple data nodes」，
        // 最终被全局异常处理器兜成 B000001 系统执行出错 —— 但这本质是调用方参数错误，
        // 所以在进入数据库之前就拦下，给出可读提示。
        if (StrUtil.isBlank(requestParam.getGid())) {
            throw new ClientException("分组标识不能为空");
        }
        // gid 是调用方传进来的，不能直接相信：不校验归属的话，
        // 任何登录用户都能把链接建到别人的分组里（列表归对方所有，统计数据也记到对方名下）
        checkGroupBelongToUser(requestParam.getGid());
        // 请求 DTO 上没有 Bean Validation（本项目约定必填性由业务代码判定），
        // 不拦的话空串会建出一条跳转不到的垃圾短链接
        if (StrUtil.isBlank(requestParam.getOriginUrl())) {
            throw new ClientException("原始链接不能为空");
        }
        verificationWhitelist(requestParam.getOriginUrl());
        // 域名：请求里指定了就用它（必须在白名单内），否则回退默认域名
        String domain = resolveDomain(requestParam.getDomain());
        // 生成算法由 ShortCodeGenerator 的实现决定：
        // segment = 号段发号器 + Base62 + 仿射混淆；hash = 旧的随机哈希（回滚开关）
        String shortLinkSuffix = shortCodeGenerator.nextCode(domain, requestParam.getOriginUrl());
        String fullShortUrl = domain + "/" + shortLinkSuffix;
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(domain)
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreatedType())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .describe(requestParam.getDescribe())
                .shortUri(shortLinkSuffix)
                .enableStatus(0)
                .totalPv(0)
                .totalUv(0)
                .totalUip(0)
                .delTime(0L)
                .fullShortUrl(fullShortUrl)
                .favicon(getFavicon(requestParam.getOriginUrl()))
                .build();
        ShortLinkGotoDO linkGotoDO = ShortLinkGotoDO.builder()
                .fullShortUrl(fullShortUrl)
                .gid(requestParam.getGid())
                .build();
        try {
            // 两张表分片键不同，这两条插入会落到各自的物理表：
            // t_link 按 gid 路由，t_link_goto 按 full_short_url 路由
            baseMapper.insert(shortLinkDO);
            shortLinkGotoMapper.insert(linkGotoDO);
        } catch (DuplicateKeyException ex) {
            // 号段发号器本身不会发出重复 ID，理论上不会走到这里。
            // 唯一的可能是新短码与遗留的哈希短码撞了（概率极低），或数据被人工改动过。
            // 唯一索引 (full_short_url, del_time) 是最后一道防线；这里不重试 ——
            // 在同一事务里捕获后再插一次，可能把前一条已插入的行一起提交，造成脏数据。
            if (!shortUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)) {
                shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
            }
            throw new ServiceException(String.format("短链接：%s 生成重复，请重试", fullShortUrl));
        }
        // 缓存预热：创建时就把跳转映射写进 Redis，避免短链接刚发出就被访问时的一次缓存穿透。
        // 有效期已经过去的短链接不写缓存 —— getLinkCacheValidTime 会返回非正数，Redis 不接受非正的过期时间
        long cacheValidTime = LinkUtil.getLinkCacheValidTime(requestParam.getValidDate());
        if (cacheValidTime > 0) {
            stringRedisTemplate.opsForValue().set(
                    String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl),
                    requestParam.getOriginUrl(),
                    cacheValidTime,
                    TimeUnit.MILLISECONDS
            );
        }
        shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
        linkMetrics.recordCreate();
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + shortLinkDO.getFullShortUrl())
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        // fullShortUrl / originGid / gid 都是查询或分片条件，缺一个就会在数据库层报出
        // 难懂的「分片条件缺失」，所以在进库之前统一拦掉
        if (StrUtil.isBlank(requestParam.getFullShortUrl())
                || StrUtil.isBlank(requestParam.getOriginGid())
                || StrUtil.isBlank(requestParam.getGid())) {
            throw new ClientException("完整短链接与分组标识不能为空");
        }
        if (StrUtil.isBlank(requestParam.getOriginUrl())) {
            throw new ClientException("原始链接不能为空");
        }
        verificationWhitelist(requestParam.getOriginUrl());
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO hasShortLinkDO = baseMapper.selectOne(queryWrapper);
        if (hasShortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }
        if (Objects.equals(hasShortLinkDO.getGid(), requestParam.getGid())) {
            // 分组没变：原地更新即可，只 SET 目标字段，domain、shortUri、统计数据一律不动
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    // 改成「永久有效」时必须显式把 valid_date 置空：MyBatis-Plus 会跳过实体里的 null 字段，
                    // 只靠 builder 传 null 是清不掉这一列的
                    .set(Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType()),
                            ShortLinkDO::getValidDate, null);
            ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                    .domain(hasShortLinkDO.getDomain())
                    .shortUri(hasShortLinkDO.getShortUri())
                    .createdType(hasShortLinkDO.getCreatedType())
                    .gid(requestParam.getGid())
                    .originUrl(requestParam.getOriginUrl())
                    .describe(requestParam.getDescribe())
                    .validDateType(requestParam.getValidDateType())
                    .validDate(requestParam.getValidDate())
                    // 原始链接没变就沿用原来的图标，避免每次改描述都去抓一次外部站点
                    .favicon(Objects.equals(requestParam.getOriginUrl(), hasShortLinkDO.getOriginUrl())
                            ? hasShortLinkDO.getFavicon()
                            : getFavicon(requestParam.getOriginUrl()))
                    .build();
            baseMapper.update(shortLinkDO, updateWrapper);
        } else {
            // 分组变了：gid 是 t_link 的分片键，物理表跟着变，没法 UPDATE 过去，
            // 只能「旧行逻辑删除 + 新行插入」。用写锁把这一步和统计写入互斥住，
            // 否则迁移过程中统计可能落到正在被删的那一行上
            RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(RedisKeyConstant.LOCK_GID_UPDATE_KEY, requestParam.getFullShortUrl()));
            RLock rLock = readWriteLock.writeLock();
            rLock.lock();
            try {
                LambdaUpdateWrapper<ShortLinkDO> linkUpdateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                        .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(ShortLinkDO::getGid, hasShortLinkDO.getGid())
                        .eq(ShortLinkDO::getDelFlag, 0)
                        .eq(ShortLinkDO::getDelTime, 0L)
                        .eq(ShortLinkDO::getEnableStatus, 0);
                ShortLinkDO delShortLinkDO = ShortLinkDO.builder()
                        .delTime(System.currentTimeMillis())
                        .build();
                delShortLinkDO.setDelFlag(1);   // delFlag 在父类 BaseDO 上，Lombok @Builder 覆盖不到
                baseMapper.update(delShortLinkDO, linkUpdateWrapper);

                // 新行沿用原短码（full_short_url 不变），只换 gid；
                // 唯一索引是 (full_short_url, del_time)，旧行 del_time 已改成时间戳，所以不会撞
                ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                        .domain(createShortLinkDefaultDomain)
                        .originUrl(requestParam.getOriginUrl())
                        .gid(requestParam.getGid())
                        .createdType(hasShortLinkDO.getCreatedType())
                        .validDateType(requestParam.getValidDateType())
                        .validDate(requestParam.getValidDate())
                        .describe(requestParam.getDescribe())
                        .shortUri(hasShortLinkDO.getShortUri())
                        .enableStatus(hasShortLinkDO.getEnableStatus())
                        .totalPv(hasShortLinkDO.getTotalPv())
                        .totalUv(hasShortLinkDO.getTotalUv())
                        .totalUip(hasShortLinkDO.getTotalUip())
                        .fullShortUrl(hasShortLinkDO.getFullShortUrl())
                        .delTime(0L)
                        .favicon(Objects.equals(requestParam.getOriginUrl(), hasShortLinkDO.getOriginUrl())
                                ? hasShortLinkDO.getFavicon()
                                : getFavicon(requestParam.getOriginUrl()))
                        .build();
                baseMapper.insert(shortLinkDO);

                // 跳转表按 full_short_url 分片，短码没变所以还是同一张物理表，换掉 gid 即可
                LambdaQueryWrapper<ShortLinkGotoDO> linkGotoQueryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                        .eq(ShortLinkGotoDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(ShortLinkGotoDO::getGid, hasShortLinkDO.getGid());
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(linkGotoQueryWrapper);
                shortLinkGotoMapper.delete(linkGotoQueryWrapper);
                shortLinkGotoDO.setGid(requestParam.getGid());
                shortLinkGotoMapper.insert(shortLinkGotoDO);

                // 记一笔变更历史。旧行虽然置了 del_flag=1 并保留旧 gid，但它落在
                // **旧分组**的分片表里 —— 按新 gid 查询时根本看不到那张物理表，
                // 所以「这条短链接曾经在哪个分组」只能靠这张表回答。
                // 写在写锁内、与换组同事务：换组失败就不该留下一条「变更成功」的记录
                linkChangeLogMapper.insert(LinkChangeLogDO.builder()
                        .fullShortUrl(requestParam.getFullShortUrl())
                        .originGid(hasShortLinkDO.getGid())
                        .currentGid(requestParam.getGid())
                        .operator(UserContext.getUsername())
                        .build());
            } finally {
                rLock.unlock();
            }
        }
        // 缓存与数据库一致性：原始链接或有效期变了，缓存里那份就是错的，直接删掉让下次访问回源重建
        if (!Objects.equals(hasShortLinkDO.getValidDateType(), requestParam.getValidDateType())
                || !Objects.equals(hasShortLinkDO.getValidDate(), requestParam.getValidDate())
                || !Objects.equals(hasShortLinkDO.getOriginUrl(), requestParam.getOriginUrl())) {
            stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
            // 原来是过期状态、改完之后变成有效（改成永久或改成将来的时间）时，
            // 还得把「不存在」的空值缓存也删掉，否则它会在剩余的 TTL 内继续把跳转请求挡在门外
            if (LinkUtil.isExpired(hasShortLinkDO.getValidDate()) && !LinkUtil.isExpired(requestParam.getValidDate())) {
                stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
            }
        }
    }

    @SneakyThrows   // sendRedirect 声明了受检的 IOException，而接口方法没有 throws
    @Override
    public void restoreUrl(String shortUri, ServletRequest request, ServletResponse response) {
        // 请求进来时手上只有短码，先把「host:port/短码」拼回完整短链接：
        // 这才是缓存 key 与唯一索引用的标识
        String serverName = request.getServerName();
        String serverPort = Optional.of(request.getServerPort())
                .filter(each -> !Objects.equals(each, 80))   // 默认端口不参与拼接，避免与建链时存的域名对不上
                .map(String::valueOf)
                .map(each -> ":" + each)
                .orElse("");
        String fullShortUrl = serverName + serverPort + "/" + shortUri;
        linkMetrics.recordRedirectRequest();
        String gotoShortLinkKey = String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl);
        String gotoIsNullShortLinkKey = String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);
        // 第一条路径：缓存命中，直接重定向，一次数据库都不碰
        String originalLink = stringRedisTemplate.opsForValue().get(gotoShortLinkKey);
        if (StrUtil.isNotBlank(originalLink)) {
            linkMetrics.recordCacheHit();
            recordStatsQuietly(fullShortUrl, request, response);
            ((HttpServletResponse) response).sendRedirect(originalLink);
            return;
        }
        // 第一次查缓存没命中；后续的双重检查命中属于并发竞态，不计入命中率
        linkMetrics.recordCacheMiss();
        // 防缓存穿透第一道：布隆过滤器说「一定不存在」时就不用查库了。
        // 攻击者拿随机短码刷接口时，请求会全部停在这里，数据库不受影响
        if (!shortUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)) {
            noFound(response);
            return;
        }
        // 防缓存穿透第二道：空值缓存。布隆过滤器只会说「可能存在」，
        // 若不做这层，一个不存在的短码每次都要放行到查库，等于穿透了缓存
        if (StrUtil.isNotBlank(stringRedisTemplate.opsForValue().get(gotoIsNullShortLinkKey))) {
            noFound(response);
            return;
        }
        // 防缓存击穿：某个热点短链接的缓存刚失效时，并发请求会同时扑向数据库。
        // 加锁让它们排队，只有一个真去查库，其余的等锁释放后由双重检查接住
        RLock lock = redissonClient.getLock(String.format(RedisKeyConstant.LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try {
            // 双重检查：等锁这段时间里，缓存可能已经被拿到锁的那个线程填好了
            originalLink = stringRedisTemplate.opsForValue().get(gotoShortLinkKey);
            if (StrUtil.isNotBlank(originalLink)) {
                recordStatsQuietly(fullShortUrl, request, response);
                ((HttpServletResponse) response).sendRedirect(originalLink);
                return;
            }
            if (StrUtil.isNotBlank(stringRedisTemplate.opsForValue().get(gotoIsNullShortLinkKey))) {
                noFound(response);
                return;
            }
            // 缓存未命中：先用 t_link_goto 拿 gid。这张表按 full_short_url 分片，
            // 正因为跳转时只有短码、没有 gid，才需要这张「短链接 → gid」的索引表
            LambdaQueryWrapper<ShortLinkGotoDO> linkGotoQueryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(linkGotoQueryWrapper);
            if (shortLinkGotoDO == null) {
                cacheNullShortLink(gotoIsNullShortLinkKey);
                noFound(response);
                return;
            }
            // 拿 gid 之后才能精确路由到 t_link 的某一张物理表
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, shortLinkGotoDO.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);
            // 记录不存在，或者已过有效期，都不能跳转
            if (shortLinkDO == null || LinkUtil.isExpired(shortLinkDO.getValidDate())) {
                cacheNullShortLink(gotoIsNullShortLinkKey);
                noFound(response);
                return;
            }
            // 回填缓存，TTL 取有效期剩余时间，让缓存的自然过期时间与短链接的失效时间对齐。
            // 对齐之后「缓存还在」就等价于「还没过期」，上面的缓存命中分支才敢直接放行
            long cacheValidTime = LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate());
            if (cacheValidTime > 0) {
                stringRedisTemplate.opsForValue().set(gotoShortLinkKey, shortLinkDO.getOriginUrl(), cacheValidTime, TimeUnit.MILLISECONDS);
            }
            ((HttpServletResponse) response).sendRedirect(shortLinkDO.getOriginUrl());
            recordStatsQuietly(fullShortUrl, request, response);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 采集本次访问的统计信息，并在 Redis 里记下访客与 IP
     * <p>
     * 只在写响应之前调用：UV 靠 Cookie 判断，而给浏览器种 Cookie 是「写响应」的一部分。
     *
     * @param fullShortUrl 完整短链接
     * @param request      HTTP 请求
     * @param response     HTTP 响应
     * @return 本次访问的统计信息
     */
    private ShortLinkStatsRecordDTO buildLinkStatsRecordAndSetUser(String fullShortUrl, ServletRequest request, ServletResponse response) {
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        AtomicReference<String> uv = new AtomicReference<>();
        // 浏览器没带 uv Cookie 时的处理：生成一个新的、种回去、并直接认定这是首次访问
        Runnable addResponseCookieTask = () -> {
            uv.set(UUID.fastUUID().toString());
            Cookie uvCookie = new Cookie("uv", uv.get());
            uvCookie.setMaxAge(60 * 60 * 24 * 30);   // 30 天
            // Cookie 的作用范围收窄到这条短链接自己的路径，不同短链接之间的访客标识互不干扰
            uvCookie.setPath(StrUtil.sub(fullShortUrl, fullShortUrl.indexOf("/"), fullShortUrl.length()));
            ((HttpServletResponse) response).addCookie(uvCookie);
            uvFirstFlag.set(Boolean.TRUE);
            stringRedisTemplate.opsForSet().add(RedisKeyConstant.SHORT_LINK_STATS_UV_KEY + fullShortUrl, uv.get());
        };
        if (ArrayUtil.isNotEmpty(cookies)) {
            Arrays.stream(cookies)
                    .filter(each -> Objects.equals(each.getName(), "uv"))
                    .findFirst()
                    .map(Cookie::getValue)
                    .ifPresentOrElse(each -> {
                        uv.set(each);
                        // 往 Set 里加成功（返回 1）说明这个访客之前没来过
                        Long uvAdded = stringRedisTemplate.opsForSet().add(RedisKeyConstant.SHORT_LINK_STATS_UV_KEY + fullShortUrl, each);
                        uvFirstFlag.set(uvAdded != null && uvAdded > 0L);
                    }, addResponseCookieTask);
        } else {
            addResponseCookieTask.run();
        }
        String remoteAddr = LinkUtil.getActualIp(((HttpServletRequest) request));
        String os = LinkUtil.getOs(((HttpServletRequest) request));
        String browser = LinkUtil.getBrowser(((HttpServletRequest) request));
        String device = LinkUtil.getDevice(((HttpServletRequest) request));
        String network = LinkUtil.getNetwork(((HttpServletRequest) request));
        // 同一个 IP 反复访问时只有第一次算 UIP
        Long uipAdded = stringRedisTemplate.opsForSet().add(RedisKeyConstant.SHORT_LINK_STATS_UIP_KEY + fullShortUrl, remoteAddr);
        boolean uipFirstFlag = uipAdded != null && uipAdded > 0L;
        return ShortLinkStatsRecordDTO.builder()
                .fullShortUrl(fullShortUrl)
                // 一次跳转一个事件 ID：RocketMQ 生产者用它做消息 Key，消费端用它做幂等键
                .eventId(UUID.fastUUID().toString())
                // 跳转请求的 traceId 跟着消息走，消费端的统计日志才能和跳转日志串起来
                .traceId(TraceContext.getTraceId())
                .uv(uv.get())
                .uvFirstFlag(uvFirstFlag.get())
                .uipFirstFlag(uipFirstFlag)
                .remoteAddr(remoteAddr)
                .os(os)
                .browser(browser)
                .device(device)
                .network(network)
                .currentDate(new Date())
                .build();
    }

    @Override
    public void shortLinkStats(ShortLinkStatsRecordDTO statsRecord) {
        // 只投递不落库：跳转是最高频的操作，统计的写库动作交给消费者异步做。
        // 具体走 Redis Stream 还是 RocketMQ 由 short-link.mq.type 决定，
        // 这里只依赖接口 —— 换 MQ 不需要改跳转链路
        shortLinkStatsMessageProducer.send(statsRecord);
    }

    /**
     * 采集并上报本次访问的统计，<b>失败只记日志，不让异常冒出去</b>
     * <p>
     * 为什么必须包一层：采集里有好几处会抛异常的地方 —— 解析 UA、读 Cookie、
     * 往 Redis 的 UV/UIP 集合里写。而这几处都在 {@code sendRedirect} <b>之前</b>执行，
     * 一旦抛异常，跳转就被打断了：用户点短链接，得到的是一个 200 空响应，
     * 链接完全打不开。
     * <p>
     * 实测过的真实案例：请求不带 {@code User-Agent} 时，
     * {@code LinkUtil.getOs} 对 null 调 {@code toLowerCase()} 抛 NPE，
     * 短链接的响应码从 302 变成 200。
     * <p>
     * 所以这里的原则是：<b>统计是旁路，任何失败都不能影响跳转</b>。
     * 用 {@code Throwable} 而不是 {@code Exception}，连 Error 一起兜住。
     *
     * @param fullShortUrl 完整短链接
     * @param request      HTTP 请求
     * @param response     HTTP 响应
     */
    private void recordStatsQuietly(String fullShortUrl, ServletRequest request, ServletResponse response) {
        try {
            shortLinkStats(buildLinkStatsRecordAndSetUser(fullShortUrl, request, response));
        } catch (Throwable ex) {
            log.error("采集短链接访问统计失败，本次跳转不受影响。fullShortUrl: {}", fullShortUrl, ex);
        }
    }

    /**
     * 短链接不可跳转时的响应：重定向到提示页
     * <p>
     * 用 302 而不是 404：用户点的是一个短链接，看到一张「页面不存在」的页面比看到一个
     * 浏览器的默认 404 页更有信息量，也便于后续在提示页上做引导。
     * 所有「不可跳转」的分支都收敛到这里，改文案或改目标页只需要动这一处。
     *
     * @param response HTTP 响应
     */
    @SneakyThrows   // sendRedirect 声明了受检的 IOException
    private void noFound(ServletResponse response) {
        linkMetrics.recordNotFound();
        ((HttpServletResponse) response).sendRedirect("/page/notfound");
    }

    /**
     * 缓存「该短链接不存在」的标记，避免同一个不存在的短码反复穿透到数据库
     *
     * @param gotoIsNullShortLinkKey 空值缓存 Key
     */
    private void cacheNullShortLink(String gotoIsNullShortLinkKey) {
        stringRedisTemplate.opsForValue().set(
                gotoIsNullShortLinkKey, "-", ShortLinkConstant.NULL_CACHE_VALID_TIME, TimeUnit.MINUTES);
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        IPage<ShortLinkDO> resultPage = baseMapper.pageLink(requestParam);
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            // 库里存的是不带协议的 domain（127.0.0.1:8001），前端要直接当链接用，这里补上协议
            result.setDomain("http://" + result.getDomain());
            return result;
        });
    }

    /**
     * 抓取目标网站的图标地址
     * <p>
     * 与「获取目标网站标题」不同，这里抓不到不能报错：图标只是界面上的锦上添花，
     * 目标站点不可达、超时、页面里根本没写 icon，都不该让建链失败。
     * 所以异常在这里就地消化，统一返回 null。
     *
     * @param url 原始链接
     * @return 图标的绝对地址；抓不到返回 null
     */
    private String getFavicon(String url) {
        try {
            // 与 UrlTitleServiceImpl 一样只发一次请求：jsoup 遇到非 2xx 会抛异常，
            // 不需要先另发一个请求探响应码（参考实现那样会把目标站点的请求量翻倍）
            Document document = Jsoup.connect(url).timeout(FAVICON_FETCH_TIMEOUT_MS).get();
            // rel 的取值有 "icon"、"shortcut icon" 等多种写法（大小写也不统一），
            // 用正则统一匹配；"apple-touch-icon" 不以 icon 开头，不会被选中
            Element faviconLink = document.select("link[rel~=(?i)^(shortcut )?icon]").first();
            if (faviconLink != null) {
                // 用 abs:href 而不是 href：页面里写的往往是 "/img/favicon.png" 这种相对路径，
                // jsoup 会按文档地址补成完整 URL，前端才能直接拿来当图片地址
                return faviconLink.attr("abs:href");
            }
        } catch (Exception ex) {
            // 只记日志不往上抛。页面没有 icon 属于正常情况（不打印），抓取失败才值得留痕
            log.warn("抓取目标网站图标失败，原始链接：{}", url, ex);
        }
        return null;
    }

    /**
     * 批量创建短链接
     * <p>
     * 逐条复用单条创建，某一条失败不影响其它条。
     */
    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        List<String> originUrls = requestParam.getOriginUrls();
        if (CollUtil.isEmpty(originUrls)) {
            throw new ClientException("原始链接不能为空");
        }
        if (originUrls.size() > MAX_BATCH_CREATE_SIZE) {
            throw new ClientException("单次批量创建不能超过 " + MAX_BATCH_CREATE_SIZE + " 条");
        }
        List<String> describes = Optional.ofNullable(requestParam.getDescribes()).orElse(List.of());
        // 原始链接与描述是两个平行集合、按下标对应。长度不一致时第 i 条会拿到别人的描述，
        // 或者直接下标越界（参考实现就是这样：describes 短了会抛 IndexOutOfBounds，
        // 整批在循环中途失败，前面已建的也白建了 —— 所以在循环前就拦下）
        if (!describes.isEmpty() && describes.size() != originUrls.size()) {
            throw new ClientException("描述数量与原始链接数量不一致");
        }
        // 批量里每一条用的都是同一个 gid，在循环前校验一次即可：
        // 既避免每条都报同一个错误，也避免 N 次重复查询
        checkGroupBelongToUser(requestParam.getGid());
        List<ShortLinkBaseInfoRespDTO> baseLinkInfos = new ArrayList<>(originUrls.size());
        List<ShortLinkBatchCreateFailureRespDTO> failures = new ArrayList<>();
        for (int i = 0; i < originUrls.size(); i++) {
            String originUrl = originUrls.get(i);
            String describe = describes.isEmpty() ? null : describes.get(i);
            // 逐条复用单条创建：校验、域名解析、短码生成、双表插入、缓存预热、布隆都是一套逻辑，
            // 批量只是把它们循环一遍。
            // 注意：这里属于自调用，createShortLink 上的 @Transactional 不会生效（Spring 代理被绕过），
            // 所以单条的两次插入没有原子性。风险已知并登记在开发文档里，本步骤不改事务结构。
            ShortLinkCreateReqDTO createReqDTO = BeanUtil.toBean(requestParam, ShortLinkCreateReqDTO.class);
            createReqDTO.setOriginUrl(originUrl);
            createReqDTO.setDescribe(describe);
            try {
                ShortLinkCreateRespDTO created = createShortLink(createReqDTO);
                baseLinkInfos.add(ShortLinkBaseInfoRespDTO.builder()
                        .describe(describe)
                        .originUrl(created.getOriginUrl())
                        .fullShortUrl(created.getFullShortUrl())
                        .build());
            } catch (Throwable ex) {
                // 单条失败不能影响其它条：批量的语义是「尽量多建几条」。
                // 参考实现这里只打日志、把失败项丢掉，调用方不知道哪条没建成、为什么
                log.error("批量创建短链接失败，原始链接：{}", originUrl, ex);
                failures.add(ShortLinkBatchCreateFailureRespDTO.builder()
                        .originUrl(originUrl)
                        .reason(ex instanceof AbstractException ? ((AbstractException) ex).getErrorMessage() : ex.getMessage())
                        .build());
            }
        }
        return ShortLinkBatchCreateRespDTO.builder()
                .total(baseLinkInfos.size())
                .baseLinkInfos(baseLinkInfos)
                .failures(failures)
                .build();
    }

    /**
     * 解析本次建链使用的域名
     * <p>
     * 请求里指定了域名就用它，但必须命中 `short-link.domain.white-list`；
     * 没指定就用默认域名。
     * <p>
     * 为什么要白名单：短链接的 domain 会同时写进 `t_link.domain` 和
     * `full_short_url`，而跳转时是用「请求的 host:port + 短码」拼回来查的。
     * 允许任意域名，等于让别人用我们的服务造出一批指向他们域名（或不存在域名）的
     * 短链接 —— 这些链接根本跳不通，还可能被当成跳板。
     *
     * @param requestedDomain 请求里指定的域名，可为空
     * @return 归一化后的域名
     */
    private String resolveDomain(String requestedDomain) {
        String domain = normalizeDomain(requestedDomain);
        if (StrUtil.isBlank(domain)) {
            return createShortLinkDefaultDomain;
        }
        List<String> whiteList = createDomainConfiguration.getWhiteList();
        if (CollUtil.isEmpty(whiteList) || !whiteList.contains(domain)) {
            // 白名单为空时连默认域名之外的都不放行 —— 缺配置时应当「谁都不许」而不是「谁都行」
            throw new ClientException("不支持的域名：" + domain);
        }
        return domain;
    }

    /**
     * 归一化域名：去掉协议前缀、首尾空白与末尾斜杠
     * <p>
     * 调用方常把 `https://example.com/` 整个当域名传进来，而库里存的是
     * `example.com`（不带协议、不带末尾斜杠）。不归一化就会拼出
     * `https://example.com//abc` 这种既不合法、也和跳转时拼出来的标识对不上的值。
     *
     * @param domain 原始域名
     * @return 归一化后的域名；入参为空白时返回 null
     */
    private String normalizeDomain(String domain) {
        if (StrUtil.isBlank(domain)) {
            return null;
        }
        String result = domain.trim();
        if (result.startsWith("https://")) {
            result = result.substring("https://".length());
        } else if (result.startsWith("http://")) {
            result = result.substring("http://".length());
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 校验分组是否属于当前登录用户
     * <p>
     * 调用方（后管）把 gid 放在请求体里，中台只信任请求头里由后管透传的用户名。
     * 查询条件是 gid + username，命中分片键，只会路由到一张物理表。
     *
     * @param gid 分组标识
     * @throws ClientException 分组标识为空、用户未登录或分组不属于当前用户
     */
    private void checkGroupBelongToUser(String gid) {
        if (StrUtil.isBlank(gid)) {
            throw new ClientException("分组标识不能为空");
        }
        String username = UserContext.getUsername();
        if (StrUtil.isBlank(username)) {
            // 中台只被后管调用；拿不到用户名说明请求没带 username 请求头，属于调用方用错接口
            throw new ClientException("用户未登录");
        }
        Long groupCount = linkGroupMapper.selectCount(Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, username)
                .eq(GroupDO::getDelFlag, 0));
        if (groupCount == null || groupCount == 0) {
            throw new ClientException("分组不存在或不属于当前用户");
        }
    }

    /**
     * 校验原始链接是否在跳转域名白名单内
     *
     * @param originUrl 原始链接
     */
    private void verificationWhitelist(String originUrl) {
        Boolean enable = gotoDomainWhiteListConfiguration.getEnable();
        if (enable == null || !enable) {
            return;
        }
        String domain = LinkUtil.extractDomain(originUrl);
        if (StrUtil.isBlank(domain)) {
            throw new ClientException("跳转链接填写错误");
        }
        List<String> details = gotoDomainWhiteListConfiguration.getDetails();
        if (!details.contains(domain)) {
            throw new ClientException("演示环境为避免恶意攻击，请生成以下网站跳转链接：" + gotoDomainWhiteListConfiguration.getNames());
        }
    }
}
