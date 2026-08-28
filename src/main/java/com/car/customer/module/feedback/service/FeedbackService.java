package com.car.customer.module.feedback.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.common.result.PageResult;
import com.car.customer.entity.Feedback;
import com.car.customer.mapper.FeedbackMapper;
import com.car.customer.module.feedback.dto.FeedbackDTO;
import com.car.customer.module.feedback.vo.AppointmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;

    /** 预约状态集合（与后台管理系统对齐：pending 待处理 / handled 已处理 + 用户自主取消 cancelled） */
    private static final Set<String> APPOINTMENT_STATUS = Set.of("pending", "handled", "cancelled");

    /** 用户可自行取消的状态：仅待处理（后台处理完成后为终态，不可取消） */
    private static final Set<String> CANCELLABLE_STATUS = Set.of("pending");

    private static final String TYPE_APPOINTMENT = "appointment";

    public void submit(FeedbackDTO dto) {
        Feedback fb = new Feedback();
        fb.setType(dto.getType() != null ? dto.getType() : "feedback");
        // 登录态提交时自动绑定会员，供"我的预约"查询（未登录留资不绑定）
        if (SecurityUtil.isLogged()) {
            fb.setMemberId(SecurityUtil.getCurrentMemberId());
        }
        fb.setName(dto.getName());
        fb.setPhone(dto.getPhone());
        fb.setContent(dto.getContent());
        fb.setCarType(dto.getCarType());
        if (dto.getRentDate() != null && !dto.getRentDate().isBlank()) {
            fb.setRentDate(LocalDate.parse(dto.getRentDate()));
        }
        fb.setStatus("pending");
        feedbackMapper.insert(fb);
        log.info("收到用户反馈: type={}, name={}, phone={}, memberId={}",
                fb.getType(), fb.getName(), fb.getPhone(), fb.getMemberId());
    }

    /**
     * 我的预约列表（分页 + 状态筛选）
     * 仅返回当前登录会员提交的 type=appointment 记录，按提交时间倒序
     */
    public PageResult<AppointmentVO> getMyAppointments(int page, int pageSize, String status) {
        Long memberId = SecurityUtil.getCurrentMemberId();

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getMemberId, memberId)
                .eq(Feedback::getType, TYPE_APPOINTMENT)
                .eq(StringUtils.hasText(status) && APPOINTMENT_STATUS.contains(status),
                        Feedback::getStatus, status)
                .orderByDesc(Feedback::getCreateTime);

        Page<Feedback> result = feedbackMapper.selectPage(new Page<>(page, Math.min(pageSize, 50)), wrapper);
        List<AppointmentVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, result.getTotal(), page, Math.min(pageSize, 50));
    }

    /**
     * 取消预约：仅本人 + 待处理状态可取消
     */
    public void cancelAppointment(Long id) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        Feedback fb = feedbackMapper.selectById(id);
        if (fb == null || !memberId.equals(fb.getMemberId()) || !TYPE_APPOINTMENT.equals(fb.getType())) {
            throw new BusinessException("预约记录不存在");
        }
        if (!CANCELLABLE_STATUS.contains(fb.getStatus())) {
            throw new BusinessException("该预约已由客服处理完成，不可取消（如有疑问请联系客服）");
        }
        fb.setStatus("cancelled");
        // 用户取消轨迹（后台管理员可见），不覆盖后台已有的处理备注
        String userMark = "用户于 " + LocalDate.now() + " 自主取消预约";
        fb.setRemark(StringUtils.hasText(fb.getRemark()) ? fb.getRemark() + "；" + userMark : userMark);
        fb.setProcessTime(LocalDateTime.now());
        feedbackMapper.updateById(fb);
        log.info("用户取消预约: id={}, memberId={}", id, memberId);
    }

    private AppointmentVO toVO(Feedback fb) {
        AppointmentVO vo = new AppointmentVO();
        vo.setId(fb.getId());
        vo.setCarType(fb.getCarType());
        vo.setRentDate(fb.getRentDate());
        vo.setName(maskName(fb.getName()));
        vo.setPhone(maskPhone(fb.getPhone()));
        vo.setContent(fb.getContent());
        vo.setStatus(fb.getStatus());
        vo.setStatusName(statusName(fb.getStatus()));
        vo.setRemark(fb.getRemark());
        vo.setHandler(fb.getHandler());
        vo.setCreateTime(fb.getCreateTime());
        vo.setProcessTime(fb.getProcessTime());
        vo.setCancellable(CANCELLABLE_STATUS.contains(fb.getStatus()));
        return vo;
    }

    private String statusName(String status) {
        return switch (status == null ? "" : status) {
            case "pending" -> "待处理";
            case "handled" -> "已处理";
            case "cancelled" -> "已取消";
            default -> status;
        };
    }

    /** 姓名脱敏：保留姓氏，如"张*" */
    private String maskName(String name) {
        if (name == null || name.isBlank()) return "";
        if (name.length() == 1) return name;
        return name.charAt(0) + "*".repeat(Math.min(name.length() - 1, 2));
    }

    /** 手机号脱敏：保留前3后4，如"138****5678" */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone == null ? "" : phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
