package com.gb.sched.service;

import org.springframework.http.HttpStatus;

/** 业务规则异常：携带 HTTP 状态码，由全局异常处理统一返回。 */
public class BusinessException extends RuntimeException {
  private final HttpStatus status;

  public BusinessException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
