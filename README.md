# Job Scanner (ATS Scraper)

An automated job discovery and scoring engine designed for **Java Developers (Backend/Fullstack)** with a specific focus on the **LATAM and Brazilian market**.

The scanner monitors multiple Applicant Tracking Systems (ATS) to find new opportunities, scores them based on relevance, and delivers a summarized digest via email.

## 🚀 Key Features

- **Multi-ATS Support**: Scans companies using Greenhouse, Lever, Ashby, Recruitee, and SmartRecruiters.
- **LATAM Focused**: Pre-configured with over 100+ prominent companies in Brazil and Latin America.
- **Smart Scoring**: Ranks jobs based on keywords (Java 21, Spring Boot, Micronaut, etc.) and location (LATAM/Brazil boost).
- **Persistence & Deduplication**: Uses SQLite to track sent jobs and avoid duplicates.
- **Monitoring**: Built-in Prometheus metrics and Grafana dashboard support.
- **Automation**: Ready-to-use GitHub Actions for daily automated scans.
- **AI-Powered (Planned)**: Integration with Google Gemini for advanced job description analysis.

## 🛠️ Technology Stack

- **Java 21** & **Spring Boot 3.5.x**
- **SQLite**: Local data storage.
- **Micrometer/Prometheus**: Metrics collection.
- **Thymeleaf**: HTML email templates.
- **Docker**: For monitoring stack (Prometheus/Grafana).
- **GitHub Actions**: CI/CD and automation.

## 📋 Prerequisites

- Java 21+
- Docker (optional, for monitoring)
- SMTP credentials (for email notifications)

## ⚙️ Configuration

The application is highly customizable via `src/main/resources/`:

- **application.yml**: Core settings, email config, and the list of companies to scan.
- **rules.yml**: Domain/Keyword rules for filtering jobs.
- **weights.yml**: Scoring weights for different technologies and seniority levels.

## 🚀 How to Run

### Local Execution

```bash
./mvnw clean package
# Normal run
./mvnw spring-boot:run
# Dry run (checks only, doesn't send emails or save to DB)
./scripts/run-dry.sh
```

### With Docker (Monitoring Stack)

```bash
make up
# The app runs on port 8081
# Prometheus: http://localhost:9090
```

## 📈 Monitoring

The application exposes metrics at `/actuator/prometheus`.
If running via Docker, Prometheus is configured to scrape the application automatically using `host.docker.internal`.

---

Developed for high-performance job hunting.
