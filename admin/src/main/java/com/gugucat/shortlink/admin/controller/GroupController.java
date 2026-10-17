package com.gugucat.shortlink.admin.controller;

import com.gugucat.shortlink.admin.common.biz.audit.OperationLog;
import com.gugucat.shortlink.admin.common.biz.idempotent.Idempotent;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.common.convention.result.Results;
import com.gugucat.shortlink.admin.dto.req.GroupSaveReqDTO;
import com.gugucat.shortlink.admin.dto.req.GroupSortReqDTO;
import com.gugucat.shortlink.admin.dto.req.GroupUpdateReqDTO;
import com.gugucat.shortlink.admin.dto.resp.LinkGroupRespDTO;
import com.gugucat.shortlink.admin.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * 短链接分组控制层
 */
@RestController
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * 创建分组
     */
    @OperationLog(module = "分组", action = "创建")
    @Idempotent
    @PostMapping("/api/clearlink/admin/v0.1/group")
    public Result saveGroup(@RequestBody GroupSaveReqDTO requestParam) {
        groupService.saveGroup(requestParam.getName());
        return Results.success();
    }


    /**
     * 查询分组
     */
    @GetMapping("/api/clearlink/admin/v0.1/group")
    public Result<List<LinkGroupRespDTO>> listGroup() {
        return Results.success(groupService.listGroup());
    }


    /**
     * 修改分组名称
     */
    @OperationLog(module = "分组", action = "修改")
    @Idempotent
    @PutMapping("/api/clearlink/admin/v0.1/group")
    public Result updateGroup(@RequestBody GroupUpdateReqDTO requestParam) {
        groupService.updateGroup(requestParam);
        return Results.success();
    }


    /**
     * 删除分组
     */
    @OperationLog(module = "分组", action = "删除")
    @Idempotent
    @DeleteMapping("/api/clearlink/admin/v0.1/group")
    public Result deleteGroup(@RequestParam("gid") String gid) {
        groupService.deleteGroup(gid);
        return Results.success();
    }


    /**
     * 分组排序
     */
    @OperationLog(module = "分组", action = "排序")
    @Idempotent
    @PostMapping("/api/clearlink/admin/v0.1/group/sort")
    public Result sortGroup(@RequestBody List<GroupSortReqDTO> requestParam) {
        groupService.sortGroup(requestParam);
        return Results.success();
    }

}
