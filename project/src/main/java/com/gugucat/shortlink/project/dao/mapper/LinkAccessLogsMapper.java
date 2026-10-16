package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.LinkAccessLogsDO;
import com.gugucat.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

/**
 * 访问日志监控持久层
 * <p>
 * 这张表记的是明细（一次跳转一行），没有维度需要累加，所以写侧直接用
 * BaseMapper 自带的 insert。读取侧则完全相反 —— 几乎所有「跨维度」的统计
 * （总计、高频 IP、新老访客）都只能从这张明细表算，聚合表里没有这些信息。
 */
public interface LinkAccessLogsMapper extends BaseMapper<LinkAccessLogsDO> {

    /**
     * 汇总指定日期内的 PV、UV、UIP
     * <p>
     * 不复用 t_link_access_stats 的累加值，而是从明细表重新算：
     * 累加表按「天 + 小时」分行，跨天查询时要再 SUM 一遍，
     * 而 UV/UIP 这类去重指标 SUM 起来是错的（同一个人两天都来过会被算两次）。
     *
     * @param requestParam 监控查询参数
     * @return pv/uv/uip；区间内没有任何访问时返回 null（调用方需判空）
     */
    @Select("""
            SELECT
                COUNT(tlal.user) AS pv,
                COUNT(DISTINCT tlal.user) AS uv,
                COUNT(DISTINCT tlal.ip) AS uip
            FROM
                t_link tl INNER JOIN
                t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
            WHERE
                tlal.full_short_url = #{param.fullShortUrl}
                AND tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = #{param.enableStatus}
                AND tlal.create_time BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tlal.full_short_url, tl.gid;
            """)
    LinkAccessStatsDO findPvUvUidStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 取指定日期内访问次数最多的 5 个 IP
     *
     * @param requestParam 监控查询参数
     * @return 每项含 ip 与 count 两个键
     */
    @Select("""
            SELECT
                tlal.ip,
                COUNT(tlal.ip) AS count
            FROM
                t_link tl INNER JOIN
                t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
            WHERE
                tlal.full_short_url = #{param.fullShortUrl}
                AND tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = #{param.enableStatus}
                AND tlal.create_time BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tlal.full_short_url, tl.gid, tlal.ip
            ORDER BY
                count DESC
            LIMIT 5;
            """)
    List<HashMap<String, Object>> listTopIpByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 汇总新老访客数
     * <p>
     * 先按访客分组，再到外层汇总，判断口径是「这个访客在全部历史里
     * 是不是只出现过一天」：只出现过一天算新访客，跨天出现过算老访客。
     * <p>
     * 注意这里<b>刻意不带日期条件</b>参与分组 —— 要判断「新」还是「老」，
     * 必须看这个访客的全部历史，只截取查询区间会把他一律算成新访客。
     *
     * @param requestParam 监控查询参数
     * @return 含 oldUserCnt 与 newUserCnt 两个键；区间内没有访客时两者都是 null（调用方需判空）
     */
    @Select("""
            SELECT
                SUM(old_user) AS oldUserCnt,
                SUM(new_user) AS newUserCnt
            FROM (
                SELECT
                    CASE WHEN COUNT(DISTINCT DATE(tlal.create_time)) > 1 THEN 1 ELSE 0 END AS old_user,
                    CASE WHEN COUNT(DISTINCT DATE(tlal.create_time)) = 1 AND MAX(tlal.create_time) >= #{param.startDate} AND MAX(tlal.create_time) <= #{param.endDate} THEN 1 ELSE 0 END AS new_user
                FROM
                    t_link tl INNER JOIN
                    t_link_access_logs tlal ON tl.full_short_url = tlal.full_short_url
                WHERE
                    tlal.full_short_url = #{param.fullShortUrl}
                    AND tl.gid = #{param.gid}
                    AND tl.enable_status = #{param.enableStatus}
                    AND tl.del_flag = '0'
                GROUP BY
                    tlal.user
            ) AS user_counts;
            """)
    HashMap<String, Object> findUvTypeCntByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);
}
