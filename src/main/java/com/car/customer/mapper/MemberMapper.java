package com.car.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.car.customer.entity.Member;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface MemberMapper extends BaseMapper<Member> {

    /**
     * 重算并更新会员等级（跨库，订单完成时调用）。
     * 与后台管理服务 CustomerInfoMapper.recalcMemberLevel 保持同一套规则，双端口径一致：
     *   普通会员：无已完成订单且消费为 0
     *   银卡会员：0 < 订单数 <= 10 或 0 < 消费 <= 10000
     *   金卡会员：10 < 订单数 <= 50 或 10000 < 消费 <= 50000
     *   钻石会员：50 < 订单数 <= 500 或 50000 < 消费 <= 500000
     *   黑卡会员：订单数 > 500 或消费 > 500000
     * 统计口径：status='completed' 且 is_delete=0 的 customer_order，
     * 消费金额为 SUM(total_amount)（实付净额，已扣优惠券）。
     */
    @Update("""
            UPDATE member m
            LEFT JOIN (
                SELECT member_id,
                       COUNT(*)                            AS order_cnt,
                       COALESCE(SUM(total_amount), 0)      AS total_spent
                FROM car_rental.customer_order
                WHERE status = 'completed' AND is_delete = 0
                GROUP BY member_id
            ) s ON s.member_id = m.id
            SET m.level = CASE
                    WHEN COALESCE(s.order_cnt, 0) > 500 OR COALESCE(s.total_spent, 0) > 500000 THEN 'black'
                    WHEN (COALESCE(s.order_cnt, 0) > 50 AND COALESCE(s.order_cnt, 0) <= 500)
                      OR (COALESCE(s.total_spent, 0) > 50000 AND COALESCE(s.total_spent, 0) <= 500000) THEN 'diamond'
                    WHEN (COALESCE(s.order_cnt, 0) > 10 AND COALESCE(s.order_cnt, 0) <= 50)
                      OR (COALESCE(s.total_spent, 0) > 10000 AND COALESCE(s.total_spent, 0) <= 50000) THEN 'gold'
                    WHEN (COALESCE(s.order_cnt, 0) > 0 AND COALESCE(s.order_cnt, 0) <= 10)
                      OR (COALESCE(s.total_spent, 0) > 0 AND COALESCE(s.total_spent, 0) <= 10000) THEN 'silver'
                    ELSE 'normal'
                END,
                m.level_name = CASE
                    WHEN COALESCE(s.order_cnt, 0) > 500 OR COALESCE(s.total_spent, 0) > 500000 THEN '黑卡会员'
                    WHEN (COALESCE(s.order_cnt, 0) > 50 AND COALESCE(s.order_cnt, 0) <= 500)
                      OR (COALESCE(s.total_spent, 0) > 50000 AND COALESCE(s.total_spent, 0) <= 500000) THEN '钻石会员'
                    WHEN (COALESCE(s.order_cnt, 0) > 10 AND COALESCE(s.order_cnt, 0) <= 50)
                      OR (COALESCE(s.total_spent, 0) > 10000 AND COALESCE(s.total_spent, 0) <= 50000) THEN '金卡会员'
                    WHEN (COALESCE(s.order_cnt, 0) > 0 AND COALESCE(s.order_cnt, 0) <= 10)
                      OR (COALESCE(s.total_spent, 0) > 0 AND COALESCE(s.total_spent, 0) <= 10000) THEN '银卡会员'
                    ELSE '普通会员'
                END
            WHERE m.id = #{memberId}
            """)
    int recalcMemberLevel(@Param("memberId") Long memberId);
}
