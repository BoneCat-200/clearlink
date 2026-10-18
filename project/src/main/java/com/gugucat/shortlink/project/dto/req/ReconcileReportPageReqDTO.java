package com.gugucat.shortlink.project.dto.req;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.project.dao.entity.ReconcileReportDO;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 统计对账报告分页请求
 * <p>
 * 继承 Page 是为了让 MyBatis-Plus 的分页拦截器能从参数里认出 IPage。
 */
@Data
public class ReconcileReportPageReqDTO extends Page<ReconcileReportDO> {

    /**
     * 业务日期，不传则查全部
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate bizDate;

    /**
     * 状态：0 执行中，1 成功，2 失败；不传则查全部
     */
    private Integer status;
}
