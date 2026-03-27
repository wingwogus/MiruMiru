🚀 Spring Boot Kotlin Initial Template

A production-grade multi-module Spring Boot 3.x + Kotlin starter template.
This project includes the essential building blocks for real-world backend services such as:

* API Response standardization
* Global exception handling
* JWT authentication
* Module separation (api / application / domain / batch)
* Logging with MDC
* JPA configuration
* Swagger UI
* Environment-specific profiles

You can run this project immediately, then customize the package name and DB settings to fit your service.


🔧 Required Customization Before Use

This template is prepared for public sharing.
If you plan to use it for your own service, you must update the following items.


1️⃣ Change Base Package (com.example → your domain)
All modules (api / application / domain / batch) use package com.example.
Rename it to your organization or project domain.

  IntelliJ shortcut:
Right-click package → Refactor → Rename (Shift + F6)
Imports will update automatically.

2️⃣ Configure Your Own Database
Local profile uses PostgreSQL.
You can override credentials via env vars.

src/main/resources/application-local.yml:
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/MiruMiru
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    driver-class-name: org.postgresql.Driver

3️⃣ Replace JWT Secret Key
A sample secret is included.
Generate a new one:

openssl rand -hex 32

Set it inside your application-local.yml / application-dev.yml.

4️⃣ Update Swagger Info
Modify SwaggerConfig.kt to match your project branding:
.info(
  Info()
    .title("Your API")
    .description("Your project description")
)

5️⃣ Redis (Optional)

Redis dependency is included.
If your service doesn’t use Redis, simply remove:

implementation("org.springframework.boot:spring-boot-starter-data-redis")


▶ How to Run
⭐ Recommended: IntelliJ Run Button
Runs with the application-local profile by default.

⭐ Official Method: Gradle bootRun
./gradlew :api:bootRun

🐳 One-click Dev (Docker Compose)
Run PostgreSQL + Redis + API in one command:

1) (Optional) copy env template
cp ../.env.example ../.env

2) start everything
docker compose -f ../compose.yml up --build

Then open:
- Swagger: http://localhost:8080/swagger-ui/index.html
- Chat test page: http://localhost:8080/chat-test.html


🧹 Local DB Reset (DROP SCHEMA)
If your local `MiruMiru` DB has old tables/data:

PGPASSWORD=$POSTGRES_PASSWORD psql -h localhost -p 5432 -U $POSTGRES_USER -d MiruMiru -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
