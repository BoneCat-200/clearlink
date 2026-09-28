package com.gugucat.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gugucat.shortlink.admin.dao.entity.UserDO;
import com.gugucat.shortlink.admin.dto.reg.UserRegisterReqDTO;
import com.gugucat.shortlink.admin.dto.reg.UserUpdateReqDTO;
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

    /**
     * 查询当前用户名是否已存在
     * @param username 用户名
     * @return boolean 是否存在：true表示存在，false表示不存在
     */
    boolean hasUserName(String username);

    /**
     * 用户注册
     * @param requestParam 用户注册请求参数
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * 更新用户信息
     * @param requestParam 用户更新请求参数
     */
    void updateUser(UserUpdateReqDTO requestParam);
}
