# 0003. Dynamic Agentic Context Expansion (NEED_MORE_LOGS Protocol) vs. Static Windowing or Vector RAG

* **Status:** Accepted
* **Date:** 2026-11-20
* **Decision Maker:** Mehdi Yasinzadeh
* **Scope:** AI Interaction Protocol & Contextual Log Retrieval

---

## 1. Context & Problem Statement

In production incident triage, an isolated stack trace does not always reveal the underlying root cause:
* Certain exceptions (such as code-level `NullPointerException` defects) contain 100% of the forensic evidence within the immediate stack trace.
* Other operational incidents (such as database connection pool exhaustion, network timeouts, or external service errors) leave vital causal evidence scattered in preceding lines logged seconds earlier.

Conventional fixed-window chunking approaches either:
1. Under-fetch context (e.g., fixed 10 lines), causing reasoning models to miss the upstream trigger.
2. Over-fetch context (e.g., sending 100 lines for every routine exception), wasting compute resources and diluting LLM reasoning.

---

## 2. Considered Alternatives

* **Alternative 1: Static Fixed-Size Slices:**  
  *Cons:* Inflexible, either truncating essential forensic context or inflating prompt latency.
* **Alternative 2: Vector Search RAG (Vector Databases):**  
  *Cons:* High ingestion latency for real-time streams; breaks temporal sequence and causal ordering of consecutive log lines.
* **Alternative 3: Agentic Dynamic Multi-Turn Context Expansion:**  
  Empowers the reasoning engine to autonomously assess context sufficiency and request targeted forensic lines.

---

## 3. Decision

We implemented **Alternative 3 (Agentic Multi-Turn Context Expansion)**:
1. During Turn 1, the incident is dispatched with baseline surrounding context.
2. If initial evidence is conclusive, the reasoning engine synthesizes the final diagnostic report (`DIAGNOSED`) in a single turn without delay.
3. If critical causal context is missing, the engine emits a structured `NEED_MORE_LOGS` directive specifying direction (`before/after`) and line count.
4. The orchestrator dynamically fetches the requested lines from the rolling circular buffer and resubmits the extended context in Turn 2 for final diagnosis.

---

## 4. Consequences & Trade-offs

### Positive Consequences:
* **Maximized Diagnostic Precision:** Successfully diagnoses both straightforward code defects and multi-step distributed failures.
* **Minimal Analytical Waste:** Avoids over-fetching for simple incidents while providing rich context where genuinely needed.
* **Strict Temporal Coherence:** Maintains consecutive temporal ordering without vector distortion.

### Negative Consequences / Trade-offs:
* Incidents demanding additional context incur an additional network turn, extending end-to-end latency to 10–12 seconds, which is well within acceptable operational SLA boundaries for complex incidents.
