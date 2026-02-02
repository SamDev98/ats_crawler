package dev.jobscanner.service;

import dev.jobscanner.model.Job;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

        @Mock
        private JavaMailSender mailSender;

        @Mock
        private TemplateEngine templateEngine;

        @Mock
        private MimeMessage mimeMessage;

        private EmailService emailService;

        @BeforeEach
        void setUp() {
                emailService = new EmailService(mailSender, templateEngine);
                ReflectionTestUtils.setField(Objects.requireNonNull(emailService), "fromEmail", "test@example.com");
                ReflectionTestUtils.setField(Objects.requireNonNull(emailService), "toEmail", "recipient@example.com");
        }

        private Job createJob(String title, int score, boolean remote, boolean contract) {
                return Job.builder()
                                .id("test-123")
                                .title(title)
                                .description("Test description")
                                .url("https://example.com/job")
                                .company("Test Company")
                                .location("Remote")
                                .source("Lever")
                                .score(score)
                                .remote(remote)
                                .contract(contract)
                                .discoveredAt(Instant.now())
                                .build();
        }

        @Nested
        @DisplayName("Send job digest")
        class SendJobDigestTests {

                @Test
                @DisplayName("Should send email successfully")
                @SuppressWarnings("null")
                void shouldSendEmailSuccessfully() {
                        List<Job> jobs = List.of(
                                        createJob("Java Developer 1", 85, true, false),
                                        createJob("Java Developer 2", 75, true, true));
                        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
                        when(templateEngine.process(eq("email/job-digest"), any(IContext.class)))
                                        .thenReturn("<html>Test email content</html>");

                        StepVerifier.create(emailService.sendJobDigest(jobs))
                                        .expectNext(true)
                                        .verifyComplete();

                        verify(mailSender).send(mimeMessage);
                }

                @Test
                @DisplayName("Should return false on messaging exception")
                @SuppressWarnings("null")
                void shouldReturnFalseOnMessagingException() {
                        List<Job> jobs = List.of(createJob("Java Developer", 80, true, false));
                        when(mailSender.createMimeMessage()).thenReturn(Objects.requireNonNull(mimeMessage));
                        when(templateEngine.process(eq("email/job-digest"), any(IContext.class)))
                                        .thenReturn("<html>Test</html>");
                        doThrow(new org.springframework.mail.MailSendException("SMTP error")).when(mailSender)
                                        .send(any(MimeMessage.class));

                        StepVerifier.create(emailService.sendJobDigest(jobs))
                                        .expectNext(false)
                                        .verifyComplete();
                }

                @Test
                @DisplayName("Should use correct template")
                void shouldUseCorrectTemplate() {
                        List<Job> jobs = List.of(createJob("Java Developer", 80, true, false));
                        when(mailSender.createMimeMessage()).thenReturn(Objects.requireNonNull(mimeMessage));
                        when(templateEngine.process(eq("email/job-digest"), any(IContext.class)))
                                        .thenReturn("<html>Test</html>");

                        emailService.sendJobDigest(jobs).block();

                        verify(templateEngine).process(eq("email/job-digest"), any(IContext.class));
                }
        }
}
