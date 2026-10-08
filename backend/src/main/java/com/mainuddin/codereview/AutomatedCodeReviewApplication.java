package com.mainuddin.codereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutomatedCodeReviewApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutomatedCodeReviewApplication.class, args);
	}

}
