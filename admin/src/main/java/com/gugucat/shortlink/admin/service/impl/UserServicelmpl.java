package com.gugucat.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.admin.common.convention.exception.ClientException;
import com.gugucat.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.gugucat.shortlink.admin.dao.entity.UserDO;
import com.gugucat.shortlink.admin.dao.mapper.UserMapper;
import com.gugucat.shortlink.admin.dto.reg.UserRegisterReqDTO;
import com.gugucat.shortlink.admin.dto.reps.UserRespDTO;
import com.gugucat.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;


@Service
@RequiredArgsConstructor
public class UserServicelmpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;

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
                userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());    // 将用户名添加到布隆过滤器中
                return;
            }
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);   // 未获取锁：用户名已存在
        }finally {
            lock.unlock(); // 释放锁
        }


    }
}
