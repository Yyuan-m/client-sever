package com.car.customer.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.Car;
import com.car.customer.entity.Favorite;
import com.car.customer.entity.Member;
import com.car.customer.mapper.CarMapper;
import com.car.customer.mapper.FavoriteMapper;
import com.car.customer.mapper.MemberMapper;
import com.car.customer.module.auth.vo.MemberVO;
import com.car.customer.module.user.dto.ChangePasswordDTO;
import com.car.customer.module.user.dto.ProfileDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final MemberMapper memberMapper;
    private final FavoriteMapper favoriteMapper;
    private final CarMapper carMapper;
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
        return vo;
    }
}
