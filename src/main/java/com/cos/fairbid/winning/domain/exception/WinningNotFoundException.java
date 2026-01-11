package com.cos.fairbid.winning.domain.exception;

/**
 * 낙찰 정보를 찾을 수 없을 때 발생하는 예외
 */
public class WinningNotFoundException extends RuntimeException {

    private WinningNotFoundException(String message) {
        super(message);
    }

    public static WinningNotFoundException withId(Long id) {
        return new WinningNotFoundException("낙찰 정보를 찾을 수 없습니다. ID: " + id);
    }

    public static WinningNotFoundException withAuctionId(Long auctionId) {
        return new WinningNotFoundException("해당 경매의 낙찰 정보를 찾을 수 없습니다. 경매 ID: " + auctionId);
    }
}
