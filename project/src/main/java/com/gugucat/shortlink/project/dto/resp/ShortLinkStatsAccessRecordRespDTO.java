package com.gugucat.shortlink.project.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接监控访问记录响应参数
 * <p>
 * 一条记录 = 一次跳转。这是监控里唯一直达明细的接口，
 * 其余接口返回的都是聚合后的数字。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkStatsAccessRecordRespDTO {

    /**
     * 访客类型，取值「新访客」/「老访客」
     * <p>
     * 注意与「访客访问类型详情」（`uvTypeStats`）的取值不同：
     * 那边是 `newUser`/`oldUser`。同一份数据在两个接口里用了两种表示，
     * 是参考实现就有的不一致（见开发文档已知问题）。
     */
    private String uvType;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * ip
     */
    private String ip;

    /**
     * 访问网络
     */
    private String network;

    /**
     * 访问设备
     */
    private String device;

    /**
     * 地区
     */
    private String locale;

    /**
     * 用户信息，即访客的 UV 标识
     */
    private String user;

    /**
     * 访问时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
