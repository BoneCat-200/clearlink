package com.gugucat.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.admin.common.biz.user.UserContext;
import com.gugucat.shortlink.admin.common.convention.exception.ClientException;
import com.gugucat.shortlink.admin.dao.entity.GroupDO;
import com.gugucat.shortlink.admin.dao.mapper.GroupMapper;
import com.gugucat.shortlink.admin.dto.req.GroupSortReqDTO;
import com.gugucat.shortlink.admin.dto.req.GroupUpdateReqDTO;
import com.gugucat.shortlink.admin.dto.resp.LinkGroupRespDTO;
import com.gugucat.shortlink.admin.service.GroupService;
import com.gugucat.shortlink.admin.util.RandomGIDUtils;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.gugucat.shortlink.admin.common.constant.RedisCacheConstant.LOCK_GROUP_CREATE_KEY;

/**
 * 短链接分组业务层实现
 */
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    private final RedissonClient redissonClient;

    /**
     * 每个用户最多能拥有的分组数
     */
    @Value("${short-link.group.max-num:20}")
    private Integer groupMaxNum;

    @Override
    public void saveGroup(String groupName) {
        saveGroup(UserContext.getUsername(), groupName);    // 归属人取当前登录用户
    }

    @Override
    public void saveGroup(String username, String groupName) {
        // 按用户加锁：「查数量」和「插入」之间不是原子的。
        // 不加锁时两个并发请求都会看到「还差一个到上限」，各插一条，一起突破上限。
        // 锁的粒度是用户名，不同用户互不影响
        RLock lock = redissonClient.getLock(String.format(LOCK_GROUP_CREATE_KEY, username));
        lock.lock();
        try {
            Long groupCount = baseMapper.selectCount(Wrappers.lambdaQuery(GroupDO.class)
                    .eq(GroupDO::getUsername, username)     // 分片键，精确路由
                    .eq(GroupDO::getDelFlag, 0));
            // 用 >= 而不是参考实现的 ==：后者在并发或数据异常（数量已超上限）时不再拦截
            if (groupCount != null && groupCount >= groupMaxNum) {
                throw new ClientException("已超出最大分组数：" + groupMaxNum);
            }
            String gid;
            do {
                gid = RandomGIDUtils.generate();
            }while (hasGid(username, gid));   //  循环生成 gid，直到生成的 gid 不存在于数据库中
            GroupDO groupDO = GroupDO.builder()
                    .gid(gid)
                    .sortOrder(0)
                    .username(username)
                    .name(groupName)
                    .build();
            baseMapper.insert(groupDO);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<LinkGroupRespDTO> listGroup() {
        LambdaQueryWrapper<GroupDO> eq = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getDelFlag, 0)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .orderByAsc(GroupDO::getSortOrder,GroupDO::getUpdateTime);
        List<GroupDO> groupDOList = baseMapper.selectList(eq);
        return BeanUtil.copyToList(groupDOList, LinkGroupRespDTO.class);
    }

    @Override
    public void updateGroup(GroupUpdateReqDTO requestParam) {
        LambdaQueryWrapper<GroupDO> eq = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, requestParam.getGid())
                .eq(GroupDO::getUsername, UserContext.getUsername())    // 仅允许修改当前登录用户的分组
                .eq(GroupDO::getDelFlag, 0);    // 仅修改未删除的分组
        GroupDO groupDO = GroupDO.builder()
                .name(requestParam.getName())
                .build();
        baseMapper.update(groupDO, eq);   // 仅更新 name 字段，其余字段为 null 不参与更新
    }

    @Override
    public void deleteGroup(String gid) {
        LambdaUpdateWrapper<GroupDO> wrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .set(GroupDO::getDelFlag, 1)    // 逻辑删除：将 delFlag 置为 1
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, UserContext.getUsername())    // 仅允许删除当前登录用户的分组
                .eq(GroupDO::getDelFlag, 0);    // 仅删除未删除的分组
        baseMapper.update(null, wrapper);   // 按条件逻辑删除，不校验影响行数（不存在则静默返回）
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortGroup(List<GroupSortReqDTO> requestParam) {
        requestParam.forEach(each -> {  // 逐条更新各分组的排序号
            LambdaQueryWrapper<GroupDO> eq = Wrappers.lambdaQuery(GroupDO.class)
                    .eq(GroupDO::getGid, each.getGid())
                    .eq(GroupDO::getUsername, UserContext.getUsername())    // 仅允许修改当前登录用户的分组
                    .eq(GroupDO::getDelFlag, 0);    // 仅修改未删除的分组
            GroupDO groupDO = GroupDO.builder()
                    .sortOrder(each.getSortOrder())
                    .build();
            baseMapper.update(groupDO, eq);   // 仅更新 sortOrder 字段
        });
    }


    /**
     * 检查分组gid是否已存在
     * @param username 分组归属用户名
     * @param gid 分组gid
     * @return 是否存在 true-存在 false-不存在
     */
    private boolean hasGid(String username, String gid) {
        LambdaQueryWrapper<GroupDO> eq = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, username);
        GroupDO hasGroupFlag = baseMapper.selectOne(eq);
        return hasGroupFlag != null;
//        return baseMapper.exists(eq);
    }



}
