package com.car.customer.module.auth.vo;

import lombok.Data;

/**
 * token 刷新返回值
 */
@Data
public class RefreshTokenVO {

    /** 新的 access token */
    private String token;
    /** 新的 refresh token（轮换：旧 refresh 失效，下发新 refresh） */
    private String refreshToken;
}
