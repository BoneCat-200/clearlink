package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.project.common.constant.RedisKeyConstant;
import com.gugucat.shortlink.project.common.constant.ShortLinkConstant;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.common.convention.exception.ServiceException;
import com.gugucat.shortlink.project.common.enums.ValidDateTypeEnum;
import com.gugucat.shortlink.project.config.GotoDomainWhiteListConfiguration;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import com.gugucat.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkMapper;
import com.gugucat.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.gugucat.shortlink.project.service.ShortLinkService;
import com.gugucat.shortlink.project.toolkit.HashUtil;
import com.gugucat.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 短链接接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;

    @Value("${short-link.domain.default}")
    private String createShortLinkDefaultDomain;

    /**
     * 抓取目标网站图标的超时时间，单位毫秒
     */
    private static final int FAVICON_FETCH_TIMEOUT_MS = 5000;

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
        // 请求 DTO 上没有 Bean Validation（本项目约定必填性由业务代码判定），
        // 不拦的话空串会建出一条跳转不到的垃圾短链接
        if (StrUtil.isBlank(requestParam.getOriginUrl())) {
            throw new ClientException("原始链接不能为空");
        }
        verificationWhitelist(requestParam.getOriginUrl());
        String shortLinkSuffix = generateSuffix(requestParam);
        String fullShortUrl = createShortLinkDefaultDomain + "/" + shortLinkSuffix;
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(createShortLinkDefaultDomain)
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
            // 布隆过滤器判重只保证「大概率不存在」，真正的唯一性由 t_link 的
            // 唯一索引 (full_short_url, del_time) 兜底；这里撞了说明布隆漏了，补进去再抛错
            if (!shortUriCreateCachePenetrationBloomFilter.contains(fullShortUrl)) {
                shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
            }
            throw new ServiceException(String.format("短链接：%s 生成重复", fullShortUrl));
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
        String gotoShortLinkKey = String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl);
        String gotoIsNullShortLinkKey = String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl);
        // 第一条路径：缓存命中，直接重定向，一次数据库都不碰
        String originalLink = stringRedisTemplate.opsForValue().get(gotoShortLinkKey);
        if (StrUtil.isNotBlank(originalLink)) {
            ((HttpServletResponse) response).sendRedirect(originalLink);
            return;
        }
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
        } finally {
            lock.unlock();
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
     * 短链接后缀生成
     *
     * @param requestParam 创建短链接请求参数
     * @return 短链接后缀
     */
    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        int customGenerateCount = 0; // 短链接生成失败次数
        String shortUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接频繁生成，请稍后再试");
            }
            String originUrl = requestParam.getOriginUrl();
            originUrl += UUID.randomUUID().toString(); // 原始url拼上随机UUID，保证同一原始链接每次生成的短码也不相同
            shortUri = HashUtil.hashToBase62(originUrl);
            // 用布隆过滤器判重而不是查库：判「不存在」是准确的，判「存在」可能是误判，
            // 误判只会让本次多循环一轮，代价远小于每次都打一次数据库
            if (!shortUriCreateCachePenetrationBloomFilter.contains(createShortLinkDefaultDomain + "/" + shortUri)) {
                break;
            }
            customGenerateCount++;
        }
        return shortUri;
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
