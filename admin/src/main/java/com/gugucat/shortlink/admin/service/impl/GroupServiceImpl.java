package com.gugucat.shortlink.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gugucat.shortlink.admin.dao.entity.GroupDO;
import com.gugucat.shortlink.admin.dao.mapper.GroupMapper;
import com.gugucat.shortlink.admin.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 短链接分组业务层实现
 */
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {




}
