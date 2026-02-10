package dev.jobscanner.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User profile configuration containing preferences, target technologies, and
 * scoring weights.
 */
@Data
public class UserProfile {
  private String name = "Default User";
  private List<String> targetTechnologies = new ArrayList<>();
  private String experienceLevel = "Senior";
  private List<String> seniorityTerms = new ArrayList<>();
  private List<String> locations = new ArrayList<>();
  private ScoringSettings scoring = new ScoringSettings();
  private Map<String, Integer> techStackWeights = new HashMap<>();

  @Data
  public static class ScoringSettings {
    private int threshold = 70;
    private Map<String, Integer> weights = new HashMap<>();
  }
}
