package com.gugucat.shortlink.admin.dto.req;

import lombok.Data;

@Data
public class GroupSaveReqDTO {
    /**
     * `name` varchar (64) DEFAULT NULL COMMENT `分组名称`
     */
    private String name;
}
