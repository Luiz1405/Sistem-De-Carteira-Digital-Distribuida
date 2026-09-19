package com.distributed.digital_wallet;

import org.springframework.boot.SpringApplication;

public class TestDigitalWalletApplication {

	public static void main(String[] args) {
		SpringApplication.from(DigitalWalletApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
