# Job Scanner (ATS Scraper)

An automated job discovery and scoring engine designed for **Java Developers (Backend/Fullstack)** with a specific focus on the **LATAM and Brazilian market**.

The scanner monitors multiple Applicant Tracking Systems (ATS) to find new opportunities, scores them based on relevance, and delivers a summarized digest via email.

## 🚀 Key Features

- **Multi-ATS Support**: Scans companies using Greenhouse, Lever, Ashby, Recruitee, and SmartRecruiters.
- **LATAM Focused**: Pre-configured with hundreds of active companies in Brazil and Latin America.
- **Smart Scoring**: Ranks jobs based on keywords (Java, Spring Boot, etc.) and location (LATAM/Brazil boost).
- **High Performance**: Optimized regex engine with caching for fast processing of thousands of job descriptions.
- **AI-Powered Analysis**: Integration with **DeepSeek V3** (via OpenRouter) to validate if positions are truly remote-friendly for LATAM candidates.
- **Customizable Profiles**: Personalize your search criteria via a simple `profile.json` file.
- **Persistence & Deduplication**: Uses SQLite to track sent jobs and avoid duplicates.
- **Robust & Type-Safe**: Built-in null-safety and defensive programming to handle inconsistent ATS data.
- **Monitoring**: Built-in Prometheus metrics and Grafana dashboard support.
- **Automation**: Ready-to-use GitHub Actions for daily automated scans.

## 🛠️ Technology Stack

- **Java 21** & **Spring Boot 3.5.x**
- **AI**: Focused on Open-Source and free-tier models via **OpenRouter** (defaults to **DeepSeek V3**).
- **SQLite**: Local data storage.
- **Micrometer/Prometheus**: Metrics collection.
- **Thymeleaf**: HTML email templates.
- **Docker**: For monitoring stack (Prometheus/Grafana).

## 📋 Prerequisites

- Java 21+
- API Key from [OpenRouter](https://openrouter.ai/) (optional, for AI filtering)
- SMTP credentials (for email notifications)

## ⚙️ Configuration

The application is highly customizable:

1.  **profile.json**: (Root directory) Define your target technologies, seniority level, and scoring weights. Copy from `profile-example.json`.
2.  **application.yml**: Core settings and the list of companies to scan.
3.  **Environment Variables**:
    - `SMTP_PASSWORD`: For email notifications.
    - `OPENROUTER_API_KEY`: For AI analysis.

### AI Providers and Models

While the primary focus is on **DeepSeek V3** due to its high performance and low cost (often free/cheap on OpenRouter), you can easily switch to other models by changing the `app.ai.openrouter.model` property.

Popular alternatives available through OpenRouter:

- `google/gemini-2.0-flash-001` (Fast and efficient)
- `anthropic/claude-3-haiku` (Excellent reasoning)
- `meta-llama/llama-3.3-70b-instruct` (Open Source)

To use a different model, simply update your `application.yml` or set the `OPENROUTER_MODEL` environment variable.

### Customizing your profile

Create a `profile.json` in the project root:

```json
{
  "name": "Seu Nome",
  "target_technologies": ["java", "spring boot", "quarkus"],
  "experience_level": "Senior",
  "locations": ["brazil", "portugal", "latam"],
  "scoring": {
    "threshold": 70,
    "weights": {
      "java_in_title": 40,
      "latam_brazil_boost": 30
    }
  }
}
```

### Advanced Performance & Reliability

The engine includes several architectural optimizations:

- **Pattern Caching**: Uses `ConcurrentHashMap` to cache compiled `java.util.regex.Pattern` objects, significantly reducing CPU cycles during scoring and filtering.
- **Defensive Data Handling**: Implements clean-up patterns for URLs and multi-layer null checks to prevent crashes from malformed ATS input.
- **Parameterized Testing**: Core services are validated through high-coverage parameterized tests ensuring consistent scoring across diverse job formats.

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
