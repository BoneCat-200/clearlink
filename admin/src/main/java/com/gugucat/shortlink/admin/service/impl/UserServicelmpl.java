package com.gugucat.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.admin.common.biz.user.LoginSecurityManager;
import com.gugucat.shortlink.admin.common.biz.user.LoginSecurityProperties;
import com.gugucat.shortlink.admin.common.biz.user.UserContext;
import com.gugucat.shortlink.admin.common.biz.user.UserInfoDTO;
import com.gugucat.shortlink.admin.common.convention.errorcode.BaseErrorCode;
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
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserServicelmpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    /**
     * 登录会话的有效期，单位分钟
     * <p>
     * 每次登录（含已登录时的重复登录）都会把有效期续到这个时长。
     * 取 30 分钟与参考实现、以及文档里的会话口径一致（原实现是 10 分钟）。
     */
    private static final long USER_LOGIN_TTL_MINUTES = 30;

    /**
     * BCrypt 哈希的固定前缀
     */
    private static final String BCRYPT_PREFIX = "$2";

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final GroupService groupService;
    private final PasswordEncoder passwordEncoder;
    private final LoginSecurityManager loginSecurityManager;
    private final LoginSecurityProperties loginSecurityProperties;

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
        if (StrUtil.isBlank(requestParam.getUsername())) {
            throw new ClientException("用户名不能为空");
        }
        // 密码策略只约束新密码：历史账号的存量密码仍可登录，否则一次升级会把老用户全部挡在门外
        validatePassword(requestParam.getPassword());
        if (hasUserName(requestParam.getUsername())) {
            throw new ClientException(UserErrorCodeEnum.USER_NAME_EXIST);
        }
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        try {
            if (lock.tryLock()) {
                UserDO userDO = BeanUtil.toBean(requestParam, UserDO.class);
                // 存哈希而不是明文：数据库或备份泄露时，攻击者拿不到可直接登录的密码
                userDO.setPassword(passwordEncoder.encode(requestParam.getPassword()));
                int insert = baseMapper.insert(userDO);
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
        } finally {
            lock.unlock(); // 释放锁
        }
    }

    @Override
    public void updateUser(UserUpdateReqDTO requestParam) {
        String currentUsername = UserContext.getUsername();
        if (StrUtil.isBlank(currentUsername)) {
            throw new ClientException("用户未登录");
        }
        // 归属人只认当前登录态：请求体里的 username 可以被随意伪造，
        // 不能拿它当更新条件，否则任何登录用户都能改别人的资料和密码
        if (StrUtil.isNotBlank(requestParam.getUsername())
                && !currentUsername.equals(requestParam.getUsername())) {
            throw new ClientException("不能修改其他用户的信息");
        }
        UserDO userDO = new UserDO();
        userDO.setRealName(requestParam.getRealName());
        userDO.setPhone(requestParam.getPhone());
        userDO.setMail(requestParam.getMail());
        // 不传密码表示「本次不修改密码」，与 MyBatis-Plus 忽略 null 字段的行为一致
        if (StrUtil.isNotBlank(requestParam.getPassword())) {
            validatePassword(requestParam.getPassword());
            userDO.setPassword(passwordEncoder.encode(requestParam.getPassword()));
        }
        LambdaUpdateWrapper<UserDO> wrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, currentUsername);   // 分片键，精确路由
        baseMapper.update(userDO, wrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        String username = requestParam.getUsername();
        // 用户名或密码为空时直接按登录失败处理，不进入查库与计数
        if (StrUtil.isBlank(username) || StrUtil.isBlank(requestParam.getPassword())) {
            throw new ClientException(UserErrorCodeEnum.USER_LOGIN_ERROR);
        }
        // 先查锁再验密码：锁定期间不再做密码比对，避免把锁定逻辑绕过去
        loginSecurityManager.checkNotLocked(username);
        UserDO userDO = baseMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDelFlag, 0));
        if (userDO == null || !passwordMatches(requestParam.getPassword(), userDO.getPassword())) {
            // 只对存在的用户名计数：不存在的用户名不计数，
            // 避免攻击者用任意用户名刷出一堆永不释放的计数键
            if (userDO != null) {
                loginSecurityManager.recordFailure(username);
            }
            // 用户不存在与密码错误返回同一条提示，避免账号枚举
            throw new ClientException(UserErrorCodeEnum.USER_LOGIN_ERROR);
        }
        loginSecurityManager.clear(username);
        // 历史数据是明文密码：登录成功后顺手升级成 BCrypt，老账号无感迁移
        upgradePasswordIfNecessary(userDO, requestParam.getPassword());

        String loginKey = USER_LOGIN_KEY + userDO.getUsername();
        // 登录态只缓存 id/username/realName，密码、手机号、邮箱不再进 Redis
        String sessionJson = buildSessionJson(userDO);
        // 已登录时不报错，直接把已有 token 返回并给会话续期。
        // 原来的实现抛「用户已登录」—— 这会让「刷新页面后重新登录」直接失败，
        // 前端也拿不到 token 去续期，只能干等会话过期。
        // 注意校验仍在前面：用户名密码不对的话走不到这里
        Map<Object, Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(loginKey);
        if (CollUtil.isNotEmpty(hasLoginMap)) {
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .map(Object::toString)
                    // 理论上不会发生（hash 非空就至少有一个 field），兜底成可读错误
                    .orElseThrow(() -> new ClientException("用户登录错误"));
            // 历史版本把整条 UserDO（含密码）存进了 Redis；
            // 复用会话时用脱敏后的结构覆盖，让存量数据也尽快清理掉
            stringRedisTemplate.opsForHash().put(loginKey, token, sessionJson);
            stringRedisTemplate.expire(loginKey, USER_LOGIN_TTL_MINUTES, TimeUnit.MINUTES);
            return new UserLoginRespDTO(token);
        }
        /**
         * token存储结构：
         *  key: clearlink:login:{username}
         *  value:
         *      field:token
         *      value:用户信息JSON（仅 id/username/realName）
         */
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().put(loginKey, token, sessionJson);
        stringRedisTemplate.expire(loginKey, USER_LOGIN_TTL_MINUTES, TimeUnit.MINUTES);

        return new UserLoginRespDTO(token);
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        if (StrUtil.isBlank(username) || StrUtil.isBlank(token)) {
            return false;
        }
        String loginKey = USER_LOGIN_KEY + username;
        // 必须校验「这个 token 是否存在」：只判断 key 是否存在的话，
        // 用户带着一个随便编的 token 也能通过校验
        Boolean tokenExists = stringRedisTemplate.opsForHash().hasKey(loginKey, token);
        if (Boolean.TRUE.equals(tokenExists)) {
            // 校验通过即续期，活跃用户不会在操作过程中被踢下线
            stringRedisTemplate.expire(loginKey, USER_LOGIN_TTL_MINUTES, TimeUnit.MINUTES);
            return true;
        }
        return false;
    }

    @Override
    public void logout(String username, String token) {
        // 登出做成幂等：无论 token 是否还在，调用方都能得到「已经退出」的结果。
        // 原实现删完整个 hash 后无条件抛「用户未登录」，导致正常登出也报错
        if (StrUtil.isBlank(username) || StrUtil.isBlank(token)) {
            return;
        }
        String loginKey = USER_LOGIN_KEY + username;
        // 只删当前 token：同一账号的其它会话不受影响
        stringRedisTemplate.opsForHash().delete(loginKey, token);
        Long remain = stringRedisTemplate.opsForHash().size(loginKey);
        if (remain == null || remain == 0) {
            stringRedisTemplate.delete(loginKey);
        }
    }

    /**
     * 校验密码强度
     * <p>
     * 规则：长度在配置范围内、不含空白、同时包含字母与数字。
     *
     * @param password 明文密码
     * @throws ClientException 不满足密码策略
     */
    private void validatePassword(String password) {
        int minLength = loginSecurityProperties.getPasswordMinLength();
        int maxLength = loginSecurityProperties.getPasswordMaxLength();
        if (password == null || password.length() < minLength || password.length() > maxLength) {
            throw new ClientException("密码长度需为 " + minLength + "-" + maxLength + " 位",
                    BaseErrorCode.PASSWORD_POLICY_ERROR);
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char character : password.toCharArray()) {
            if (Character.isWhitespace(character)) {
                throw new ClientException("密码不能包含空白字符", BaseErrorCode.PASSWORD_POLICY_ERROR);
            }
            if ((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')) {
                hasLetter = true;
            } else if (character >= '0' && character <= '9') {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new ClientException(BaseErrorCode.PASSWORD_POLICY_ERROR);
        }
    }

    /**
     * 校验明文密码与库中密码是否匹配
     * <p>
     * 兼容两种存量格式：BCrypt 哈希与历史明文。
     * 明文比较用 {@link MessageDigest#isEqual}，避免逐字节比较带来的时序侧信道。
     *
     * @param rawPassword    明文密码
     * @param storedPassword 库中密码
     * @return 是否匹配
     */
    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (StrUtil.isBlank(storedPassword)) {
            return false;
        }
        if (storedPassword.startsWith(BCRYPT_PREFIX)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 把历史明文密码升级为 BCrypt
     * <p>
     * 升级失败不影响本次登录：用户已经验证通过，下一次登录还会再尝试升级。
     *
     * @param userDO      用户
     * @param rawPassword 本次输入的明文密码
     */
    private void upgradePasswordIfNecessary(UserDO userDO, String rawPassword) {
        if (StrUtil.isBlank(userDO.getPassword()) || userDO.getPassword().startsWith(BCRYPT_PREFIX)) {
            return;
        }
        try {
            LambdaUpdateWrapper<UserDO> wrapper = Wrappers.lambdaUpdate(UserDO.class)
                    .set(UserDO::getPassword, passwordEncoder.encode(rawPassword))
                    .eq(UserDO::getUsername, userDO.getUsername());  // 分片键，精确路由
            baseMapper.update(null, wrapper);
        } catch (Throwable ex) {
            log.warn("历史密码升级为 BCrypt 失败，username: {}", userDO.getUsername(), ex);
        }
    }

    /**
     * 构造写入 Redis 的脱敏会话信息
     *
     * @param userDO 用户
     * @return 只含 id/username/realName 的 JSON
     */
    private String buildSessionJson(UserDO userDO) {
        return JSON.toJSONString(UserInfoDTO.builder()
                .userId(String.valueOf(userDO.getId()))
                .username(userDO.getUsername())
                .realName(userDO.getRealName())
                .build());
    }
}
