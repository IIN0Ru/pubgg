package gg.pub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 펍지지 (PUB.GG) 애플리케이션 시작점.
 * 이 main 메서드를 실행하면 내장 서버가 켜지고 http://localhost:8080 에서 사이트가 열린다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan   // @ConfigurationProperties 가 붙은 설정 클래스를 찾아서 등록
public class PubggApplication {

    public static void main(String[] args) {
        SpringApplication.run(PubggApplication.class, args);
    }
}
