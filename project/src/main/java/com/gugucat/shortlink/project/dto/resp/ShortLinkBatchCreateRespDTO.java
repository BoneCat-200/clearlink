package com.gugucat.shortlink.project.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 短链接批量创建响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkBatchCreateRespDTO {

    /**
     * 成功数量
     */
    private Integer total;

    /**
     * 批量创建成功的短链接信息
     */
    private List<ShortLinkBaseInfoRespDTO> baseLinkInfos;

    /**
     * 失败的明细（含原链接与原因）
     * <p>
     * 参考实现没有这个字段：失败项被静默丢弃，调用方无法知道哪条没建成。
     */
    private List<ShortLinkBatchCreateFailureRespDTO> failures;
}
