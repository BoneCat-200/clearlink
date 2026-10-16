package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.LinkAccessLogsDO;

/**
 * 访问日志监控持久层
 * <p>
 * 这张表记的是明细（一次跳转一行），没有维度需要累加，所以写侧直接用
 * BaseMapper 自带的 insert，不需要自定义语句。
 */
public interface LinkAccessLogsMapper extends BaseMapper<LinkAccessLogsDO> {
}
