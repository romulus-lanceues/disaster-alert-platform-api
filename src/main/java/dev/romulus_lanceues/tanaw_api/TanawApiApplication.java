package dev.romulus_lanceues.tanaw_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TanawApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TanawApiApplication.class, args);
	}

}
