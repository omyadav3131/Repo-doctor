package com.omyadav.repodoctor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class RepodoctorApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepodoctorApplication.class, args);
	}

}
