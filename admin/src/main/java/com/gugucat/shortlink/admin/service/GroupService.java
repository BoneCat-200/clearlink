package com.gugucat.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gugucat.shortlink.admin.dao.entity.GroupDO;

/**
 * 短链接分组业务层
 */

public interface GroupService extends IService<GroupDO> {

    /**
     * 创建分组
     */
    void saveGroup(String groupName);
}
