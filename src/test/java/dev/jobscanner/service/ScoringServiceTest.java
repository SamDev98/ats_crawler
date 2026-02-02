package dev.jobscanner.service;

import dev.jobscanner.config.ScoringConfig;
import dev.jobscanner.model.Job;
import dev.jobscanner.service.RulesService.EligibilityResult;
import dev.jobscanner.service.ScoringService.ScoringResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {

    private ScoringService scoringService;
    private ScoringConfig scoringConfig;

    @BeforeEach
    void setUp() {
        scoringConfig = new ScoringConfig();
        scoringConfig.setThreshold(70);
        scoringConfig.setJavaInTitle(20);
        scoringConfig.setSeniorLevel(10);
        scoringConfig.setRemoteExplicit(15);
        scoringConfig.setNoUsOnly(20);
        scoringConfig.setContractB2b(15);
        scoringConfig.setLatamBrazilBoost(10);
        scoringConfig.setTechStack(20);
        scoringConfig.setSeniorityTerms(List.of("senior", "lead", "staff", "principal"));
        scoringConfig.setTechStackTerms(Map.of(
                "spring", 5,
                "kafka", 5,
                "aws", 5,
                "kubernetes", 5,
                "docker", 3
        ));
        scoringService = new ScoringService(scoringConfig);
    }

    private Job createJob(String title, String description, String location) {
        return Job.builder()
                .id("test-123")
                .title(title)
                .description(description)
                .url("https://example.com/job")
                .company("Test Company")
                .location(location)
                .source("Test")
                .discoveredAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Java in title scoring")
    class JavaInTitleTests {

        @Test
        @DisplayName("Should add points for Java in title")
        void shouldAddPointsForJavaInTitle() {
            Job job = createJob("Java Developer", "Building microservices.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("java_in_title", 20);
        }

        @Test
        @DisplayName("Should not add points for Java not in title")
        void shouldNotAddPointsWithoutJavaInTitle() {
            Job job = createJob("Backend Developer", "Using Java and Spring.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).doesNotContainKey("java_in_title");
        }
    }

    @Nested
    @DisplayName("Seniority level scoring")
    class SeniorityLevelTests {

        @Test
        @DisplayName("Should add points for Senior in title")
        void shouldAddPointsForSeniorInTitle() {
            Job job = createJob("Senior Java Developer", "Building microservices.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("senior_level", 10);
        }

        @Test
        @DisplayName("Should add points for Lead in title")
        void shouldAddPointsForLeadInTitle() {
            Job job = createJob("Lead Java Developer", "Leading the team.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("senior_level", 10);
        }

        @Test
        @DisplayName("Should add points for Staff in title")
        void shouldAddPointsForStaffInTitle() {
            Job job = createJob("Staff Java Engineer", "Technical leadership.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("senior_level", 10);
        }

        @Test
        @DisplayName("Should not add points without seniority term")
        void shouldNotAddPointsWithoutSeniorityTerm() {
            Job job = createJob("Java Developer", "Building microservices.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).doesNotContainKey("senior_level");
        }
    }

    @Nested
    @DisplayName("Remote explicit scoring")
    class RemoteExplicitTests {

        @Test
        @DisplayName("Should add points for remote explicit")
        void shouldAddPointsForRemoteExplicit() {
            Job job = createJob("Java Developer", "Building microservices.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(true, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("remote_explicit", 15);
        }

        @Test
        @DisplayName("Should not add points without remote")
        void shouldNotAddPointsWithoutRemote() {
            Job job = createJob("Java Developer", "Building microservices.", "San Francisco");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).doesNotContainKey("remote_explicit");
        }
    }

    @Nested
    @DisplayName("Contract/B2B scoring")
    class ContractB2BTests {

        @Test
        @DisplayName("Should add points for contract")
        void shouldAddPointsForContract() {
            Job job = createJob("Java Developer", "Building microservices.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, true, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("contract_b2b", 15);
        }

        @Test
        @DisplayName("Should not add points without contract")
        void shouldNotAddPointsWithoutContract() {
            Job job = createJob("Java Developer", "Building microservices.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).doesNotContainKey("contract_b2b");
        }
    }

    @Nested
    @DisplayName("LATAM/Brazil boost scoring")
    class LatamBrazilBoostTests {

        @Test
        @DisplayName("Should add points for Brazil location")
        void shouldAddPointsForBrazilLocation() {
            Job job = createJob("Java Developer", "Building microservices.", "Brazil");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("latam_brazil_boost", 10);
        }

        @Test
        @DisplayName("Should add points for LATAM in description")
        void shouldAddPointsForLatamInDescription() {
            Job job = createJob("Java Developer", "We hire from LATAM region.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("latam_brazil_boost", 10);
        }

        @Test
        @DisplayName("Should add points for São Paulo location")
        void shouldAddPointsForSaoPauloLocation() {
            Job job = createJob("Java Developer", "Building microservices.", "São Paulo");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("latam_brazil_boost", 10);
        }

        @Test
        @DisplayName("Should not add points for US location")
        void shouldNotAddPointsForUsLocation() {
            Job job = createJob("Java Developer", "Building microservices.", "New York, US");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).doesNotContainKey("latam_brazil_boost");
        }
    }

    @Nested
    @DisplayName("Tech stack scoring")
    class TechStackTests {

        @Test
        @DisplayName("Should add points for Spring")
        void shouldAddPointsForSpring() {
            Job job = createJob("Java Developer", "Experience with Spring Boot required.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("tech_stack", 5);
        }

        @Test
        @DisplayName("Should add points for multiple tech stack items")
        void shouldAddPointsForMultipleTechStackItems() {
            Job job = createJob("Java Developer", "Spring, Kafka, AWS, Docker experience.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("tech_stack", 18); // 5+5+5+3
        }

        @Test
        @DisplayName("Should cap tech stack score at max")
        void shouldCapTechStackScore() {
            Job job = createJob("Java Developer", "Spring, Kafka, AWS, Kubernetes, Docker experience.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("tech_stack", 20); // Capped at max
        }
    }

    @Nested
    @DisplayName("Threshold and total score")
    class ThresholdTests {

        @Test
        @DisplayName("Should pass threshold for high score job")
        void shouldPassThresholdForHighScoreJob() {
            Job job = createJob("Senior Java Developer - Remote",
                    "Spring Boot, Kafka, AWS. Remote LATAM friendly. B2B contract.", "Brazil");
            EligibilityResult eligibility = EligibilityResult.eligible(true, true, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.shouldApply()).isTrue();
            assertThat(result.score()).isGreaterThanOrEqualTo(70);
        }

        @Test
        @DisplayName("Should not pass threshold for low score job")
        void shouldNotPassThresholdForLowScoreJob() {
            Job job = createJob("Java Developer", "Basic Java work.", "San Francisco");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.shouldApply()).isFalse();
            assertThat(result.score()).isLessThan(70);
        }

        @Test
        @DisplayName("Should cap total score at 100")
        void shouldCapTotalScoreAt100() {
            // Create a job that would exceed 100 if not capped
            Job job = createJob("Senior Java Developer - Remote",
                    "Spring Boot, Kafka, AWS, Kubernetes. Remote worldwide. B2B contract. LATAM Brazil São Paulo.",
                    "Brazil");
            EligibilityResult eligibility = EligibilityResult.eligible(true, true, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.score()).isLessThanOrEqualTo(100);
        }

        @Test
        @DisplayName("Should return correct threshold from config")
        void shouldReturnCorrectThreshold() {
            assertThat(scoringService.getThreshold()).isEqualTo(70);
        }
    }

    @Nested
    @DisplayName("No US-only scoring")
    class NoUsOnlyTests {

        @Test
        @DisplayName("Should add points for passing eligibility")
        void shouldAddPointsForPassingEligibility() {
            Job job = createJob("Java Developer", "Building microservices.", "Remote");
            EligibilityResult eligibility = EligibilityResult.eligible(false, false, true);

            ScoringResult result = scoringService.calculateScore(job, eligibility);

            assertThat(result.breakdown()).containsEntry("no_us_only", 20);
        }
    }
}
