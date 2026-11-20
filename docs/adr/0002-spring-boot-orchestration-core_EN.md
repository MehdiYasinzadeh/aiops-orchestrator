# 0002. Spring Boot 3 & Java 21 as Core Orchestration Backbone vs. Monolithic Python Architecture

* **Status:** Accepted
* **Date:** 2026-11-20
* **Decision Maker:** Mehdi Yasinzadeh
* **Scope:** Backend Technology Stack & Core System Orchestration

---

## 1. Context & Problem Statement

The central orchestrator of the AIOps platform must concurrently execute high-throughput operational tasks with maximum reliability:
1. Ingest continuous high-frequency log streams from distributed enterprise microservices.
2. Manage dynamic rolling in-memory context buffers without memory leaks.
3. Manage enterprise datastore connections, transactional resilience, and health metric emission.
4. Orchestrate polyglot inter-service communication across Python inference services and local reasoning runtimes.

While Python dominates the machine learning ecosystem, running long-lived high-throughput log ingestion pipelines in Python presents challenges with Global Interpreter Lock (GIL) contention, thread scalability, and connection pool resilience under heavy load.

---

## 2. Considered Alternatives

* **Alternative 1: Monolithic All-in-Python Architecture:**  
  *Pros:* Single language across ML models and ingestion.  
  *Cons:* Less resilient concurrency management under high I/O throughput compared to the JVM; higher memory footprint for streaming buffers.
* **Alternative 2: All-in-Go Architecture:**  
  *Pros:* Excellent performance and lightweight concurrency.  
  *Cons:* Mismatch with team expertise; less mature enterprise AI/data integration ecosystem.
* **Alternative 3: Polyglot Architecture (Spring Boot 3 / Java 21 Core + Isolated Python ML Service):**  
  Delegates enterprise streaming and data orchestration to the JVM while keeping AI inference in Python.

---

## 3. Decision

We chose **Alternative 3**:
* The core orchestration engine, stream ingestion, circular buffer pools, and relational persistence are implemented in **Java 21 and Spring Boot 3.x** leveraging Project Loom Virtual Threads for high-concurrency non-blocking I/O.
* Python is strictly isolated to serving the fast classification model via a dedicated microservice, communicating with the Spring Boot core over **gRPC using binary Protocol Buffers**.

---

## 4. Consequences & Trade-offs

### Positive Consequences:
* **Enterprise-Grade Stability:** Robust JVM garbage collection, memory profiling, and massive concurrency scaling via Java 21 Virtual Threads.
* **Full Engineering Control & Transparence:** Elimination of "black box" operational behavior in the central business logic.
* **Resilient Infrastructure:** First-class production monitoring via Spring Data JPA and Spring Boot Actuator.

### Negative Consequences / Trade-offs:
* Managing dual runtime build environments (JVM and Python virtual environments), cleanly mitigated using independent Docker containers.
