package com.socialmedia.social_media_chat_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SocialMediaChatServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocialMediaChatServiceApplication.class, args);
	}
	
}
