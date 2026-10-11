package com.gugucat.shortlink.admin.dto.req;

import lombok.Data;

@Data
public class GroupUpdateReqDTO {
    /**
     * `gid` varchar (32) DEFAULT NULL COMMENT `分组标识`
     */
    private String gid;

    /**
     * `name` varchar (64) DEFAULT NULL COMMENT `分组名称`
     */
    private String name;
}
