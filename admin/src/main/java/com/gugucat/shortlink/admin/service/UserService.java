package com.gugucat.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gugucat.shortlink.admin.dao.entity.UserDO;
import com.gugucat.shortlink.admin.dto.reps.UserRespDTO;


/**
 * 用户业务层
 */

public interface UserService extends IService<UserDO> {
    /**
     * 根据用户名查找用户信息
     * @param username 用户名
     * @return UserRespDTO 用户响应实体
     */
    UserRespDTO getUserByUsername(String username);
}
