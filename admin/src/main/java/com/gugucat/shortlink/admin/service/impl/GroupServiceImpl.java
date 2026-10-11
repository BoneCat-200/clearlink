package com.gugucat.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.admin.common.biz.user.UserContext;
import com.gugucat.shortlink.admin.dao.entity.GroupDO;
import com.gugucat.shortlink.admin.dao.mapper.GroupMapper;
import com.gugucat.shortlink.admin.dto.reps.LinkGroupRespDTO;
import com.gugucat.shortlink.admin.service.GroupService;
import com.gugucat.shortlink.admin.util.RandomGIDUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 短链接分组业务层实现
 */
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    @Override
    public void saveGroup(String groupName) {
        String gid;
        do {
            gid = RandomGIDUtils.generate();
        }while (hasGid(gid));   //  循环生成 gid，直到生成的 gid 不存在于数据库中
        GroupDO groupDO = GroupDO.builder()
                .gid(gid)
                .sortOrder(0)
                .username(UserContext.getUsername())
                .name(groupName)
                .build();
        baseMapper.insert(groupDO);
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


    /**
     * 检查分组gid是否已存在
     * @param gid 分组gid
     * @return 是否存在 true-存在 false-不存在
     */
    private boolean hasGid(String gid) {
        LambdaQueryWrapper<GroupDO> eq = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername,UserContext.getUsername());
        GroupDO hasGroupFlag = baseMapper.selectOne(eq);
        return hasGroupFlag != null;
//        return baseMapper.exists(eq);
    }



}
