package com.gugucat.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.admin.common.biz.user.UserContext;
import com.gugucat.shortlink.admin.common.convention.exception.ClientException;
import com.gugucat.shortlink.admin.dao.entity.OperationLogDO;
import com.gugucat.shortlink.admin.dao.mapper.OperationLogMapper;
import com.gugucat.shortlink.admin.dto.req.OperationLogPageReqDTO;
import com.gugucat.shortlink.admin.dto.resp.OperationLogRespDTO;
import com.gugucat.shortlink.admin.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 操作审计日志接口实现层
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLogDO>
        implements OperationLogService {

    /**
     * 单页最大条数，防止调用方传一个超大 size 把库拖慢
     */
    private static final long MAX_PAGE_SIZE = 100;

    @Override
    public IPage<OperationLogRespDTO> pageOperationLog(OperationLogPageReqDTO requestParam) {
        String username = UserContext.getUsername();
        if (StrUtil.isBlank(username)) {
            // 正常链路一定经过网关与 UserTransmitFilter；这里兜底防止直连后管越权查全量
            throw new ClientException("用户未登录");
        }
        long current = Math.max(requestParam.getCurrent(), 1);
        long size = Math.min(Math.max(requestParam.getSize(), 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<OperationLogDO> queryWrapper = Wrappers.lambdaQuery(OperationLogDO.class)
                // 越权边界：只查当前登录用户自己的记录
                .eq(OperationLogDO::getUsername, username)
                .eq(StrUtil.isNotBlank(requestParam.getModule()), OperationLogDO::getModule, requestParam.getModule())
                .eq(requestParam.getSuccess() != null, OperationLogDO::getSuccess, requestParam.getSuccess())
                .ge(requestParam.getStartTime() != null, OperationLogDO::getCreateTime, requestParam.getStartTime())
                .le(requestParam.getEndTime() != null, OperationLogDO::getCreateTime, requestParam.getEndTime())
                // create_time 是秒/毫秒级 DATETIME，同一时刻可能有多条，补 id 保证分页稳定
                .orderByDesc(OperationLogDO::getCreateTime)
                .orderByDesc(OperationLogDO::getId);
        Page<OperationLogDO> page = baseMapper.selectPage(new Page<>(current, size), queryWrapper);
        return page.convert(each -> BeanUtil.toBean(each, OperationLogRespDTO.class));
    }
}
