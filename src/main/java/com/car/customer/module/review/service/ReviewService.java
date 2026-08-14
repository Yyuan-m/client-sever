package com.car.customer.module.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.Member;
import com.car.customer.entity.RentalOrder;
import com.car.customer.entity.Review;
import com.car.customer.mapper.MemberMapper;
import com.car.customer.mapper.RentalOrderMapper;
import com.car.customer.mapper.ReviewMapper;
import com.car.customer.module.order.service.OrderService;
import com.car.customer.module.review.dto.ReviewSubmitDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 评价服务
 * 评价模型：首评（reviewRound=1）+ 追评（reviewRound=2），每单最多2次
 * 状态联动：订单完成时 reviewStatus=unreviewed → 首评后 reviewed → 追评后 final_reviewed
 * 首页"客户真实评价"展示读取同一张表，name/avatar/carName 为提交时冗余快照
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final RentalOrderMapper orderMapper;
    private final MemberMapper memberMapper;
    private final OrderService orderService;

    /**
     * 提交评价（首评或追评，由订单 reviewStatus 决定轮次）
     * @return 保存后的评价（含 id、reviewRound）
     */
    @Transactional
    public Review submitReview(ReviewSubmitDTO dto) {
        Long memberId = SecurityUtil.getCurrentMemberId();

        RentalOrder order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权评价该订单");
        }
        if (!"completed".equals(order.getStatus())) {
            throw new BusinessException("仅已完成订单可评价");
        }

        // 依据订单 reviewStatus 决定本轮评价类型
        // 容错：历史已完成订单（迁移前未回填 reviewStatus）的 null 值视为 unreviewed，允许首评
        String reviewStatus = order.getReviewStatus();
        if (reviewStatus == null) {
            reviewStatus = "unreviewed";
        }
        int round;
        String nextReviewStatus;
        String nextReviewStatusName;
        if ("unreviewed".equals(reviewStatus)) {
            // 首评
            round = 1;
            nextReviewStatus = "reviewed";
            nextReviewStatusName = "已评价";
        } else if ("reviewed".equals(reviewStatus)) {
            // 追评
            round = 2;
            nextReviewStatus = "final_reviewed";
            nextReviewStatusName = "已追评";
        } else {
            // final_reviewed
            throw new BusinessException("该订单评价已完成，无法再次评价");
        }

        // 二次校验：防止并发提交导致同一轮次重复（基于 order_id + review_round 唯一性）
        Long existCount = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, order.getId())
                .eq(Review::getReviewRound, round));
        if (existCount != null && existCount > 0) {
            throw new BusinessException("该订单已提交过" + (round == 1 ? "首评" : "追评") + "，请勿重复提交");
        }

        // 取会员信息填充冗余字段（首页展示用，避免 JOIN）
        Member member = memberMapper.selectById(memberId);
        String name = member != null && member.getNickname() != null ? member.getNickname() : "匿名用户";
        String avatar = member != null ? member.getAvatar() : null;

        Review review = new Review();
        review.setMemberId(memberId);
        review.setOrderId(order.getId());
        review.setCarId(order.getCarId());
        review.setReviewRound(round);
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        review.setImages(dto.getImages());
        review.setName(name);
        review.setAvatar(avatar);
        review.setCarName(order.getCarName());
        review.setDate(LocalDate.now());
        reviewMapper.insert(review);

        // 同步订单评价状态
        order.setReviewStatus(nextReviewStatus);
        order.setReviewStatusName(nextReviewStatusName);
        orderMapper.updateById(order);

        log.info("评价提交成功: orderId={}, round={}, reviewId={}", order.getId(), round, review.getId());
        return review;
    }

    /**
     * 查询订单的评价列表（按轮次升序，首评在前追评在后）
     */
    public List<Review> getOrderReviews(Long orderId) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        RentalOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权查看该订单评价");
        }
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId)
                .orderByAsc(Review::getReviewRound));
    }

    /**
     * 查询订单可评价的轮次（前端按钮展示用）
     * 返回 null=不可评价，1=可首评，2=可追评
     * 容错：历史已完成订单（迁移前未回填 reviewStatus）的 null 值视为 unreviewed，返回 1
     */
    public Integer getCanReviewRound(Long orderId) {
        orderService.getOrderDetail(orderId); // 复用权限校验 + 自动完成触发
        RentalOrder order = orderMapper.selectById(orderId);
        if (order == null || !"completed".equals(order.getStatus())) {
            return null;
        }
        String rs = order.getReviewStatus();
        if (rs == null || "unreviewed".equals(rs)) return 1;
        if ("reviewed".equals(rs)) return 2;
        return null;
    }
}
