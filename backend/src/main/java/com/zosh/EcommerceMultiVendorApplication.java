package com.zosh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.zosh.campus", "com.zosh.config"})
public class EcommerceMultiVendorApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcommerceMultiVendorApplication.class, args);
	}

}
