package com.zaalima.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VirtualThreadTaskHandlingApplication {

	public static void main(String[] args) {
		SpringApplication.run(VirtualThreadTaskHandlingApplication.class, args);
	}

}