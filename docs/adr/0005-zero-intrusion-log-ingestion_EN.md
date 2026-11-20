# 0005. Zero-Intrusion Log Ingestion Strategy vs. Invasive In-App Instrumentation Agents

* **Status:** Accepted
* **Date:** 2026-11-20
* **Decision Maker:** Mehdi Yasinzadeh
* **Scope:** Log Ingestion Architecture & Host System Reliability

---

## 1. Context & Problem Statement

In enterprise environments, operational teams rigorously resist changes that threaten the production stability of core applications:
1. Observability tools requiring application code modifications or invasive proprietary SDKs face prolonged approval delays and integration friction.
2. Synchronous in-app logging appenders introduce severe operational risks: if the monitoring backend stalls or suffers a network partition, host applications experience thread starvation and cascading downtime.

---

## 2. Considered Alternatives

* **Alternative 1: Mandatory Proprietary SDKs:**  
  *Cons:* Heavy organizational friction; tightly couples client business code to the monitoring vendor.
* **Alternative 2: Invasive Bytecode Instrumentation Agents:**  
  *Cons:* High operational risk; JVM version incompatibilities can trigger unrecoverable crashes in host applications.
* **Alternative 3: Zero-Intrusion Offset-Tracked Ingestion:**  
  Tapping into standard system log channels, container stdout, or asynchronous non-blocking network streams without touching client code.

---

## 3. Decision

We selected **Alternative 3 (Zero-Intrusion Ingestion)**:
* The platform enforces a strict **Zero-Intrusion** policy: logs are captured via standard runtime streams, files, or non-blocking network sockets without modifying target applications.
* Ingestion pipelines operate purely asynchronously and non-blocking; under no circumstance will monitoring unavailability impact host application throughput.
* Persistent stream offset tracking guarantees that analytical processing seamlessly resumes from the exact recorded position following system restarts.

---

## 4. Consequences & Trade-offs

### Positive Consequences:
* **Frictionless Plug & Play Deployment:** No recompilation, re-testing, or redeployment required for target enterprise services.
* **Zero Production Blast Radius:** Absolute guarantee against thread locking or cascading stalls in host applications.
* **Resilient Data Continuity:** Eliminates log loss across orchestrator maintenance or restart cycles via offset tracking.

### Negative Consequences / Trade-offs:
* The orchestrator must handle multi-line stack trace stitching in its stream parsing layer, which is cleanly managed within Spring Boot's high-performance stream buffer engine.
