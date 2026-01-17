# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Enterprise Java Project Standards (2026)

## 1. Core Technical Stack
- **Runtime:** Java 21 (LTS) - Utilizing Project Loom (Virtual Threads).
- **Build Tool:** Maven 3.9+ (Use `./mvnw` wrapper).
- **Framework:** Spring Boot 3.4+ / Jakarta EE 10+.
- **Architecture:** Domain-Driven Design (DDD) with Clean/Hexagonal boundaries.

## 2. Object-Oriented Design & Architecture
- **OOP Principles:** Strictly enforce SOLID, DRY, and KISS.
- **Composition over Inheritance:** Interfaces define contracts; implementation inheritance is discouraged.
- **Package Structure (Layered):**
  - `com.enterprise.project.domain`: Core logic, entities, value objects, interfaces (ports).
  - `com.enterprise.project.application`: Use cases, services, DTO mapping.
  - `com.enterprise.project.infrastructure`: Database, external API clients, configurations.
  - `com.enterprise.project.interfaces`: Controllers, Web adapters, CLI.
- **Immutability:** Use Java `record` for DTOs and Value Objects. Use `Lombok` for boilerplate reduction in entities.

## 3. Open Source & IP Governance
- **License Policy:** Only allow permissive licenses (Apache 2.0, MIT, BSD).
- **Forbidden Licenses:** Strictly avoid AGPL, GPL, or LGPL to protect proprietary IP.
- **Auditing:** Every build must run license verification.
- **Safety:** Do not use libraries that require proprietary cloud-side licensing for "Enterprise" features if open-source alternatives exist.

## 4. Security & Quality Standards
- **OWASP Compliance:** Follow Top 10 guidelines. Use `Jakarta Bean Validation` for all ingress data.
- **Supply Chain:** Generate SBOM (CycloneDX) on every build.
- **Secrets:** Never commit secrets. Use environment variables. Use `gitleaks` for pre-commit checks.
- **Tests:** - Mandatory JUnit 5 + Mockito for unit tests.
  - Integration tests for infrastructure using `Testcontainers` (Open Source).
  - Minimum 80% line coverage.

## 5. Build & Development Commands
- **Compile:** `./mvnw clean compile`
- **Run Locally:** `./mvnw spring-boot:run`
- **Run All Tests:** `./mvnw test`
- **Security Scan:** `./mvnw dependency-check:check` (OWASP)
- **License Audit:** `./mvnw license:check`
- **Linting:** `./mvnw checkstyle:check` (Google Java Style)

## 6. Development Workflow (AI Collaboration)
- **Plan First:** Claude must provide a technical plan before editing more than 2 files.
- **Atomic Commits:** One feature per commit using Conventional Commits (`feat:`, `fix:`, `docs:`).
- **Sub-Agents:** Use `/agents` for specialized tasks like deep security audits or generating Javadoc.
- **Verification:** After writing code, Claude must run `./mvnw clean compile` to verify the code compiles successfully.
