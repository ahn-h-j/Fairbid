package com.cos.fairbid.common.exception;

import com.cos.fairbid.auction.domain.exception.InvalidAuctionException;
import com.cos.fairbid.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 공통 형식으로 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 경매 도메인 검증 예외 처리
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(InvalidAuctionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAuctionException(InvalidAuctionException e) {
        log.warn("InvalidAuctionException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 그 외 예상치 못한 예외 처리
     * HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error occurred", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
