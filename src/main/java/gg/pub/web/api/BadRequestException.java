package gg.pub.web.api;

/** 주소 값이 이상할 때 (모르는 플랫폼, 빈 닉네임). ApiExceptionHandler 가 400 BAD_REQUEST 로 바꾼다 */
class BadRequestException extends RuntimeException {
    BadRequestException(String message) {
        super(message);
    }
}
