package com.car.customer.module.auth.vo;

import lombok.Data;

@Data
public class LoginVO {

    /** 短效 access token，用于业务接口鉴权 */
    private String token;
    /** 长效 refresh token，用于无感刷新 access token */
    private String refreshToken;
    private MemberVO user;
}
