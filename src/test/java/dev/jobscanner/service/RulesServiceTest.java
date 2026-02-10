package dev.jobscanner.service;

import dev.jobscanner.config.RulesConfig;
import dev.jobscanner.config.UserProfile;
import dev.jobscanner.model.Job;
import dev.jobscanner.service.RulesService.EligibilityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RulesServiceTest {

    private RulesService rulesService;
    private RulesConfig rulesConfig;
    private UserProfile userProfile;

    @BeforeEach
    void setUp() {
        rulesConfig = new RulesConfig();
        rulesConfig.setBlockTerms(List.of("us only", "u.s. only", "us citizen", "security clearance"));
        rulesConfig.setRemoteIndicators(List.of("remote", "distributed", "global", "worldwide"));
        rulesConfig.setContractIndicators(List.of("contract", "contractor", "b2b", "c2c", "freelance"));
        rulesConfig.setJavaTerms(List.of("java", "java developer", "java engineer", "backend java"));

        userProfile = new UserProfile();
        userProfile.setTargetTechnologies(new ArrayList<>());

        rulesService = new RulesService(rulesConfig, userProfile);
    }

    private Job createJob(String title, String description) {
        return Job.builder()
                .title(title)
                .description(description)
                .url("https://example.com/job")
                .company("Test Company")
                .location("Remote")
                .source("Test")
                .discoveredAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Java-related job detection")
    class JavaRelatedTests {

        @Test
        @DisplayName("Should accept job with Java in title")
        void shouldAcceptJavaInTitle() {
            Job job = createJob("Senior Java Developer", "Building microservices with Spring Boot.");
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isTrue();
            assertThat(result.isJavaRelated()).isTrue();
        }

        @Test
        @DisplayName("Should accept job with java in description")
        void shouldAcceptJavaInDescription() {
            Job job = createJob("Backend Developer", "We use Java, Spring Boot and Kafka.");
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isTrue();
            assertThat(result.isJavaRelated()).isTrue();
        }

        @Test
        @DisplayName("Should accept job with Spring Boot in description")
        void shouldAcceptSpringBootInDescription() {
            userProfile.setTargetTechnologies(List.of("Spring Boot"));
            Job job = createJob("Backend Developer",
                    "Building services with spring boot and microservices architecture.");
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isTrue();
            assertThat(result.isJavaRelated()).isTrue();
        }

        @Test
        @DisplayName("Should reject job that is not Java related")
        void shouldRejectNonJavaJob() {
            Job job = createJob("Python Developer", "Building services with Django and Flask.");
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isFalse();
            assertThat(result.blockReason()).isEqualTo("Not Java-related");
        }

        @Test
        @DisplayName("Should not match JavaScript as Java")
        void shouldNotMatchJavaScript() {
            Job job = createJob("JavaScript Developer", "Building frontend apps with JavaScript and React.");
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isFalse();
            assertThat(result.blockReason()).isEqualTo("Not Java-related");
        }
    }

    @Nested
    @DisplayName("Block terms detection")
    class BlockTermsTests {

        @ParameterizedTest(name = "Should block {1}")
        @CsvSource({
                "'Must be US only. Java and Spring Boot experience required.', 'us only'",
                "'Must be us citizen or permanent resident. Java experience required.', 'us citizen'",
                "'Security clearance required. Java and AWS experience.', 'security clearance'"
        })
        void shouldBlockSpecificTerms(String description, String expectedBlockReason) {
            Job job = createJob("Java Developer", description);
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isFalse();
            assertThat(result.blockReason()).isEqualTo(expectedBlockReason);
        }
    }

    @Nested
    @DisplayName("Remote indicator detection")
    class RemoteIndicatorTests {

        @ParameterizedTest(name = "Should detect {1} indicator")
        @CsvSource({
                "'Java Developer - Remote', 'This is a remote position. Java required.', 'remote'",
                "'Java Developer', 'We hire globally. Looking for Java expertise.', 'global'",
                "'Java Developer', 'Worldwide candidates welcome. Java and microservices.', 'worldwide'"
        })
        void shouldDetectRemoteIndicators(String title, String description, String indicatorType) {
            Job job = createJob(title, description);
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isTrue();
            assertThat(result.isRemote()).isTrue();
        }

        @Test
        @DisplayName("Should not mark as remote without indicator")
        void shouldNotMarkRemoteWithoutIndicator() {
            Job job = createJob("Java Developer", "Office-based in San Francisco. Java required.");
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isTrue();
            assertThat(result.isRemote()).isFalse();
        }
    }

    @Nested
    @DisplayName("Contract indicator detection")
    class ContractIndicatorTests {

        @ParameterizedTest(name = "Should detect {1} indicator")
        @CsvSource({
                "'Java Developer - Contract', 'This is a contract position. Java required.', 'contract'",
                "'Java Developer', 'B2B or employment available. Java and Spring Boot.', 'B2B'",
                "'Java Developer', 'Freelance opportunity for Java developers.', 'freelance'"
        })
        void shouldDetectContractIndicators(String title, String description, String indicatorType) {
            Job job = createJob(title, description);
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isTrue();
            assertThat(result.isContract()).isTrue();
        }

        @Test
        @DisplayName("Should not mark as contract without indicator")
        void shouldNotMarkContractWithoutIndicator() {
            Job job = createJob("Java Developer", "Full-time permanent position. Java required.");
            EligibilityResult result = rulesService.checkEligibility(job);

            assertThat(result.eligible()).isTrue();
            assertThat(result.isContract()).isFalse();
        }
    }

    @Nested
    @DisplayName("EligibilityResult factory methods")
    class EligibilityResultTests {

        @Test
        @DisplayName("Should create blocked result")
        void shouldCreateBlockedResult() {
            EligibilityResult result = EligibilityResult.blocked("test reason");

            assertThat(result.eligible()).isFalse();
            assertThat(result.blockReason()).isEqualTo("test reason");
            assertThat(result.isRemote()).isFalse();
            assertThat(result.isContract()).isFalse();
            assertThat(result.isJavaRelated()).isFalse();
        }

        @Test
        @DisplayName("Should create eligible result")
        void shouldCreateEligibleResult() {
            EligibilityResult result = EligibilityResult.eligible(true, true, true);

            assertThat(result.eligible()).isTrue();
            assertThat(result.blockReason()).isNull();
            assertThat(result.isRemote()).isTrue();
            assertThat(result.isContract()).isTrue();
            assertThat(result.isJavaRelated()).isTrue();
        }
    }
}
