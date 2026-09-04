package com.car.customer.module.complaint.service;

import com.car.customer.common.result.PageResult;
import com.car.customer.module.complaint.dto.ComplaintSubmitDTO;
import com.car.customer.module.complaint.vo.ComplaintVO;

public interface ComplaintService {

    /** 提交投诉（需登录） */
    void submit(ComplaintSubmitDTO dto);

    /** 我的投诉记录（分页，按提交时间倒序，可按投诉类型筛选，需登录） */
    PageResult<ComplaintVO> myComplaints(int page, int pageSize, String type);

    /** 投诉详情（仅本人，需登录） */
    ComplaintVO getDetail(Long id);

    /** 对已处理投诉评分（1-5星，仅本人 + 已解决状态，需登录） */
    void rate(Long id, Integer satisfaction);
}
