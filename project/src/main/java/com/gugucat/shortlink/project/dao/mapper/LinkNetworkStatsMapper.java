package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.LinkNetworkStatsDO;
import com.gugucat.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 访问网络统计访问持久层
 */
public interface LinkNetworkStatsMapper extends BaseMapper<LinkNetworkStatsDO> {

    /**
     * 累加访问网络监控数据
     * <p>
     * 唯一键是 (full_short_url, date, network)，重复插入时累加 cnt。
     *
     * @param linkNetworkStatsDO 本次访问的网络统计数据
     */
    @Insert("INSERT INTO " +
            "t_link_network_stats (full_short_url, date, cnt, network, create_time, update_time, del_flag) " +
            "VALUES( #{linkNetworkStats.fullShortUrl}, #{linkNetworkStats.date}, #{linkNetworkStats.cnt}, #{linkNetworkStats.network}, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE cnt = cnt + #{linkNetworkStats.cnt};")
    void shortLinkNetworkState(@Param("linkNetworkStats") LinkNetworkStatsDO linkNetworkStatsDO);

    /**
     * 按访问网络汇总指定日期内的访问量
     *
     * @param requestParam 监控查询参数
     * @return 每种网络一条的 cnt
     */
    @Select("""
            SELECT
                tlns.network,
                SUM(tlns.cnt) AS cnt
            FROM
                t_link tl INNER JOIN
                t_link_network_stats tlns ON tl.full_short_url = tlns.full_short_url
            WHERE
                tlns.full_short_url = #{param.fullShortUrl}
                AND tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = #{param.enableStatus}
                AND tlns.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tlns.full_short_url, tl.gid, tlns.network;
            """)
    List<LinkNetworkStatsDO> listNetworkStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 按访问网络汇总指定分组在指定日期内的访问量
     *
     * @param requestParam 分组监控查询参数
     * @return 每种网络一条的 cnt
     */
    @Select("""
            SELECT
                tlns.network,
                SUM(tlns.cnt) AS cnt
            FROM
                t_link tl INNER JOIN
                t_link_network_stats tlns ON tl.full_short_url = tlns.full_short_url
            WHERE
                tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = '0'
                AND tlns.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tl.gid, tlns.network;
            """)
    List<LinkNetworkStatsDO> listNetworkStatsByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);
}
