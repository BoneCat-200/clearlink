package com.gugucat.shortlink.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.common.convention.result.Results;
import com.gugucat.shortlink.admin.dto.resp.LinkGroupRespDTO;
import com.gugucat.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.gugucat.shortlink.admin.service.GroupService;
import com.gugucat.shortlink.admin.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回收站接口实现层
 * <p>
 * 短链接数据在中台，但「当前用户有哪些分组」只有后管知道（t_group 在后管）。
 * 回收站是跨分组的视图，所以由后管先算出 gidList 再交给中台分页。
 */
@Service("recycleBinServiceImplByAdmin")  // 显式命名，避免聚合部署时与 project 模块的同名 Bean 冲突
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;
    private final GroupService groupService;

    @Override
    public Result<Page<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        // 用当前登录用户自己的分组覆盖客户端传来的 gidList：
        // 不这么做的话，任何人传别人的 gid 就能看到别人的回收站
        List<String> gidList = groupService.listGroup().stream()
                .map(LinkGroupRespDTO::getGid)
                .toList();
        if (CollUtil.isEmpty(gidList)) {
            // 没有分组就意味着没有任何短链接，回收站必然是空的 —— 这是正常结果，不是错误
            return Results.success(new Page<ShortLinkPageRespDTO>(requestParam.getCurrent(), requestParam.getSize()));
        }
        requestParam.setGidList(gidList);
        return shortLinkActualRemoteService.pageRecycleBinShortLink(gidList, requestParam.getCurrent(), requestParam.getSize());
    }
}
