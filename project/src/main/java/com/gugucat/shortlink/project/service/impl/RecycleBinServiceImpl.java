package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.project.common.constant.RedisKeyConstant;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkMapper;
import com.gugucat.shortlink.project.dto.req.RecycleBinRecoverReqDTO;
import com.gugucat.shortlink.project.dto.req.RecycleBinSaveReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.gugucat.shortlink.project.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 回收站管理接口实现层
 */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements RecycleBinService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void saveRecycleBin(RecycleBinSaveReqDTO requestParam) {
        // gid 是 t_link 的分片键，缺了它 ShardingSphere 无法路由
        if (StrUtil.isBlank(requestParam.getGid()) || StrUtil.isBlank(requestParam.getFullShortUrl())) {
            throw new ClientException("完整短链接与分组标识不能为空");
        }
        // 「在不在回收站」用的是 enable_status，不是 del_flag：
        // del_flag 留给「彻底删除」，两者的区别是回收站里的记录还能恢复
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelTime, 0L)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(1)
                .build();
        if (baseMapper.update(shortLinkDO, updateWrapper) < 1) {
            // 影响行数为 0 时若静默返回成功，前端会以为操作生效了、但列表毫无变化
            throw new ClientException("短链接不存在或已在回收站中");
        }
        // 移入回收站后这条短链接就不能再跳转了，缓存必须立刻失效，
        // 否则用户点进去还能访问一个「已删除」的链接（缓存的 TTL 可能长达 30 天）
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
    }

    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getGid()) || StrUtil.isBlank(requestParam.getFullShortUrl())) {
            throw new ClientException("完整短链接与分组标识不能为空");
        }
        LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 1)     // 只有在回收站里的才能被恢复
                .eq(ShortLinkDO::getDelTime, 0L)
                .eq(ShortLinkDO::getDelFlag, 0);
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(0)
                .build();
        if (baseMapper.update(shortLinkDO, updateWrapper) < 1) {
            throw new ClientException("短链接不存在或不在回收站中");
        }
        // 必须删掉空值缓存：这条短链接在回收站期间如果被访问过，跳转链路会写下
        // 一个 30 分钟的「不存在」标记；不删的话，恢复之后它最多还有 30 分钟跳不了
        stringRedisTemplate.delete(String.format(RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        // gidList 为空时 XML 里的 <foreach> 会生成 `IN ()` —— 一个语法错误的 SQL，
        // 最终只会得到一句难懂的「系统执行出错」。这里提前拦下并说清原因
        if (CollUtil.isEmpty(requestParam.getGidList())) {
            throw new ClientException("分组标识不能为空");
        }
        IPage<ShortLinkDO> resultPage = baseMapper.pageRecycleBinLink(requestParam);
        return resultPage.convert(each -> {
            ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            // 与正常列表一致：库里存的是不带协议的 domain，前端要直接当链接用，这里补上协议
            result.setDomain("http://" + result.getDomain());
            return result;
        });
    }
}
