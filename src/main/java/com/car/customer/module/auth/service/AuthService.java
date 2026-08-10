package com.car.customer.module.auth.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.util.JwtUtil;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.Member;
import com.car.customer.entity.SmsCode;
import com.car.customer.mapper.MemberMapper;
import com.car.customer.mapper.SmsCodeMapper;
import com.car.customer.module.auth.dto.ForgotPasswordDTO;
import com.car.customer.module.auth.dto.LoginDTO;
import com.car.customer.module.auth.dto.RegisterDTO;
import com.car.customer.module.auth.dto.SmsCodeDTO;
import com.car.customer.module.auth.vo.LoginVO;
import com.car.customer.module.auth.vo.MemberVO;
import com.car.customer.module.auth.vo.RefreshTokenVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberMapper memberMapper;
    private final SmsCodeMapper smsCodeMapper;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_TOKEN_BLACKLIST_PREFIX = "customer:token:blacklist:";
    /** Redis 中 refresh token 的存储前缀，key = refresh token 字符串，value = userId */
    private static final String REDIS_REFRESH_TOKEN_PREFIX = "customer:refresh_token:";

    @org.springframework.beans.factory.annotation.Value("${jwt.expiration}")
    private long jwtExpiration;

    @org.springframework.beans.factory.annotation.Value("${jwt.refresh-expiration}")
    private long jwtRefreshExpiration;

    public LoginVO login(LoginDTO dto) {
        Member member = memberMapper.selectOne(new LambdaQueryWrapper<Member>()
                .eq(Member::getUsername, dto.getUsername()));
        if (member == null) {
            throw new BusinessException("账号不存在");
        }
        if (member.getStatus() != null && member.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        if (!dto.getPassword().equals(member.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        // 记录登录时间
        member.setLastLoginTime(java.time.LocalDateTime.now());
        memberMapper.updateById(member);
        String accessToken = jwtUtil.generateAccessToken(member.getId(), member.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(member.getId(), member.getUsername());
        // refresh token 写入 Redis，TTL 与 token 有效期一致
        saveRefreshToken(refreshToken, member.getId());
        LoginVO vo = new LoginVO();
        vo.setToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setUser(toMemberVO(member));
        return vo;
    }

    /**
     * 刷新 access token：refresh token 轮换（旧的失效，下发新的 access + refresh）
     */
    public RefreshTokenVO refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("refresh token 不能为空");
        }
        // 1. JWT 结构校验 + 类型校验
        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException(401, "refresh token 无效或已过期");
        }
        // 2. Redis 存在性校验（登出/被吊销后即失效）
        String key = REDIS_REFRESH_TOKEN_PREFIX + refreshToken;
        Object userIdRaw = redisTemplate.opsForValue().get(key);
        if (userIdRaw == null) {
            throw new BusinessException(401, "refresh token 已失效，请重新登录");
        }
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        String username = jwtUtil.getUsernameFromToken(refreshToken);
        // 3. 轮换：删除旧 refresh，签发新的 access + refresh
        redisTemplate.delete(key);
        String newAccessToken = jwtUtil.generateAccessToken(userId, username);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);
        saveRefreshToken(newRefreshToken, userId);
        RefreshTokenVO vo = new RefreshTokenVO();
        vo.setToken(newAccessToken);
        vo.setRefreshToken(newRefreshToken);
        return vo;
    }

    /** 将 refresh token 存入 Redis（key=token，value=userId） */
    private void saveRefreshToken(String refreshToken, Long userId) {
        redisTemplate.opsForValue().set(
                REDIS_REFRESH_TOKEN_PREFIX + refreshToken,
                userId,
                java.time.Duration.ofMillis(jwtRefreshExpiration));
    }

    @Transactional
    public Long register(RegisterDTO dto) {
        // 校验用户名是否已注册
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<Member>()
                .eq(Member::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        Member member = new Member();
        member.setUsername(dto.getUsername());
        member.setPassword(dto.getPassword());
        member.setPhone(dto.getPhone());
        // 昵称：优先使用传入昵称，否则用用户名
        member.setNickname(dto.getNickname() != null && !dto.getNickname().isBlank()
                ? dto.getNickname() : "尊享客户" + dto.getUsername());
        member.setGender(0);
        member.setLevel("normal");
        member.setLevelName("普通会员");
        member.setCreditScore(100);
        member.setTotalOrders(0);
        member.setTotalSpent(java.math.BigDecimal.ZERO);
        member.setStatus(1);
        memberMapper.insert(member);
        return member.getId();
    }

    public void sendSmsCode(SmsCodeDTO dto) {
        // 生成 6 位验证码
        String code = RandomUtil.randomNumbers(6);
        // 存入数据库（5 分钟有效）
        SmsCode smsCode = new SmsCode();
        smsCode.setPhone(dto.getPhone());
        smsCode.setCode(code);
        smsCode.setExpireTime(LocalDateTime.now().plusMinutes(5));
        smsCode.setUsed(0);
        smsCodeMapper.insert(smsCode);
        // 实际项目此处应调用短信服务商；开发期仅打印日志
        log.info("【LUXURY CAR】手机号 {} 的验证码: {}", dto.getPhone(), code);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordDTO dto) {
        verifySmsCode(dto.getPhone(), dto.getCode());
        Member member = memberMapper.selectOne(new LambdaQueryWrapper<Member>()
                .eq(Member::getPhone, dto.getPhone()));
        if (member == null) {
            throw new BusinessException("账号不存在");
        }
        member.setPassword(dto.getPassword());
        memberMapper.updateById(member);
    }

    public MemberVO getCurrentUserInfo() {
        Long memberId = SecurityUtil.getCurrentMemberId();
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException("用户不存在");
        }
        return toMemberVO(member);
    }

    /**
     * 登出：access token 加黑名单，refresh token 从 Redis 删除
     */
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            accessToken = accessToken.substring(7);
        }
        if (accessToken != null) {
            // 将 access token 加入黑名单，有效期与 jwt 过期时间一致，过期后自动从 Redis 清除
            redisTemplate.opsForValue().set(REDIS_TOKEN_BLACKLIST_PREFIX + accessToken, "1",
                    java.time.Duration.ofMillis(jwtExpiration));
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            // 删除 refresh token，使其无法再用于刷新
            redisTemplate.delete(REDIS_REFRESH_TOKEN_PREFIX + refreshToken);
        }
    }

    // ---------- 私有方法 ----------

    private void verifySmsCode(String phone, String code) {
        SmsCode smsCode = smsCodeMapper.selectOne(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getCode, code)
                .eq(SmsCode::getUsed, 0)
                .orderByDesc(SmsCode::getId)
                .last("LIMIT 1"));
        if (smsCode == null) {
            throw new BusinessException("验证码错误");
        }
        if (smsCode.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("验证码已过期");
        }
        smsCode.setUsed(1);
        smsCodeMapper.updateById(smsCode);
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
        vo.setDriverLicenseNo(member.getDriverLicenseNo());
        vo.setDriverLicenseType(member.getDriverLicenseType());
        vo.setDriverLicenseExpireDate(member.getDriverLicenseExpireDate());
        vo.setProvince(member.getProvince());
        vo.setCity(member.getCity());
        vo.setAddress(member.getAddress());
        vo.setLastLoginTime(member.getLastLoginTime());
        return vo;
    }
}
