package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.ReconcileReportDO;
import com.gugucat.shortlink.project.dto.biz.ReconcileMismatchDTO;
import com.gugucat.shortlink.project.dto.biz.ReconcileSummaryDTO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计对账持久层
 */
public interface ReconcileMapper extends BaseMapper<ReconcileReportDO> {

    /**
     * 统计某业务日期的对账汇总
     *
     * @param bizDate 业务日期
     * @return 参与对账的短链接数与不一致数
     */
    ReconcileSummaryDTO selectSummary(@Param("bizDate") LocalDate bizDate);

    /**
     * 查询不一致明细，按差值倒序
     *
     * @param bizDate 业务日期
     * @param limit   最多返回多少条
     * @return 不一致明细
     */
    List<ReconcileMismatchDTO> selectMismatchList(@Param("bizDate") LocalDate bizDate,
                                                  @Param("limit") int limit);
}
