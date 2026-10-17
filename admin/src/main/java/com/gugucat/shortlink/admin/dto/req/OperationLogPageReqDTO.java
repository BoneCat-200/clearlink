package com.gugucat.shortlink.admin.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.dto.resp.OperationLogRespDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 操作审计日志分页查询参数
 * <p>
 * 继承 Page 让 current / size 直接绑定；查询范围固定为当前登录用户，
 * 不接受客户端传 username，避免越权查看他人操作记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogPageReqDTO extends Page<OperationLogRespDTO> {

    /**
     * 业务模块
     */
    private String module;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 开始时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /**
     * 结束时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
}
