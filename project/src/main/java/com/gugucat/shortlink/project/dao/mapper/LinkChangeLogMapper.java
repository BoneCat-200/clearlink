package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.LinkChangeLogDO;

/**
 * 短链接变更分组记录持久层
 * <p>
 * 只做「写入」和「按短链接分页查询」，没有累加逻辑，直接用 BaseMapper 的能力。
 */
public interface LinkChangeLogMapper extends BaseMapper<LinkChangeLogDO> {
}
