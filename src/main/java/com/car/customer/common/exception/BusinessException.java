package com.car.customer.common.exception;

import com.car.customer.common.result.Result;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = Result.ERROR;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
