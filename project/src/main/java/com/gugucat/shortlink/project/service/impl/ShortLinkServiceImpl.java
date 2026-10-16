package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.project.common.constant.RedisKeyConstant;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.common.convention.exception.ServiceException;
import com.gugucat.shortlink.project.config.GotoDomainWhiteListConfiguration;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import com.gugucat.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkMapper;
import com.gugucat.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.gugucat.shortlink.project.service.ShortLinkService;
import com.gugucat.shortlink.project.toolkit.HashUtil;
import com.gugucat.shortlink.project.toolkit.LinkUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;

    @Value("${short-link.domain.default}")
    private String createShortLinkDefaultDomain;

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
        // 缓存预热：创建时就把跳转映射写进 Redis，避免短链接刚发出就被访问时的一次缓存穿透
        stringRedisTemplate.opsForValue().set(
                String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, fullShortUrl),
                requestParam.getOriginUrl(),
                LinkUtil.getLinkCacheValidTime(requestParam.getValidDate()),
                TimeUnit.MILLISECONDS
        );
        shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + shortLinkDO.getFullShortUrl())
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .build();
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
