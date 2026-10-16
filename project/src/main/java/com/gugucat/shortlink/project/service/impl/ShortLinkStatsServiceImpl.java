package com.gugucat.shortlink.project.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gugucat.shortlink.project.common.biz.user.UserContext;
import com.gugucat.shortlink.project.common.convention.exception.ClientException;
import com.gugucat.shortlink.project.common.convention.exception.ServiceException;
import com.gugucat.shortlink.project.dao.entity.GroupDO;
import com.gugucat.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkDeviceStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkLocaleStatsDO;
import com.gugucat.shortlink.project.dao.entity.LinkNetworkStatsDO;
import com.gugucat.shortlink.project.dao.mapper.LinkAccessLogsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkAccessStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkBrowserStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkDeviceStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkGroupMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkLocaleStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkNetworkStatsMapper;
import com.gugucat.shortlink.project.dao.mapper.LinkOsStatsMapper;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsAccessDailyRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsBrowserRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsDeviceRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsLocaleCNRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsNetworkRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsOsRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsTopIpRespDTO;
import com.gugucat.shortlink.project.dto.resp.ShortLinkStatsUvRespDTO;
import com.gugucat.shortlink.project.service.ShortLinkStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 短链接监控接口实现层
 * <p>
 * 这里<b>不做预聚合</b>：一次请求会打十余条 GROUP BY 查询。之所以能接受，
 * 是因为它们都落在按 full_short_url 索引的明细/聚合表上，且查询区间通常是小时级；
 * 一旦要看「全部短链接」的横向对比，这个思路就撑不住了（那属于分组维度的统计）。
 */
@Service
@RequiredArgsConstructor
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {

    private final LinkGroupMapper linkGroupMapper;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;

    /**
     * 结束日期补上当天最后一刻
     * <p>
     * 库里两类时间列混用：{@code t_link_access_stats.date} 是 DATE，
     * 而 {@code t_link_access_logs.create_time} 是 DATETIME。
     * 拿 {@code 'yyyy-MM-dd'} 这种只有日期的字符串去做 BETWEEN 的右边界时，
     * 对 DATE 列是按当天零点比较（当天算在内），对 DATETIME 列也是按当天零点比较
     * —— 于是<b>当天 00:00 之后的访问全部落在区间外</b>。
     * 结果就是按天分布里有当天的数据、而总计 PV/UV/UIP 里没有，两个数对不上。
     * 把右边界补到 {@code 23:59:59} 之后，两类列的比较都变成闭区间。
     */
    private static final String END_OF_DAY_SUFFIX = " 23:59:59";

    /**
     * 日期参数的格式，只接受 yyyy-MM-dd
     * <p>
     * 必须显式校验格式，不能只靠「解析失败就抛异常」兜着：右边界是在原始字符串后面
     * 直接拼 {@code " 23:59:59"} 的，一个本来就不是日期的串（比如已经带了时分秒，
     * 或干脆是乱填的）拼完会变成一个更离谱的值，最后在 SQL 里比较出莫名其妙的结果。
     */
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override
    public ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        checkRequestParam(requestParam);
        // 归属校验必须在查询之前：下面的 SQL 只按 gid 过滤，不校验 gid 是否属于当前用户
        checkGroupBelongToUser(requestParam.getGid());
        List<LinkAccessStatsDO> listStatsByShortLink = linkAccessStatsMapper.listStatsByShortLink(requestParam);
        if (CollUtil.isEmpty(listStatsByShortLink)) {
            // 区间内没有任何访问。返回 null 而不是一堆 0：
            // 调用方据此可以把「没数据」和「有数据但都是 0」区分开
            return null;
        }
        // 基础访问数据。这张表和上面那张表来自不同的表、且落库时不在同一个事务里，
        // 理论上存在「累加表有数据、明细表还没有」的中间态，所以这里必须判空，
        // 否则一次查询就会变成 500（参考实现直接 .getPv() 会空指针）
        LinkAccessStatsDO pvUvUipStatsByShortLink = linkAccessLogsMapper.findPvUvUidStatsByShortLink(requestParam);
        if (pvUvUipStatsByShortLink == null) {
            pvUvUipStatsByShortLink = LinkAccessStatsDO.builder().pv(0).uv(0).uip(0).build();
        }
        // 基础访问详情：先把区间内每一天都铺出来，再拿查到的那天去填，
        // 查不到的补 0，保证前端拿到的日期轴是连续的
        List<ShortLinkStatsAccessDailyRespDTO> daily = new ArrayList<>();
        List<String> rangeDates = DateUtil.rangeToList(DateUtil.parse(requestParam.getStartDate()), DateUtil.parse(requestParam.getEndDate()), DateField.DAY_OF_MONTH)
                .stream()
                .map(DateUtil::formatDate)
                .toList();
        rangeDates.forEach(each -> {
            Optional<LinkAccessStatsDO> matched = listStatsByShortLink.stream()
                    .filter(item -> Objects.equals(each, DateUtil.formatDate(item.getDate())))
                    .findFirst();
            daily.add(matched
                    .map(item -> ShortLinkStatsAccessDailyRespDTO.builder()
                            .date(each)
                            .pv(item.getPv())
                            .uv(item.getUv())
                            .uip(item.getUip())
                            .build())
                    .orElseGet(() -> ShortLinkStatsAccessDailyRespDTO.builder()
                            .date(each)
                            .pv(0)
                            .uv(0)
                            .uip(0)
                            .build()));
        });
        // 地区访问详情（仅国内）。占比的分母是各地区的合计，不是总 PV ——
        // 地区查询失败的那些访问会被记为「未知」并计入合计，
        // 所以这个分母与总 PV 一致（除非历史上存在没写成地区的旧数据）
        List<ShortLinkStatsLocaleCNRespDTO> localeCnStats = new ArrayList<>();
        List<LinkLocaleStatsDO> listedLocaleByShortLink = linkLocaleStatsMapper.listLocaleByShortLink(requestParam);
        int localeCnSum = listedLocaleByShortLink.stream()
                .mapToInt(LinkLocaleStatsDO::getCnt)
                .sum();
        listedLocaleByShortLink.forEach(each -> localeCnStats.add(ShortLinkStatsLocaleCNRespDTO.builder()
                .cnt(each.getCnt())
                .locale(each.getProvince())
                .ratio(calcRatio(each.getCnt(), localeCnSum))
                .build()));
        // 小时访问详情：固定 24 个元素，下标即小时，没有访问的小时补 0
        List<Integer> hourStats = new ArrayList<>();
        List<LinkAccessStatsDO> listHourStatsByShortLink = linkAccessStatsMapper.listHourStatsByShortLink(requestParam);
        for (int i = 0; i < 24; i++) {
            AtomicInteger hour = new AtomicInteger(i);
            int hourCnt = listHourStatsByShortLink.stream()
                    .filter(each -> Objects.equals(each.getHour(), hour.get()))
                    .findFirst()
                    .map(LinkAccessStatsDO::getPv)
                    .orElse(0);
            hourStats.add(hourCnt);
        }
        // 高频访问IP详情
        List<ShortLinkStatsTopIpRespDTO> topIpStats = new ArrayList<>();
        List<HashMap<String, Object>> listTopIpByShortLink = linkAccessLogsMapper.listTopIpByShortLink(requestParam);
        listTopIpByShortLink.forEach(each -> topIpStats.add(ShortLinkStatsTopIpRespDTO.builder()
                .ip(Objects.toString(each.get("ip"), "未知"))
                .cnt(Integer.parseInt(Objects.toString(each.get("count"), "0")))
                .build()));
        // 一周访问详情：固定 7 个元素，下标 0 对应 weekday=1（周一）
        List<Integer> weekdayStats = new ArrayList<>();
        List<LinkAccessStatsDO> listWeekdayStatsByShortLink = linkAccessStatsMapper.listWeekdayStatsByShortLink(requestParam);
        for (int i = 1; i < 8; i++) {
            AtomicInteger weekday = new AtomicInteger(i);
            int weekdayCnt = listWeekdayStatsByShortLink.stream()
                    .filter(each -> Objects.equals(each.getWeekday(), weekday.get()))
                    .findFirst()
                    .map(LinkAccessStatsDO::getPv)
                    .orElse(0);
            weekdayStats.add(weekdayCnt);
        }
        // 浏览器访问详情
        List<ShortLinkStatsBrowserRespDTO> browserStats = new ArrayList<>();
        List<HashMap<String, Object>> listBrowserStatsByShortLink = linkBrowserStatsMapper.listBrowserStatsByShortLink(requestParam);
        int browserSum = listBrowserStatsByShortLink.stream()
                .mapToInt(each -> Integer.parseInt(Objects.toString(each.get("count"), "0")))
                .sum();
        listBrowserStatsByShortLink.forEach(each -> {
            int cnt = Integer.parseInt(Objects.toString(each.get("count"), "0"));
            browserStats.add(ShortLinkStatsBrowserRespDTO.builder()
                    .cnt(cnt)
                    .browser(Objects.toString(each.get("browser"), "未知"))
                    .ratio(calcRatio(cnt, browserSum))
                    .build());
        });
        // 操作系统访问详情
        List<ShortLinkStatsOsRespDTO> osStats = new ArrayList<>();
        List<HashMap<String, Object>> listOsStatsByShortLink = linkOsStatsMapper.listOsStatsByShortLink(requestParam);
        int osSum = listOsStatsByShortLink.stream()
                .mapToInt(each -> Integer.parseInt(Objects.toString(each.get("count"), "0")))
                .sum();
        listOsStatsByShortLink.forEach(each -> {
            int cnt = Integer.parseInt(Objects.toString(each.get("count"), "0"));
            osStats.add(ShortLinkStatsOsRespDTO.builder()
                    .cnt(cnt)
                    .os(Objects.toString(each.get("os"), "未知"))
                    .ratio(calcRatio(cnt, osSum))
                    .build());
        });
        // 访客访问类型详情。固定返回两条，没有数据时是两条 0，而不是空列表
        // —— 前端的新老访客饼图按固定分类渲染
        List<ShortLinkStatsUvRespDTO> uvTypeStats = new ArrayList<>();
        HashMap<String, Object> findUvTypeByShortLink = linkAccessLogsMapper.findUvTypeCntByShortLink(requestParam);
        int oldUserCnt = Integer.parseInt(Optional.ofNullable(findUvTypeByShortLink)
                .map(each -> each.get("oldUserCnt"))
                .map(Object::toString)
                .orElse("0"));
        int newUserCnt = Integer.parseInt(Optional.ofNullable(findUvTypeByShortLink)
                .map(each -> each.get("newUserCnt"))
                .map(Object::toString)
                .orElse("0"));
        int uvSum = oldUserCnt + newUserCnt;
        uvTypeStats.add(ShortLinkStatsUvRespDTO.builder()
                .uvType("newUser")
                .cnt(newUserCnt)
                .ratio(calcRatio(newUserCnt, uvSum))
                .build());
        uvTypeStats.add(ShortLinkStatsUvRespDTO.builder()
                .uvType("oldUser")
                .cnt(oldUserCnt)
                .ratio(calcRatio(oldUserCnt, uvSum))
                .build());
        // 访问设备类型详情
        List<ShortLinkStatsDeviceRespDTO> deviceStats = new ArrayList<>();
        List<LinkDeviceStatsDO> listDeviceStatsByShortLink = linkDeviceStatsMapper.listDeviceStatsByShortLink(requestParam);
        int deviceSum = listDeviceStatsByShortLink.stream()
                .mapToInt(LinkDeviceStatsDO::getCnt)
                .sum();
        listDeviceStatsByShortLink.forEach(each -> deviceStats.add(ShortLinkStatsDeviceRespDTO.builder()
                .cnt(each.getCnt())
                .device(each.getDevice())
                .ratio(calcRatio(each.getCnt(), deviceSum))
                .build()));
        // 访问网络类型详情
        List<ShortLinkStatsNetworkRespDTO> networkStats = new ArrayList<>();
        List<LinkNetworkStatsDO> listNetworkStatsByShortLink = linkNetworkStatsMapper.listNetworkStatsByShortLink(requestParam);
        int networkSum = listNetworkStatsByShortLink.stream()
                .mapToInt(LinkNetworkStatsDO::getCnt)
                .sum();
        listNetworkStatsByShortLink.forEach(each -> networkStats.add(ShortLinkStatsNetworkRespDTO.builder()
                .cnt(each.getCnt())
                .network(each.getNetwork())
                .ratio(calcRatio(each.getCnt(), networkSum))
                .build()));
        return ShortLinkStatsRespDTO.builder()
                .pv(pvUvUipStatsByShortLink.getPv())
                .uv(pvUvUipStatsByShortLink.getUv())
                .uip(pvUvUipStatsByShortLink.getUip())
                .daily(daily)
                .localeCnStats(localeCnStats)
                .hourStats(hourStats)
                .topIpStats(topIpStats)
                .weekdayStats(weekdayStats)
                .browserStats(browserStats)
                .osStats(osStats)
                .uvTypeStats(uvTypeStats)
                .deviceStats(deviceStats)
                .networkStats(networkStats)
                .build();
    }

    /**
     * 校验当前用户是否拥有该分组
     * <p>
     * 监控查询的所有条件里只有 gid，没有用户。不做这一步的话，
     * 任何登录用户只要猜到 gid 就能看到别人短链接的完整监控数据。
     *
     * @param gid 分组标识
     */
    private void checkGroupBelongToUser(String gid) {
        String username = Optional.ofNullable(UserContext.getUsername())
                // 中台只被后管调用，这里拿不到用户名说明请求没经过后管
                //（少了 username 请求头），属于调用方用错了接口
                .orElseThrow(() -> new ServiceException("用户未登录"));
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername, username);
        List<GroupDO> groupDOList = linkGroupMapper.selectList(queryWrapper);
        if (CollUtil.isEmpty(groupDOList)) {
            throw new ServiceException("用户信息与分组标识不匹配");
        }
    }

    /**
     * 校验监控查询的必填参数
     * <p>
     * 这些参数缺失时 SQL 并不会报错，只会查不到任何数据 ——
     * 调用方拿到一个空的监控面板，却不知道是「这个短链接没人访问」还是「自己少传了参数」。
     *
     * @param requestParam 监控查询参数
     */
    private void checkRequestParam(ShortLinkStatsReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getFullShortUrl())
                || StrUtil.isBlank(requestParam.getGid())
                || StrUtil.isBlank(requestParam.getStartDate())
                || StrUtil.isBlank(requestParam.getEndDate())
                || requestParam.getEnableStatus() == null) {
            throw new ClientException("完整短链接、分组标识、开始日期、结束日期与启用标识不能为空");
        }
        if (!DATE_PATTERN.matcher(requestParam.getStartDate()).matches()
                || !DATE_PATTERN.matcher(requestParam.getEndDate()).matches()) {
            throw new ClientException("日期格式不正确，应为 yyyy-MM-dd");
        }
        if (requestParam.getStartDate().compareTo(requestParam.getEndDate()) > 0) {
            // yyyy-MM-dd 是定长格式，字典序即时间序，不需要真的解析成日期去比
            throw new ClientException("开始日期不能晚于结束日期");
        }
        // 右边界补到当天最后一刻，否则当天有访问的明细数据会被排除在区间外。
        // 放在这里统一处理，是因为有 4 条 SQL 都拿 endDate 去比较 DATETIME 列
        requestParam.setEndDate(requestParam.getEndDate() + END_OF_DAY_SUFFIX);
    }

    /**
     * 计算占比
     *
     * @param cnt 该项的计数
     * @param sum 各项的合计
     * @return 0~1 之间保留两位小数的占比；合计为 0 时返回 0
     */
    private double calcRatio(int cnt, int sum) {
        if (sum == 0) {
            // 参考实现这里会算出 0/0 = NaN，再被 Math.round(NaN) 兜成 0。
            // 结果虽然一样，但显式短路让意图清楚，也免得将来换了取整方式就漏出 NaN
            return 0.0;
        }
        return Math.round((double) cnt / sum * 100.0) / 100.0;
    }
}
