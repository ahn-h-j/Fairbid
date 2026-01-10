package com.cos.fairbid.common.exception;


import com.cos.fairbid.auction.domain.AuctionDuration;
import com.cos.fairbid.auction.domain.Category;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.auction.domain.exception.InvalidAuctionException;
import com.cos.fairbid.bid.domain.BidType;
import com.cos.fairbid.bid.domain.exception.AuctionEndedException;
import com.cos.fairbid.bid.domain.exception.BidTooLowException;
import com.cos.fairbid.bid.domain.exception.InvalidBidException;
import com.cos.fairbid.bid.domain.exception.SelfBidNotAllowedException;
import com.cos.fairbid.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 공통 형식으로 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * @Valid 검증 실패 예외 처리
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e
    ) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * Bean Validation 제약조건 위반 예외 처리
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException e
    ) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * JSON 파싱 실패 예외 처리 (잘못된 enum 값 등)
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e
    ) {
        String message = "요청 본문을 파싱할 수 없습니다.";

        // enum 변환 실패 시 유효한 값 안내
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            String causeMessage = cause.getMessage();

            if (causeMessage.contains("Category")) {
                String validValues = Arrays.stream(Category.values())
                        .map(Enum::name)
                        .collect(Collectors.joining(", "));
                message = "유효하지 않은 카테고리입니다. 허용 값: " + validValues;
            } else if (causeMessage.contains("AuctionDuration")) {
                String validValues = Arrays.stream(AuctionDuration.values())
                        .map(Enum::name)
                        .collect(Collectors.joining(", "));
                message = "유효하지 않은 경매 기간입니다. 허용 값: " + validValues;
            } else if (causeMessage.contains("BidType")) {
                String validValues = Arrays.stream(BidType.values())
                        .map(Enum::name)
                        .collect(Collectors.joining(", "));
                message = "유효하지 않은 입찰 유형입니다. 허용 값: " + validValues;
            }
        }

        log.warn("HttpMessageNotReadableException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST_BODY", message));
    }

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
     * 경매를 찾을 수 없을 때 예외 처리
     * HTTP 404 Not Found
     */
    @ExceptionHandler(AuctionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuctionNotFoundException(AuctionNotFoundException e) {
        log.warn("AuctionNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    // =====================================================
    // 입찰 관련 예외 처리
    // =====================================================

    /**
     * 경매가 종료된 경우 예외 처리
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(AuctionEndedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuctionEndedException(AuctionEndedException e) {
        log.warn("AuctionEndedException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 입찰가가 최소 입찰 금액보다 낮은 경우 예외 처리
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(BidTooLowException.class)
    public ResponseEntity<ApiResponse<Void>> handleBidTooLowException(BidTooLowException e) {
        log.warn("BidTooLowException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 입찰 요청이 유효하지 않은 경우 예외 처리
     * HTTP 400 Bad Request
     */
    @ExceptionHandler(InvalidBidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBidException(InvalidBidException e) {
        log.warn("InvalidBidException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 본인 경매에 입찰 시도 시 예외 처리
     * HTTP 403 Forbidden
     */
    @ExceptionHandler(SelfBidNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSelfBidNotAllowedException(SelfBidNotAllowedException e) {
        log.warn("SelfBidNotAllowedException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
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
