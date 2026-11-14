package gg.pub.web.api;

import gg.pub.pubgapi.PlayerNotFoundException;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.pubgapi.PubgRateLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 이 패키지(web/api)의 JSON API 오류 → 상태 코드 + { code, message }.
 * 프론트(frontend/src/api/client.ts)는 code 로 안내를 나눈다.
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
class ApiExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> badRequest(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    ResponseEntity<ApiError> notFound(PlayerNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("PLAYER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PubgRateLimitException.class)
    ResponseEntity<ApiError> rateLimited(PubgRateLimitException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(PubgRateLimitException.RETRY_AFTER_SECONDS))
                .body(new ApiError("RATE_LIMITED", e.getMessage(), PubgRateLimitException.RETRY_AFTER_SECONDS));
    }

    /** 위에서 잡히지 않은 PUBG API 실패(키 문제, 연결 끊김 등) */
    @ExceptionHandler(PubgApiException.class)
    ResponseEntity<ApiError> upstream(PubgApiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError("UPSTREAM_ERROR", e.getMessage()));
    }
}
