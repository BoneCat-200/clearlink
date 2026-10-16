package com.gugucat.shortlink.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gugucat.shortlink.project.dao.entity.LinkStatsTodayDO;

/**
 * 短链接今日统计接口层
 * <p>
 * 当前只作为实体上的通用能力入口，落库走 Mapper 里的累加语句。
 */
public interface LinkStatsTodayService extends IService<LinkStatsTodayDO> {
}
