package com.gugucat.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gugucat.shortlink.admin.dao.entity.UserDO;
import com.gugucat.shortlink.admin.dto.req.UserLoginReqDTO;
import com.gugucat.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.gugucat.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.gugucat.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.gugucat.shortlink.admin.dto.resp.UserRespDTO;


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

    /**
     * 用户登录
     * @param requestParam 用户登录请求参数
     * @return UserLoginRespDTO 用户登录响应实体
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    /**
     * 检查用户是否已登录
     * @param username 用户名
     * @param token 登录令牌
     * @return boolean 是否已登录：true表示已登录，false表示未登录
     */
    Boolean checkLogin(String username, String token);

    /**
     * 用户登出
     * @param username 用户名
     * @param token 登录令牌
     * @return Void
     */
    void logout(String username, String token);
}
