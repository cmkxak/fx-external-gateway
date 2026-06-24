package com.hectofinancial.fxgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 해외거래망 전용 GW 서버.
 * 여러 해외송금 네트워크(Thunes 외 추후 N개)를 붙이는 아웃바운드 게이트웨이.
 */
@SpringBootApplication
public class FxGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FxGatewayApplication.class, args);
    }
}
