package com.gugucat.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gugucat.shortlink.admin.dao.entity.GroupDO;
import com.gugucat.shortlink.admin.dto.reps.LinkGroupRespDTO;

import java.util.List;

/**
 * 短链接分组业务层
 */

public interface GroupService extends IService<GroupDO> {

    /**
     * 创建分组
     */
    void saveGroup(String groupName);

    /**
     * 查询分组
     */
    List<LinkGroupRespDTO> listGroup();
}
