package com.car.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.car.customer.entity.MemberCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户优惠券 Mapper（操作 member_coupon 表，跨库 JOIN car_rental.coupon 模板）
 */
@Mapper
public interface MemberCouponMapper extends BaseMapper<MemberCoupon> {

    /**
     * 查询用户已领取的券（跨库 JOIN coupon 模板）
     */
    @Select("SELECT mc.*, " +
            "c.name AS coupon_name, c.type AS coupon_type, c.type_name AS coupon_type_name, " +
            "c.value AS coupon_value, c.min_amount AS min_amount, c.discount_cap AS discount_cap, " +
            "c.apply_scope AS apply_scope, c.stackable AS stackable, " +
            "c.valid_start_time AS valid_start_time, c.valid_end_time AS valid_end_time " +
            "FROM member_coupon mc " +
            "LEFT JOIN car_rental.coupon c ON mc.coupon_id = c.id AND c.is_delete = 0 " +
            "WHERE mc.member_id = #{memberId} AND mc.is_delete = 0 " +
            "ORDER BY mc.claim_time DESC")
    List<MemberCoupon> selectMyCoupons(@Param("memberId") Long memberId);

    /**
     * 查询某用户某券已领取数量（用于 per_user_limit 校验）
     */
    @Select("SELECT COUNT(*) FROM member_coupon " +
            "WHERE member_id = #{memberId} AND coupon_id = #{couponId} AND is_delete = 0")
    int countReceivedByUser(@Param("memberId") Long memberId, @Param("couponId") Long couponId);

    /**
     * 状态流转（带乐观锁版本号校验）
     * @return 影响行数，0 表示版本冲突或状态不符
     */
    @Update("UPDATE member_coupon SET status = #{newStatus}, version = version + 1 " +
            "WHERE id = #{id} AND status = #{expectStatus} AND version = #{version} AND is_delete = 0")
    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("expectStatus") String expectStatus,
                                @Param("newStatus") String newStatus,
                                @Param("version") Integer version);

    /**
     * 核销回写订单（带乐观锁）
     */
    @Update("UPDATE member_coupon " +
            "SET status = 'used', use_time = NOW(), order_id = #{orderId}, version = version + 1 " +
            "WHERE id = #{id} AND status = 'locked' AND version = #{version} AND is_delete = 0")
    int verifyWithOrder(@Param("id") Long id,
                        @Param("orderId") Long orderId,
                        @Param("version") Integer version);
}
