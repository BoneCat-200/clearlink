package com.gugucat.shortlink.admin.remote;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gugucat.shortlink.admin.common.convention.result.Result;
import com.gugucat.shortlink.admin.remote.dto.req.RecycleBinRecoverReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.RecycleBinRemoveReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.RecycleBinSaveReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.gugucat.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.gugucat.shortlink.admin.remote.dto.resp.ShortLinkStatsRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 短链接中台远程调用服务
 * <p>
 * 只给服务名不给 url，走 Nacos 服务发现找 clearlink-project 的实例。
 */
@FeignClient(value = "clearlink-project")
public interface ShortLinkActualRemoteService {

    /**
     * 创建短链接
     *
     * @param requestParam 创建短链接请求参数
     * @return 短链接创建响应
     */
    @PostMapping("/api/clearlink/v0.1/create")
    Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam);

    /**
     * 修改短链接
     * <p>
     * 返回 Result 而不是 void：中台的业务异常（如「短链接记录不存在」）是以 HTTP 200 +
     * 错误码 的形式返回的，声明成 void 会把错误码整个丢掉，后管就会误报成功。
     *
     * @param requestParam 修改短链接请求参数
     * @return 修改结果
     */
    @PostMapping("/api/clearlink/v0.1/update")
    Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam);

    /**
     * 分页查询短链接
     *
     * @param gid      分组标识
     * @param orderTag 排序类型
     * @param current  当前页
     * @param size     每页条数
     * @return 查询短链接响应
     */
    @GetMapping("/api/clearlink/v0.1/page")
    Result<Page<ShortLinkPageRespDTO>> pageShortLink(@RequestParam("gid") String gid,
                                                     @RequestParam("orderTag") String orderTag,
                                                     @RequestParam("current") Long current,
                                                     @RequestParam("size") Long size);

    /**
     * 根据 URL 获取标题
     *
     * @param url 目标网站地址
     * @return 网站标题
     */
    @GetMapping("/api/clearlink/v0.1/title")
    Result<String> getTitleByUrl(@RequestParam("url") String url);

    /**
     * 将短链接移入回收站
     * <p>
     * 返回 Result 而不是 void：中台的业务异常（如「短链接不存在或已在回收站中」）是以
     * HTTP 200 + 错误码 的形式返回的，声明成 void 会把错误码整个丢掉、后管误报成功。
     *
     * @param requestParam 移入回收站请求参数
     * @return 操作结果
     */
    @PostMapping("/api/clearlink/v0.1/recycle-bin/save")
    Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO requestParam);

    /**
     * 分页查询回收站短链接
     *
     * @param gidList 分组标识集合
     * @param current 当前页
     * @param size    每页条数
     * @return 查询回收站短链接响应
     */
    @GetMapping("/api/clearlink/v0.1/recycle-bin/page")
    Result<Page<ShortLinkPageRespDTO>> pageRecycleBinShortLink(@RequestParam("gidList") List<String> gidList,
                                                               @RequestParam("current") Long current,
                                                               @RequestParam("size") Long size);

    /**
     * 从回收站恢复短链接
     * <p>
     * 返回 Result 而不是 void，避免中台的业务错误码被吞掉、后管误报成功。
     *
     * @param requestParam 恢复短链接请求参数
     * @return 操作结果
     */
    @PostMapping("/api/clearlink/v0.1/recycle-bin/recover")
    Result<Void> recoverRecycleBin(@RequestBody RecycleBinRecoverReqDTO requestParam);

    /**
     * 从回收站移除短链接
     * <p>
     * 返回 Result 而不是 void，避免中台的业务错误码被吞掉、后管误报成功。
     *
     * @param requestParam 移除短链接请求参数
     * @return 操作结果
     */
    @PostMapping("/api/clearlink/v0.1/recycle-bin/remove")
    Result<Void> removeRecycleBin(@RequestBody RecycleBinRemoveReqDTO requestParam);

    /**
     * 获取单个短链接在指定日期内的监控数据
     * <p>
     * 中台会校验这个 gid 是否属于当前用户，而当前用户是靠
     * {@code OpenFeignConfiguration} 透传的请求头带过去的。
     *
     * @param fullShortUrl 完整短链接
     * @param gid          分组标识
     * @param enableStatus 启用标识
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @return 短链接监控数据
     */
    @GetMapping("/api/clearlink/v0.1/stats")
    Result<ShortLinkStatsRespDTO> oneShortLinkStats(@RequestParam("fullShortUrl") String fullShortUrl,
                                                    @RequestParam("gid") String gid,
                                                    @RequestParam("enableStatus") Integer enableStatus,
                                                    @RequestParam("startDate") String startDate,
                                                    @RequestParam("endDate") String endDate);

    /**
     * 获取指定分组在指定日期内的监控数据
     *
     * @param gid       分组标识
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 分组短链接监控数据
     */
    @GetMapping("/api/clearlink/v0.1/stats/group")
    Result<ShortLinkStatsRespDTO> groupShortLinkStats(@RequestParam("gid") String gid,
                                                      @RequestParam("startDate") String startDate,
                                                      @RequestParam("endDate") String endDate);

    /**
     * 获取单个短链接在指定日期内的访问记录
     *
     * @param fullShortUrl 完整短链接
     * @param gid          分组标识
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @param enableStatus 启用标识
     * @param current      当前页
     * @param size         每页条数
     * @return 访问记录分页
     */
    @GetMapping("/api/clearlink/v0.1/stats/access-record")
    Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(@RequestParam("fullShortUrl") String fullShortUrl,
                                                                              @RequestParam("gid") String gid,
                                                                              @RequestParam("startDate") String startDate,
                                                                              @RequestParam("endDate") String endDate,
                                                                              @RequestParam("enableStatus") Integer enableStatus,
                                                                              @RequestParam("current") Long current,
                                                                              @RequestParam("size") Long size);

    /**
     * 获取指定分组在指定日期内的访问记录
     *
     * @param gid       分组标识
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param current   当前页
     * @param size      每页条数
     * @return 访问记录分页
     */
    @GetMapping("/api/clearlink/v0.1/stats/access-record/group")
    Result<Page<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(@RequestParam("gid") String gid,
                                                                                   @RequestParam("startDate") String startDate,
                                                                                   @RequestParam("endDate") String endDate,
                                                                                   @RequestParam("current") Long current,
                                                                                   @RequestParam("size") Long size);
}
