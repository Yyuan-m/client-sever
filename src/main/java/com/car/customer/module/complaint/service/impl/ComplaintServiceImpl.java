package com.car.customer.module.complaint.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.result.PageResult;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.AfterSalesComplaint;
import com.car.customer.entity.Member;
import com.car.customer.entity.RentalOrder;
import com.car.customer.entity.SysDictData;
import com.car.customer.mapper.AfterSalesComplaintMapper;
import com.car.customer.mapper.MemberMapper;
import com.car.customer.mapper.RentalOrderMapper;
import com.car.customer.mapper.SysDictDataMapper;
import com.car.customer.module.complaint.dto.ComplaintSubmitDTO;
import com.car.customer.module.complaint.service.ComplaintService;
import com.car.customer.module.complaint.vo.ComplaintVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 投诉服务（跨库写入 car_rental.after_sales_complaint，与后台管理系统共用）
 * 提交：自动记录当前会员、订单归属校验、字典映射类型名、默认优先级/状态
 * 查询：按 member_id 返回"我的投诉"记录（状态中文名来自 complaint_status 字典）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private static final String DICT_COMPLAINT_TYPE = "complaint_type";
    private static final String DICT_COMPLAINT_STATUS = "complaint_status";
    private static final String DEFAULT_PRIORITY = "normal";
    private static final String DEFAULT_STATUS = "pending";

    private final AfterSalesComplaintMapper complaintMapper;
    private final MemberMapper memberMapper;
    private final RentalOrderMapper orderMapper;
    private final SysDictDataMapper sysDictDataMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void submit(ComplaintSubmitDTO dto) {
        Long memberId = SecurityUtil.getCurrentMemberId();

        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException("账号不存在");
        }

        // 投诉类型校验 + 类型名称（字典 complaint_type）
        SysDictData typeDict = sysDictDataMapper.selectOne(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, DICT_COMPLAINT_TYPE)
                .eq(SysDictData::getDictValue, dto.getType())
                .eq(SysDictData::getStatus, 1)
                .last("LIMIT 1"));
        if (typeDict == null) {
            throw new BusinessException("投诉类型不存在，请重新选择");
        }

        AfterSalesComplaint complaint = new AfterSalesComplaint();
        complaint.setMemberId(memberId);
        complaint.setCustomerName(displayName(member));
        complaint.setType(dto.getType());
        complaint.setTypeName(typeDict.getDictLabel());
        complaint.setDescription(dto.getDescription().trim());

        // 可选关联订单：校验归属后回填 orderId/orderNo
        if (StringUtils.hasText(dto.getOrderNo())) {
            String orderNo = dto.getOrderNo().trim();
            RentalOrder order = orderMapper.selectOne(new LambdaQueryWrapper<RentalOrder>()
                    .eq(RentalOrder::getOrderNo, orderNo)
                    .eq(RentalOrder::getMemberId, memberId)
                    .last("LIMIT 1"));
            if (order == null) {
                throw new BusinessException("订单不存在或不属于当前账号，请核对订单号");
            }
            complaint.setOrderId(order.getId());
            complaint.setOrderNo(order.getOrderNo());
        }

        // 凭证图片：List → JSON数组字符串（与评价图片存储约定一致）
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            List<String> images = dto.getImages().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
            if (!images.isEmpty()) {
                complaint.setImages(toJson(images));
            }
        }

        complaint.setPriority(DEFAULT_PRIORITY);
        complaint.setStatus(DEFAULT_STATUS);
        complaint.setTicketNo(generateTicketNo());
        complaint.setCreatedAt(LocalDateTime.now());
        complaintMapper.insert(complaint);

        log.info("投诉提交成功: id={}, ticketNo={}, memberId={}, type={}, orderNo={}",
                complaint.getId(), complaint.getTicketNo(), memberId, dto.getType(), dto.getOrderNo());
    }

    @Override
    public PageResult<ComplaintVO> myComplaints(int page, int pageSize, String type) {
        Long memberId = SecurityUtil.getCurrentMemberId();

        // 状态中文名映射（字典 complaint_status）
        Map<String, String> statusNameMap = statusNameMap();

        Page<AfterSalesComplaint> result = complaintMapper.selectPage(
                new Page<>(page, Math.min(pageSize, 50)),
                new LambdaQueryWrapper<AfterSalesComplaint>()
                        .eq(AfterSalesComplaint::getMemberId, memberId)
                        .eq(StringUtils.hasText(type), AfterSalesComplaint::getType, type)
                        .orderByDesc(AfterSalesComplaint::getCreatedAt));

        List<ComplaintVO> list = result.getRecords().stream()
                .map(c -> toVO(c, statusNameMap))
                .toList();
        return PageResult.of(list, result.getTotal(), page, Math.min(pageSize, 50));
    }

    @Override
    public ComplaintVO getDetail(Long id) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        AfterSalesComplaint complaint = complaintMapper.selectById(id);
        if (complaint == null) {
            throw new BusinessException("投诉工单不存在");
        }
        if (!memberId.equals(complaint.getMemberId())) {
            throw new BusinessException(403, "无权查看该投诉");
        }
        return toVO(complaint, statusNameMap());
    }

    @Override
    @Transactional
    public void rate(Long id, Integer satisfaction) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        AfterSalesComplaint complaint = complaintMapper.selectById(id);
        if (complaint == null) {
            throw new BusinessException("投诉工单不存在");
        }
        if (!memberId.equals(complaint.getMemberId())) {
            throw new BusinessException(403, "无权对该投诉评分");
        }
        if (!"resolved".equals(complaint.getStatus())) {
            throw new BusinessException("仅已处理的投诉可评分");
        }
        if (complaint.getSatisfaction() != null && complaint.getSatisfaction() > 0) {
            throw new BusinessException("您已对该投诉评分，感谢反馈");
        }
        complaint.setSatisfaction(satisfaction);
        complaintMapper.updateById(complaint);
        log.info("投诉评分成功: id={}, memberId={}, satisfaction={}", id, memberId, satisfaction);
    }

    private ComplaintVO toVO(AfterSalesComplaint c, Map<String, String> statusNameMap) {
        ComplaintVO vo = new ComplaintVO();
        vo.setId(c.getId());
        vo.setTicketNo(c.getTicketNo());
        vo.setOrderNo(c.getOrderNo());
        vo.setCustomerName(c.getCustomerName());
        vo.setType(c.getType());
        vo.setTypeName(c.getTypeName());
        vo.setDescription(c.getDescription());
        vo.setImages(fromJsonImages(c.getImages()));
        vo.setPriority(c.getPriority());
        vo.setStatus(c.getStatus());
        vo.setStatusName(statusNameMap.getOrDefault(c.getStatus(), c.getStatus()));
        vo.setSolution(c.getSolution());
        vo.setAssignee(c.getAssignee());
        vo.setSatisfaction(c.getSatisfaction());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }

    /** 状态中文名映射（字典 complaint_status，启用状态） */
    private Map<String, String> statusNameMap() {
        return sysDictDataMapper.selectList(
                        new LambdaQueryWrapper<SysDictData>()
                                .eq(SysDictData::getDictType, DICT_COMPLAINT_STATUS)
                                .eq(SysDictData::getStatus, 1))
                .stream()
                .collect(Collectors.toMap(SysDictData::getDictValue, SysDictData::getDictLabel, (a, b) -> a));
    }

    /** 客户姓名：实名 > 昵称 > 手机号 */
    private String displayName(Member member) {
        if (StringUtils.hasText(member.getRealName())) return member.getRealName();
        if (StringUtils.hasText(member.getNickname())) return member.getNickname();
        if (StringUtils.hasText(member.getPhone())) return member.getPhone();
        return "匿名用户";
    }

    /** 工单号：TS + 年月日时分秒毫秒 + 3位随机，保证唯一 */
    private String generateTicketNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int random = ThreadLocalRandom.current().nextInt(1000);
        return "TS" + time + String.format("%03d", random);
    }

    private String toJson(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            throw new BusinessException("凭证图片数据异常");
        }
    }

    private List<String> fromJsonImages(String images) {
        if (!StringUtils.hasText(images)) return List.of();
        try {
            return objectMapper.readValue(images, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            // 容错：历史脏数据（如逗号分隔）直接按逗号拆分
            return List.of(images.split(","));
        }
    }
}
