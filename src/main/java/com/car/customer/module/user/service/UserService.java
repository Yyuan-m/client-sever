package com.car.customer.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.Car;
import com.car.customer.entity.Favorite;
import com.car.customer.entity.Member;
import com.car.customer.entity.MemberVerifyRecord;
import com.car.customer.entity.RentalOrder;
import com.car.customer.mapper.CarMapper;
import com.car.customer.mapper.FavoriteMapper;
import com.car.customer.mapper.MemberMapper;
import com.car.customer.mapper.MemberVerifyRecordMapper;
import com.car.customer.mapper.RentalOrderMapper;
import com.car.customer.module.auth.vo.MemberVO;
import com.car.customer.module.user.dto.ChangePasswordDTO;
import com.car.customer.module.user.dto.ProfileDTO;
import com.car.customer.module.user.dto.VerifySubmitDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final MemberMapper memberMapper;
    private final MemberVerifyRecordMapper memberVerifyRecordMapper;
    private final FavoriteMapper favoriteMapper;
    private final CarMapper carMapper;
    private final RentalOrderMapper rentalOrderMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public MemberVO updateProfile(ProfileDTO dto) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException("用户不存在");
        }
        // 基础资料
        if (dto.getAvatar() != null) member.setAvatar(dto.getAvatar());
        if (dto.getNickname() != null) member.setNickname(dto.getNickname());
        if (dto.getPhone() != null) member.setPhone(dto.getPhone());
        if (dto.getEmail() != null) member.setEmail(dto.getEmail());
        // 实名认证
        checkVerifyEditable(member);
        if (dto.getRealName() != null) member.setRealName(dto.getRealName());
        if (dto.getGender() != null) member.setGender(dto.getGender());
        if (dto.getBirthday() != null) member.setBirthday(dto.getBirthday());
        if (dto.getIdCard() != null) member.setIdCard(dto.getIdCard());
        if (dto.getIdCardFrontImg() != null) member.setIdCardFrontImg(dto.getIdCardFrontImg());
        if (dto.getIdCardBackImg() != null) member.setIdCardBackImg(dto.getIdCardBackImg());
        // 驾驶证
        if (dto.getDriverLicenseNo() != null) member.setDriverLicenseNo(dto.getDriverLicenseNo());
        if (dto.getDriverLicenseType() != null) member.setDriverLicenseType(dto.getDriverLicenseType());
        if (dto.getDriverLicenseFrontImg() != null) member.setDriverLicenseFrontImg(dto.getDriverLicenseFrontImg());
        if (dto.getDriverLicenseBackImg() != null) member.setDriverLicenseBackImg(dto.getDriverLicenseBackImg());
        if (dto.getDriverLicenseExpireDate() != null) member.setDriverLicenseExpireDate(dto.getDriverLicenseExpireDate());
        // 地址
        if (dto.getProvince() != null) member.setProvince(dto.getProvince());
        if (dto.getCity() != null) member.setCity(dto.getCity());
        if (dto.getAddress() != null) member.setAddress(dto.getAddress());
        memberMapper.updateById(member);
        return toMemberVO(member);
    }

    /**
     * 提交实名认证：写入认证字段并生成一条待审核记录，等待后台人工审核。
     * 提交后 member.verify_status 置为 pending，期间不可再修改认证信息。
     */
    @Transactional
    public MemberVO submitVerify(VerifySubmitDTO dto) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException("用户不存在");
        }
        if ("pending".equals(member.getVerifyStatus())) {
            throw new BusinessException("认证资料审核中，请耐心等待，期间不可重复提交");
        }
        if (dto.getRealName() == null || dto.getRealName().isBlank()
                || dto.getIdCard() == null || dto.getIdCard().isBlank()
                || dto.getDriverLicenseNo() == null || dto.getDriverLicenseNo().isBlank()) {
            throw new BusinessException("真实姓名、身份证号、驾驶证号为必填项");
        }
        if (dto.getIdCardFrontImg() == null || dto.getIdCardBackImg() == null
                || dto.getDriverLicenseFrontImg() == null || dto.getDriverLicenseBackImg() == null) {
            throw new BusinessException("请上传完整的证件照片（身份证正反面、驾驶证正副页）");
        }

        // 1. 更新 member 表认证字段 + 状态
        member.setRealName(dto.getRealName().trim());
        member.setGender(dto.getGender());
        member.setIdCard(dto.getIdCard().trim());
        member.setBirthday(dto.getBirthDate());
        member.setDriverLicenseNo(dto.getDriverLicenseNo().trim());
        member.setDriverLicenseType(dto.getDriverLicenseType());
        member.setDriverLicenseExpireDate(dto.getDriverLicenseExpireDate());
        member.setIdCardFrontImg(dto.getIdCardFrontImg());
        member.setIdCardBackImg(dto.getIdCardBackImg());
        member.setDriverLicenseFrontImg(dto.getDriverLicenseFrontImg());
        member.setDriverLicenseBackImg(dto.getDriverLicenseBackImg());
        member.setVerifyStatus("pending");
        member.setVerifyRejectReason(null);
        member.setVerifySubmitTime(LocalDateTime.now());
        memberMapper.updateById(member);

        // 2. 生成待审核记录
        MemberVerifyRecord record = new MemberVerifyRecord();
        record.setMemberId(memberId);
        record.setRealName(member.getRealName());
        record.setIdCard(member.getIdCard());
        record.setBirthDate(dto.getBirthDate());
        record.setDriverLicenseNo(member.getDriverLicenseNo());
        record.setDriverLicenseType(dto.getDriverLicenseType());
        record.setDriverLicenseExpireDate(dto.getDriverLicenseExpireDate());
        record.setIdCardFrontImg(dto.getIdCardFrontImg());
        record.setIdCardBackImg(dto.getIdCardBackImg());
        record.setDriverLicenseFrontImg(dto.getDriverLicenseFrontImg());
        record.setDriverLicenseBackImg(dto.getDriverLicenseBackImg());
        record.setStatus("pending");
        memberVerifyRecordMapper.insert(record);

        return toMemberVO(member);
    }

    /** 审核中/已认证时禁止通过普通资料接口修改认证相关字段 */
    private void checkVerifyEditable(Member member) {
        if ("pending".equals(member.getVerifyStatus())) {
            throw new BusinessException("认证资料审核中，暂不能修改认证信息");
        }
    }

    public void changePassword(ChangePasswordDTO dto) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException("用户不存在");
        }
        if (!dto.getOldPassword().equals(member.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        member.setPassword(dto.getNewPassword());
        memberMapper.updateById(member);
    }

    public List<Car> getFavorites() {
        Long memberId = SecurityUtil.getCurrentMemberId();
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getMemberId, memberId)
                .orderByDesc(Favorite::getCreateTime));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> carIds = favorites.stream().map(Favorite::getCarId).toList();
        List<Car> cars = carMapper.selectBatchIds(carIds);
        cars.forEach(this::parseTags);
        return cars;
    }

    @Transactional
    public void toggleFavorite(Long carId, String action) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        // 校验车辆存在
        Car car = carMapper.selectById(carId);
        if (car == null) {
            throw new BusinessException("车辆不存在");
        }

        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getMemberId, memberId)
                .eq(Favorite::getCarId, carId));

        if ("remove".equals(action)) {
            if (existing != null) {
                favoriteMapper.deleteById(existing.getId());
            }
        } else {
            // 添加收藏（已存在则跳过）
            if (existing == null) {
                Favorite fav = new Favorite();
                fav.setMemberId(memberId);
                fav.setCarId(carId);
                favoriteMapper.insert(fav);
            }
        }
    }

    private void parseTags(Car car) {
        if (car.getTags() == null || car.getTags().isBlank()) {
            car.setTagList(Collections.emptyList());
            return;
        }
        try {
            car.setTagList(objectMapper.readValue(car.getTags(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            car.setTagList(Collections.emptyList());
        }
    }

    private MemberVO toMemberVO(Member member) {
        MemberVO vo = new MemberVO();
        vo.setId(member.getId());
        vo.setUsername(member.getUsername());
        vo.setNickname(member.getNickname());
        vo.setPhone(member.getPhone());
        vo.setEmail(member.getEmail());
        vo.setAvatar(member.getAvatar());
        vo.setLevel(member.getLevel());
        vo.setLevelName(member.getLevelName());
        vo.setCreditScore(member.getCreditScore());
        vo.setTotalOrders(member.getTotalOrders());
        vo.setTotalSpent(member.getTotalSpent());
        // 租车订单数 & 累计消费：实时从订单表统计（覆盖 member 表冗余值）
        fillOrderStats(member.getId(), vo);
        vo.setRealName(member.getRealName());
        vo.setGender(member.getGender());
        vo.setBirthday(member.getBirthday());
        vo.setIdCard(member.getIdCard());
        vo.setIdCardFrontImg(member.getIdCardFrontImg());
        vo.setIdCardBackImg(member.getIdCardBackImg());
        vo.setDriverLicenseNo(member.getDriverLicenseNo());
        vo.setDriverLicenseType(member.getDriverLicenseType());
        vo.setDriverLicenseFrontImg(member.getDriverLicenseFrontImg());
        vo.setDriverLicenseBackImg(member.getDriverLicenseBackImg());
        vo.setDriverLicenseExpireDate(member.getDriverLicenseExpireDate());
        vo.setProvince(member.getProvince());
        vo.setCity(member.getCity());
        vo.setAddress(member.getAddress());
        vo.setLastLoginTime(member.getLastLoginTime());
        vo.setVerifyStatus(member.getVerifyStatus() != null ? member.getVerifyStatus() : "unverified");
        vo.setVerifyRejectReason(member.getVerifyRejectReason());
        vo.setVerifySubmitTime(member.getVerifySubmitTime());
        return vo;
    }

    /**
     * 从订单表实时统计会员的租车订单数 & 累计消费：
     * - 租车订单数：该会员全部订单数（含待支付/租赁中/已完成/已取消，不含逻辑删除）
     * - 累计消费：仅 status=completed（已完成）订单的 totalAmount 之和
     */
    private void fillOrderStats(Long memberId, MemberVO vo) {
        Long orderCount = rentalOrderMapper.selectCount(new LambdaQueryWrapper<RentalOrder>()
                .eq(RentalOrder::getMemberId, memberId));
        vo.setTotalOrders(orderCount == null ? 0 : orderCount.intValue());

        List<RentalOrder> completedOrders = rentalOrderMapper.selectList(new LambdaQueryWrapper<RentalOrder>()
                .eq(RentalOrder::getMemberId, memberId)
                .eq(RentalOrder::getStatus, "completed"));
        BigDecimal totalSpent = completedOrders.stream()
                .map(RentalOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalSpent(totalSpent);
    }
}
