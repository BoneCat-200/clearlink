package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gugucat.shortlink.project.common.biz.user.UserContext;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.common.convention.exception.ServiceException;
import com.gugucat.shortlink.project.dao.entity.GroupDO;
import com.gugucat.shortlink.project.dao.entity.LinkChangeLogDO;
import com.gugucat.shortlink.project.dao.entity.ShortLinkDO;
import com.gugucat.shortlink.project.dao.mapper.LinkChangeLogMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkGroupMapper;
import com.gugucat.shortlink.project.dao.mapper.ShortLinkMapper;
import com.gugucat.shortlink.project.dto.req.ShortLinkChangeLogPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkChangeLogRespDTO;
import com.gugucat.shortlink.project.service.LinkChangeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 短链接变更分组记录接口实现层
 */
@Service
@RequiredArgsConstructor
public class LinkChangeLogServiceImpl implements LinkChangeLogService {

    private final LinkChangeLogMapper linkChangeLogMapper;
    private final ShortLinkMapper shortLinkMapper;
    private final LinkGroupMapper linkGroupMapper;

    @Override
    public IPage<ShortLinkChangeLogRespDTO> pageChangeLog(ShortLinkChangeLogPageReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getFullShortUrl()) || StrUtil.isBlank(requestParam.getGid())) {
            throw new ClientException("完整短链接与分组标识不能为空");
        }
        checkGroupBelongToUser(requestParam.getGid());
        // 两道校验缺一不可：上面校验「这个分组是不是你的」，这里校验
        // 「这条短链接现在是不是就在这个分组里」。
        // 只做前者的话，拿自己的 gid 配上别人的 fullShortUrl 就能读到别人的变更历史
        //（监控模块的访问记录接口就踩过这个坑）
        Long linkCount = shortLinkMapper.selectCount(Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getDelFlag, 0));
        if (linkCount == null || linkCount < 1) {
            throw new ClientException("短链接记录不存在");
        }
        LambdaQueryWrapper<LinkChangeLogDO> queryWrapper = Wrappers.lambdaQuery(LinkChangeLogDO.class)
                .eq(LinkChangeLogDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(LinkChangeLogDO::getDelFlag, 0)
                // 同一秒内可能有多次变更（批量操作），加 id 作为第二关键字才是全序
                .orderByDesc(LinkChangeLogDO::getCreateTime)
                .orderByDesc(LinkChangeLogDO::getId);
        IPage<LinkChangeLogDO> page = linkChangeLogMapper.selectPage(requestParam, queryWrapper);
        return page.convert(each -> BeanUtil.toBean(each, ShortLinkChangeLogRespDTO.class));
    }

    /**
     * 校验当前用户是否拥有该分组
     *
     * @param gid 分组标识
     */
    private void checkGroupBelongToUser(String gid) {
        String username = Optional.ofNullable(UserContext.getUsername())
                .orElseThrow(() -> new ServiceException("用户未登录"));
        List<GroupDO> groupDOList = linkGroupMapper.selectList(Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, username));
        if (CollUtil.isEmpty(groupDOList)) {
            throw new ServiceException("用户信息与分组标识不匹配");
        }
    }
}
