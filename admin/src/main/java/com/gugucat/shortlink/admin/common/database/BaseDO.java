package com.gugucat.shortlink.admin.common.database;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaseDO {
    /**
     * `create_time` datetime DEFAULT NULL COMMENT `创建时间`
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * `update_time` datetime DEFAULT NULL COMMENT `修改时间`
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * `del_flag` tinyint (1) DEFAULT NULL COMMENT `删除标识 0：未删除 1：已删除`
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
