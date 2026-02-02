package dev.jobscanner.service;

import dev.jobscanner.model.Job;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Service for sending job digest emails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${scanner.email.to}")
    private String toEmail;

    /**
     * Send a job digest email with qualified jobs.
     *
     * @param jobs List of qualified jobs to include
     * @return Mono<Boolean> indicating success or failure
     */
    @SuppressWarnings("null")
    public Mono<Boolean> sendJobDigest(List<Job> jobs) {
        return Mono.fromCallable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String subject = String.format("Job Scanner: %d New Java Developer Positions - %s",
                        jobs.size(), today);

                helper.setFrom(fromEmail);
                helper.setTo(toEmail);
                helper.setSubject(subject);

                // Generate HTML content using Thymeleaf
                String htmlContent = generateEmailContent(jobs);
                helper.setText(htmlContent, true);

                mailSender.send(message);
                log.info("Email sent successfully to {}", toEmail);
                return true;

            } catch (MessagingException | MailException e) {
                log.error("Failed to send email: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * Generate HTML email content using Thymeleaf template.
     */
    private String generateEmailContent(List<Job> jobs) {
        Context context = new Context(Locale.getDefault());
        context.setVariable("jobs", jobs);
        context.setVariable("jobCount", jobs.size());
        context.setVariable("date", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));

        // Calculate statistics
        long remoteCount = jobs.stream().filter(Job::isRemote).count();
        long contractCount = jobs.stream().filter(Job::isContract).count();
        double avgScore = jobs.stream().mapToInt(Job::getScore).average().orElse(0);

        context.setVariable("remoteCount", remoteCount);
        context.setVariable("contractCount", contractCount);
        context.setVariable("avgScore", String.format("%.1f", avgScore));

        return templateEngine.process("email/job-digest", context);
    }
}
