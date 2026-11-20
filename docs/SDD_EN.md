# System Design Document (SDD)

## Product Name: Enterprise AIOps Incident & Root Cause Analysis Platform
* **Document Version:** 1.0.0
* **Status:** Approved
* **Reference Document:** [PRD_EN.md](file:///Users/mehdi/IdeaProjects/aiops-orchestrator/docs/PRD_EN.md)
* **System Architect:** Mehdi Yasinzadeh

---

## 1. High-Level Architecture Overview

The platform is designed around a **Two-Tier Cognitive Architecture (System 1 & System 2)** to achieve an optimal balance between high-throughput log screening, minimal compute overhead, and deep diagnostic reasoning:

* **Tier 1 (System 1 - Anomaly Gatekeeper):** An ultra-fast, non-autoregressive classifier that evaluates sliding log windows in single-digit milliseconds, filtering out routine operations from anomalous events.
* **Tier 2 (System 2 - Deep Reasoning Engine):** A local, dedicated Large Language Model (Local LLM) runtime that analyzes only verified anomalous incidents, conducting deep multi-turn root cause analysis.
* **Core Orchestrator:** The central coordinator managing ingestion persistence, dynamic contextual buffers, signature-based debouncing, and diagnostic workflows.

```
+----------------------------------------------------------------------------------------------------+
|                                    Target Enterprise Microservices                                 |
|                               (Standard Stream / File / Socket Emitters)                           |
+-------------------------------------------------+--------------------------------------------------+
                                                  | (Real-time Log Ingestion)
                                                  v
+----------------------------------------------------------------------------------------------------+
|                                aiops-orchestrator (Spring Boot 3.x)                                |
|                                                                                                    |
|  +--------------------+     +-----------------------+     +-------------------------------------+  |
|  | 1. Ingestion Engine| --> | 2. Dynamic Buffer     | --> | 3. Deduplication & Signature Extr.  |  |
|  | (Resilient Offset) |     | (Rolling Context Pool)|     | (Debounce Recurring Noise)          |  |
|  +--------------------+     +-----------------------+     +------------------+------------------+  |
|                                                                              |                     |
|                                                      [Suspicious Windows]    |                     |
|                                                                              v                     |
|                                                       +-----------------------------------------+  |
|                                                       | 4. System-1 Client (gRPC / Protobuf)    |  |
|                                                       |    Evaluate: NORMAL vs ERROR            |  |
|                                                       +----------------------+------------------+  |
|                                                                              |                     |
|                                                          [If Verified Error] |                     |
|                                                                              v                     |
|                                                       +-----------------------------------------+  |
|                                                       | 5. Multi-Turn Agentic Controller        |  |
|                                                       |    Handle NEED_MORE_LOGS Protocol       |  |
|                                                       +----------------------+------------------+  |
|                                                                              |                     |
|                                                                              v                     |
|                                                       +-----------------------------------------+  |
|                                                       | 6. Persistence & Metrics Layer          |  |
|                                                       |    (Spring Data JPA & Micrometer)       |  |
|                                                       +----------------------+------------------+  |
+------------------------------------------------------------------------------+---------------------+
                                                                               |
                              +------------------------------------------------+--------------------+
                              |                                                |                    |
                              v (gRPC / HTTP/2 Protobuf)                       v (REST /api/chat)   v (SQL / Read)
             +----------------------------------+            +-------------------+    +--------------------+
             |       aiops-laya-detector        |            |  Local LLM Runtime|    | aiops-observability|
             |       (gRPC Server / Python)     |            |  (Local Dedicated)|    | (Grafana Dashboard)|
             | - Non-autoregressive forward pass|            | - Deep RCA Engine |    | - Incident Table   |
             | - Sub-millisecond evaluation     |            | - Multi-Turn Loop |    | - Real-time Alert  |
             +----------------------------------+            +-------------------+    +--------------------+
```

---

## 2. Module Breakdown & Component Design

The platform is organized across three modular, decoupled repositories:

### 2.1. Central Orchestration Core (`aiops-orchestrator`)
The primary system backbone, built on **Java 21 and Spring Boot 3.x**:
1. **Ingestion Engine:**
   * Ingests high-throughput log streams asynchronously via non-blocking I/O.
   * Tracks persistent stream offsets to ensure seamless recovery following restarts without data loss.
2. **Dynamic Context Buffer:**
   * Retains an in-memory rolling circular buffer containing surrounding preceding and succeeding log lines for every evaluated event.
3. **Signature Extractor & Deduplicator:**
   * Derives unified incident signatures from raw stack traces (`RootException@Class.Method`).
   * Aggregates recurring error storms into single incident records, incrementing occurrence counters and eliminating redundant LLM invocations.
4. **Agentic Multi-Turn Controller:**
   * Dispatches incidents with initial context to the Tier-2 reasoning model.
   * Processes model decisions: upon receiving a `NEED_MORE_LOGS` directive, dynamically extracts the requested surrounding lines from the context buffer and re-prompts the model for final synthesis.
5. **Persistence & Observability Layer:**
   * Persists diagnosed incidents to relational storage using Spring Data JPA.
   * Exposes operational telemetry (incident volume, evaluation latency, filtering ratio) via Spring Boot Actuator and Micrometer.

### 2.2. Fast Classification Microservice (`aiops-laya-detector`)
A lightweight inference service implemented in **Python 3.11 and gRPC**:
* Executes non-autoregressive model inference over sliding log windows in a single forward pass without token generation overhead.
* Exposes a high-performance gRPC service over HTTP/2 using Protocol Buffers (supporting Unary and Streaming calls) operating with sub-millisecond latency.
* Categorizes log slices decisively into `Normal` or `Error`.

### 2.3. Deployment & Observability Package (`aiops-observability`)
Contains container definitions, relational datastore setup, and dashboard configurations:
* Docker Compose manifests providing a turnkey deployment of relational storage and Grafana.
* Production-ready Grafana dashboard templates designed to display real-time incident tables: timestamp, service name, signature, occurrence count, root cause analysis, and actionable remediation steps.

### 2.4. Local Reasoning Runtime (Local LLM Runtime)
* Serves a dedicated, code-specialized reasoning model entirely offline within the private network.
* Complies with standard HTTP REST protocols (`/api/chat`).
* Hardware-agnostic design, deployable on standard enterprise server infrastructure and local compute environments.

---

## 3. Data Flow & Sequence Scenarios

### Scenario 1: Routine Log Processing (Fast Path)
```
[Log Stream] ---> [Orchestrator] ---> [Laya Detector]
                         |                   |
                         | <--- (Normal) ----+
                         v
                  [Dropped / Ignored] (Zero LLM Overhead)
```

### Scenario 2: Critical Incident with Sufficient Context (Single-Turn Triage)
```
[Log Stream] ---> [Orchestrator: Ingest & Deduplicate]
                         |
                         v [Check Window]
                  [Laya Detector] ---> [Status: ERROR]
                         |
                         v [Turn 1: Stacktrace + Context]
                  [Local LLM]
                         |
                         v [DIAGNOSED: Root Cause + Remediation]
                  [Orchestrator]
                         |
                         v [Persist & Publish]
                  [Relational DB] ---> [Grafana Live Table]
```

### Scenario 3: Complex Incident Requiring Contextual Expansion (Multi-Turn Agentic Loop)
```
[Orchestrator] ---------------- [Turn 1: Incident Log] ---------------> [Local LLM]
                                                                               |
                               <--- [NEED_MORE_LOGS: direction, count] -------+
       |
       v (Dynamically extract surrounding lines from rolling buffer)
[Orchestrator] ---------------- [Turn 2: Extended Context] ------------> [Local LLM]
                                                                               |
                               <--- [DIAGNOSED: Final JSON RCA] --------------+
       |
       +---> [Persist to Relational DB] ---> [Update Grafana Dashboard]
```

---

## 4. Data Persistence & Incident Records

Diagnosed incidents and AI-generated analytical summaries are maintained as official operational records for auditing, triage tracking, and dashboard rendering:

* **Persistent Datastore:** Relational database storage (such as PostgreSQL) for structured queryability and historical triage auditing.
* **Incident Entity Attributes:** Each diagnostic record captures the following attributes:
  - **Incident Signature (`incident_signature`):** Canonical hash/string of root exception and throwing class/method, preventing duplicate record creation.
  - **Source Metadata (`service_name`):** Target application or component originating the log event.
  - **Severity & Status (`severity`, `status`):** Criticality classification (Critical, High, Medium) and resolution lifecycle state.
  - **Temporal & Frequency Tracking (`first_seen_at`, `last_seen_at`, `occurrence_count`):** Initial occurrence timestamp, latest occurrence timestamp, and cumulative error count.
  - **Root Cause Summary (`root_cause_summary`):** Plain-language synthesis of the underlying technical failure.
  - **Actionable Remediation (`actionable_remediation`):** Ordered list of concrete technical steps to resolve the defect.
  - **Diagnostic Metrics (`diagnostic_turns`):** Number of reasoning turns required to reach final diagnosis.
* Designed modularly via Spring Data JPA to remain completely agnostic of specific database engines and schemas.

---

## 5. Resilience, Fault Tolerance & Scaling Patterns

1. **Circuit Breaker Pattern:**  
   External calls to classification and reasoning services are wrapped in resilience boundaries (e.g., via Resilience4j) with bounded timeouts to prevent thread exhaustion or cascading stalls.
2. **Graceful Degradation:**  
   In the event of temporary unavailability of the Tier-1 classifier, the orchestrator automatically falls back to an emergency critical-rule filter, ensuring continuous ingestion without pipeline collapse.
3. **Asynchronous Non-Blocking Pipeline:**  
   Log ingestion and buffer management execute decoupled from analytical inference, ensuring that variable LLM reasoning latency never throttles raw log intake.

---

## 6. Security, Network Perimeter & Data Isolation

* **Isolated Network Mesh:** All components communicate exclusively over internal, private networks (isolated Docker bridge or private Kubernetes pod network).
* **Zero External Egress:** The entire pipeline operates completely offline without internet connectivity requirements.
* **Complete Data Containment:** AI evaluation runs on-premises, guaranteeing that sensitive internal log streams never leave the enterprise perimeter.
