package com.gugucat.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gugucat.shortlink.admin.dao.entity.GroupDO;
import com.gugucat.shortlink.admin.dto.req.GroupUpdateReqDTO;
import com.gugucat.shortlink.admin.dto.resp.LinkGroupRespDTO;

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

    /**
     * 修改分组
     * @param requestParam 分组修改请求参数
     */
    void updateGroup(GroupUpdateReqDTO requestParam);

    /**
     * 删除分组
     * @param gid 分组标识
     */
    void deleteGroup(String gid);
}
