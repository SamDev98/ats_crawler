package dev.jobscanner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;

/**
 * Configuration for loading the UserProfile from profile.json.
 */
@Slf4j
@Configuration
public class ProfileConfig {

  private static final String PROFILE_FILE = "profile.json";

  @Bean
  public UserProfile userProfile(ObjectMapper objectMapper) {
    File file = new File(PROFILE_FILE);
    if (!file.exists()) {
      log.warn("profile.json not found in root directory. Using default empty profile.");
      return new UserProfile();
    }

    try {
      UserProfile profile = objectMapper.readValue(file, UserProfile.class);
      log.info("Loaded user profile for: {}", profile.getName());
      return profile;
    } catch (IOException e) {
      log.error("Failed to load profile.json. Ensure it matches the required structure.", e);
      throw new IllegalStateException("Could not load user profile", e);
    }
  }
}
