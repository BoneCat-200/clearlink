package com.gugucat.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.admin.common.convention.exception.ClientException;
import com.gugucat.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.gugucat.shortlink.admin.dao.entity.UserDO;
import com.gugucat.shortlink.admin.dao.mapper.UserMapper;
import com.gugucat.shortlink.admin.dto.req.UserLoginReqDTO;
import com.gugucat.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.gugucat.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.gugucat.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.gugucat.shortlink.admin.dto.resp.UserRespDTO;
import com.gugucat.shortlink.admin.service.GroupService;
import com.gugucat.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;


@Service
@RequiredArgsConstructor
public class UserServicelmpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final GroupService groupService;

    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> eq = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username); // 构建查询条件（mybatis-plus提供查询器）
        UserDO userDO = baseMapper.selectOne(eq);   // 根据查询条件查询用户信息
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);   // 将userDO的同名字段映射到result
        return result;
    }

    @Override
    public boolean hasUserName(String username) {
        return userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)   // 用户与默认分组是一笔账，分组建失败不能让用户留下来
    public void register(UserRegisterReqDTO requestParam) {
        if (hasUserName(requestParam.getUsername())){
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        }
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        try {
            if (lock.tryLock()){
                int insert = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));    // 将请求参数转换为UserDO并插入数据库
                if (insert < 1) {
                    throw new ClientException(UserErrorCodeEnum.USER_EXIST);
                }
                // 新用户默认带一个分组：否则建链时没有 gid 可用，前端也看不到任何分组
                // 注册是免鉴权接口，此刻 UserContext 为空，所以归属人要显式传进去
                groupService.saveGroup(requestParam.getUsername(), "默认分组");
                userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());    // 将用户名添加到布隆过滤器中
                return;
            }
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);   // 未获取锁：用户名已存在
        }finally {
            lock.unlock(); // 释放锁
        }


    }

    @Override
    public void updateUser(UserUpdateReqDTO requestParam) {
        // TODO 校验用户名是否登录

        // 根据用户名更新
        LambdaQueryWrapper<UserDO> eq = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), eq);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        UserDO userDO = baseMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword, requestParam.getPassword())
                .eq(UserDO::getDelFlag, 0));
        if (userDO == null){
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }
        if (stringRedisTemplate.hasKey(USER_LOGIN_KEY + userDO.getUsername())){ // 如果redis中已经存在该用户的用户名
            // TODO 返回已有token并续期
            throw new ClientException("用户已登录");
        }
        /**
         * token存储结构：
         *  key: clearlink:login:{username}
         *  value:
         *      field:token
         *      value:用户信息JSON
         */
        String token = UUID.randomUUID().toString();
        String loginKey = USER_LOGIN_KEY + userDO.getUsername();
        // TODO 将用户信息存储进Redis前过滤敏感字段
        stringRedisTemplate.opsForHash().put(loginKey, token, JSON.toJSONString(userDO));
        stringRedisTemplate.expire(loginKey, 10, TimeUnit.MINUTES);

        return new UserLoginRespDTO(token);
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        // TODO 续期token
        return stringRedisTemplate.hasKey(USER_LOGIN_KEY + username) != null;
    }

    @Override
    public void logout(String username, String token) {
        if (checkLogin(username, token)){
            stringRedisTemplate.delete(USER_LOGIN_KEY + username);
        }
        throw new ClientException("用户未登录或token不存在");
    }
}
