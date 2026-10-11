package com.gugucat.shortlink.admin.dto.resp;

import lombok.Data;

@Data
public class LinkGroupRespDTO {
    /**
     * `gid` varchar (32) DEFAULT NULL COMMENT `分组标识`
     */
    private String gid;

    /**
     * `name` varchar (64) DEFAULT NULL COMMENT `分组名称`
     */
    private String name;

    /**
     * `username` varchar (256) DEFAULT NULL COMMENT `创建分组用户名`
     */
    private String username;

    /**
     * `sort_order` int (3) DEFAULT NULL COMMENT `分组排序`
     */
    private Integer sortOrder;

}
