package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gugucat.shortlink.project.common.biz.user.UserContext;
import com.gugucat.shortlink.project.common.convention.exception.AbstractException;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.common.convention.exception.ServiceException;
import com.gugucat.shortlink.project.common.enums.ReconcileStatusEnum;
import com.gugucat.shortlink.project.common.enums.ReconcileTriggerTypeEnum;
import com.gugucat.shortlink.project.dao.entity.ReconcileReportDO;
import com.gugucat.shortlink.project.dao.mapper.ReconcileMapper;
import com.gugucat.shortlink.project.dto.biz.ReconcileMismatchDTO;
import com.gugucat.shortlink.project.dto.biz.ReconcileSummaryDTO;
import com.gugucat.shortlink.project.dto.req.ReconcileReportPageReqDTO;
import com.gugucat.shortlink.project.dto.resp.ReconcileReportRespDTO;
import com.gugucat.shortlink.project.service.ReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static com.gugucat.shortlink.project.common.constant.RedisKeyConstant.LOCK_RECONCILE_KEY;

/**
 * 统计对账业务层实现
 * <p>
 * 一次对账分三步：先落一条「执行中」报告，再查汇总与明细，最后把报告更新为成功或失败。
 * 报告先落库而不是最后才插，是为了让执行中的任务可被查询到；
 * 如果进程在执行中崩溃，这条记录会停留在「执行中」，便于发现异常任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileServiceImpl implements ReconcileService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ReconcileMapper reconcileMapper;
    private final RedissonClient redissonClient;

    /**
     * 报告里最多保存多少条不一致明细
     */
    @Value("${short-link.reconcile.max-detail-size:200}")
    private Integer maxDetailSize;

    /**
     * 允许手动触发对账的用户名，逗号分隔
     * <p>
     * 系统暂无 RBAC，用配置白名单把「运维动作」限制在指定账号上；
     * 定时任务不受此限制。
     */
    @Value("${short-link.reconcile.allowed-operators:admin}")
    private List<String> allowedOperators;

    @Override
    public ReconcileReportRespDTO reconcile(LocalDate bizDate, ReconcileTriggerTypeEnum triggerType) {
        LocalDate actualBizDate = bizDate == null ? LocalDate.now().minusDays(1) : bizDate;
        if (actualBizDate.isAfter(LocalDate.now())) {
            throw new ClientException("对账日期不能晚于今天");
        }
        String operator = null;
        if (triggerType == ReconcileTriggerTypeEnum.MANUAL) {
            operator = UserContext.getUsername();
            if (StrUtil.isBlank(operator) || !isAllowedOperator(operator)) {
                throw new ClientException("无权限触发对账任务");
            }
        }
        // 按业务日期加锁：定时任务和手动触发同时跑同一日期，会写出两份互相覆盖认知的报告
        RLock lock = redissonClient.getLock(String.format(LOCK_RECONCILE_KEY, actualBizDate));
        // 不等待：拿不到锁说明同一天已有任务在跑，直接让调用方稍后再试
        boolean locked = lock.tryLock();
        if (!locked) {
            throw new ClientException("对账任务正在执行中");
        }
        long start = System.currentTimeMillis();
        ReconcileReportDO report = ReconcileReportDO.builder()
                .bizDate(actualBizDate)
                .triggerType(triggerType.getType())
                .operator(operator)
                .totalLinks(0L)
                .matchedLinks(0L)
                .mismatchLinks(0L)
                .status(ReconcileStatusEnum.RUNNING.getStatus())
                .build();
        reconcileMapper.insert(report);
        try {
            ReconcileSummaryDTO summary = reconcileMapper.selectSummary(actualBizDate);
            long totalLinks = summary == null || summary.getTotalLinks() == null ? 0L : summary.getTotalLinks();
            long mismatchLinks = summary == null || summary.getMismatchLinks() == null ? 0L : summary.getMismatchLinks();
            List<ReconcileMismatchDTO> mismatchDetail = mismatchLinks > 0
                    ? reconcileMapper.selectMismatchList(actualBizDate, maxDetailSize)
                    : Collections.emptyList();

            report.setTotalLinks(totalLinks);
            report.setMatchedLinks(totalLinks - mismatchLinks);
            report.setMismatchLinks(mismatchLinks);
            report.setStatus(ReconcileStatusEnum.SUCCESS.getStatus());
            report.setMismatchDetail(mismatchDetail.isEmpty() ? null : JSON.toJSONString(mismatchDetail));
            report.setCostMs(System.currentTimeMillis() - start);
            reconcileMapper.updateById(report);
            log.info("统计对账完成，bizDate: {}, totalLinks: {}, mismatchLinks: {}, costMs: {}",
                    actualBizDate, totalLinks, mismatchLinks, report.getCostMs());
            return toResp(reconcileMapper.selectById(report.getId()));
        } catch (Throwable ex) {
            report.setStatus(ReconcileStatusEnum.FAILED.getStatus());
            // 不能使用 StrUtil.maxLength：它会在截断后追加 "..."，
            // 结果最多 515 个字符，写 varchar(512) 会再次失败，导致失败报告也落不了库
            report.setErrorMessage(StrUtil.subPre(ex.getMessage(), 512));
            report.setCostMs(System.currentTimeMillis() - start);
            reconcileMapper.updateById(report);
            log.error("统计对账失败，bizDate: {}", actualBizDate, ex);
            if (ex instanceof AbstractException) {
                throw (AbstractException) ex;
            }
            throw new ServiceException("对账任务执行失败：" + ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public IPage<ReconcileReportRespDTO> pageReconcileReport(ReconcileReportPageReqDTO requestParam) {
        // 分页参数兜底：负数页码或超大 size 都会让数据库做无意义的扫描
        if (requestParam.getCurrent() < 1) {
            requestParam.setCurrent(1);
        }
        if (requestParam.getSize() < 1) {
            requestParam.setSize(DEFAULT_PAGE_SIZE);
        }
        if (requestParam.getSize() > MAX_PAGE_SIZE) {
            requestParam.setSize(MAX_PAGE_SIZE);
        }
        LambdaQueryWrapper<ReconcileReportDO> queryWrapper = Wrappers.lambdaQuery(ReconcileReportDO.class)
                .eq(requestParam.getBizDate() != null, ReconcileReportDO::getBizDate, requestParam.getBizDate())
                .eq(requestParam.getStatus() != null, ReconcileReportDO::getStatus, requestParam.getStatus())
                .orderByDesc(ReconcileReportDO::getCreateTime)
                .orderByDesc(ReconcileReportDO::getId);
        IPage<ReconcileReportDO> page = reconcileMapper.selectPage(requestParam, queryWrapper);
        return page.convert(this::toResp);
    }

    /**
     * 判断当前用户是否允许手动触发对账
     *
     * @param operator 当前用户名
     * @return 是否允许
     */
    private boolean isAllowedOperator(String operator) {
        if (allowedOperators == null) {
            return false;
        }
        return allowedOperators.stream()
                .map(StrUtil::trim)
                .anyMatch(operator::equals);
    }

    /**
     * 实体转响应对象
     *
     * @param reportDO 报告实体
     * @return 响应对象
     */
    private ReconcileReportRespDTO toResp(ReconcileReportDO reportDO) {
        if (reportDO == null) {
            return null;
        }
        ReconcileStatusEnum statusEnum = ReconcileStatusEnum.of(reportDO.getStatus());
        List<ReconcileMismatchDTO> mismatchDetail = StrUtil.isBlank(reportDO.getMismatchDetail())
                ? Collections.emptyList()
                : JSON.parseArray(reportDO.getMismatchDetail(), ReconcileMismatchDTO.class);
        return ReconcileReportRespDTO.builder()
                .id(reportDO.getId())
                .bizDate(reportDO.getBizDate())
                .triggerType(reportDO.getTriggerType())
                .operator(reportDO.getOperator())
                .totalLinks(reportDO.getTotalLinks())
                .matchedLinks(reportDO.getMatchedLinks())
                .mismatchLinks(reportDO.getMismatchLinks())
                .status(reportDO.getStatus())
                .statusDesc(statusEnum == null ? null : statusEnum.getDescription())
                .mismatchDetail(mismatchDetail)
                .errorMessage(reportDO.getErrorMessage())
                .costMs(reportDO.getCostMs())
                .createTime(reportDO.getCreateTime())
                .build();
    }
}
