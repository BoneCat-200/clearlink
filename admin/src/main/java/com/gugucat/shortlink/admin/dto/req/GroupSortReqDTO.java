package com.gugucat.shortlink.admin.dto.req;

import lombok.Data;

@Data
public class GroupSortReqDTO {
    /**
     * `gid` varchar (32) DEFAULT NULL COMMENT `分组标识`
     */
    private String gid;

    /**
     * `sort_order` int (3) DEFAULT NULL COMMENT `分组排序`
     */
    private Integer sortOrder;
}
