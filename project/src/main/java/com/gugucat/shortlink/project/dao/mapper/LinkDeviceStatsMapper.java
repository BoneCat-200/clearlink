package com.gugucat.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gugucat.shortlink.project.dao.entity.LinkDeviceStatsDO;
import com.gugucat.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.gugucat.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 访问设备统计访问持久层
 */
public interface LinkDeviceStatsMapper extends BaseMapper<LinkDeviceStatsDO> {

    /**
     * 累加访问设备监控数据
     * <p>
     * 唯一键是 (full_short_url, date, device)，重复插入时累加 cnt。
     *
     * @param linkDeviceStatsDO 本次访问的设备统计数据
     */
    @Insert("INSERT INTO " +
            "t_link_device_stats (full_short_url, date, cnt, device, create_time, update_time, del_flag) " +
            "VALUES( #{linkDeviceStats.fullShortUrl}, #{linkDeviceStats.date}, #{linkDeviceStats.cnt}, #{linkDeviceStats.device}, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE cnt = cnt + #{linkDeviceStats.cnt};")
    void shortLinkDeviceState(@Param("linkDeviceStats") LinkDeviceStatsDO linkDeviceStatsDO);

    /**
     * 按访问设备汇总指定日期内的访问量
     *
     * @param requestParam 监控查询参数
     * @return 每种设备一条的 cnt
     */
    @Select("""
            SELECT
                tlds.device,
                SUM(tlds.cnt) AS cnt
            FROM
                t_link tl INNER JOIN
                t_link_device_stats tlds ON tl.full_short_url = tlds.full_short_url
            WHERE
                tlds.full_short_url = #{param.fullShortUrl}
                AND tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = #{param.enableStatus}
                AND tlds.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tlds.full_short_url, tl.gid, tlds.device;
            """)
    List<LinkDeviceStatsDO> listDeviceStatsByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 按访问设备汇总指定分组在指定日期内的访问量
     *
     * @param requestParam 分组监控查询参数
     * @return 每种设备一条的 cnt
     */
    @Select("""
            SELECT
                tlds.device,
                SUM(tlds.cnt) AS cnt
            FROM
                t_link tl INNER JOIN
                t_link_device_stats tlds ON tl.full_short_url = tlds.full_short_url
            WHERE
                tl.gid = #{param.gid}
                AND tl.del_flag = '0'
                AND tl.enable_status = '0'
                AND tlds.date BETWEEN #{param.startDate} and #{param.endDate}
            GROUP BY
                tl.gid, tlds.device;
            """)
    List<LinkDeviceStatsDO> listDeviceStatsByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);
}
