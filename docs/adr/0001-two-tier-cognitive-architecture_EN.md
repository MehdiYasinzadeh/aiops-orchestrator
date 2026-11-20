# 0001. Two-Tier Cognitive Architecture (System 1 & System 2) vs. Monolithic LLM Pipeline

* **Status:** Accepted
* **Date:** 2026-11-20
* **Decision Maker:** Mehdi Yasinzadeh
* **Scope:** Core System Architecture & AI Inference Strategy

---

## 1. Context & Problem Statement

Modern enterprise distributed microservices generate millions of log entries daily. During operational incidents, a single underlying failure (such as database connection exhaustion or network partition) triggers thousands of redundant stack traces per second.

Piping this raw log stream directly into an autoregressive Large Language Model (LLM):
1. Demands impractical computational resources and rapidly saturates infrastructure compute.
2. Introduces severe processing latency due to token-by-token autoregressive generation.
3. Wastes significant analytical compute, given that over 90% of enterprise log traffic consists of routine operations or redundant error duplicates.

---

## 2. Considered Alternatives

* **Alternative 1: Direct Monolithic LLM Pipeline:**  
  *Pros:* Simple single-component implementation.  
  *Cons:* Non-scalable, high latency, and immediate failure during log storms.
* **Alternative 2: Pure Static Regex Rules Without AI:**  
  *Pros:* Extremely fast evaluation.  
  *Cons:* Brittle, incapable of adapting to novel failure modes, zero diagnostic reasoning.
* **Alternative 3: Two-Tier Cognitive Architecture (System 1 Fast Gatekeeper + System 2 Deep RCA):**  
  Inspired by Daniel Kahneman's cognitive decision framework.

---

## 3. Decision

We selected **Alternative 3 (Two-Tier Cognitive Architecture)**:

1. **Tier 1 (System 1 - Anomaly Gatekeeper):** A sub-millisecond, non-autoregressive classifier combined with signature-based debouncing that screens sliding log windows in a single forward pass without token generation, immediately discarding normal traffic.
2. **Tier 2 (System 2 - Deep Reasoning Engine):** A dedicated local reasoning model invoked exclusively for verified, deduplicated incidents to perform root cause analysis and actionable remediation synthesis.

---

## 4. Consequences & Trade-offs

### Positive Consequences:
* **85% to 99% Compute Load Reduction:** Prevents reasoning model saturation by filtering routine traffic and error bursts.
* **Sub-Millisecond Normal Path Latency:** Healthy system logs flow through without blocking or analytical lag.
* **Hardware Efficiency:** Predictable computational footprint deployable on standard enterprise infrastructure.

### Negative Consequences / Trade-offs:
* Managing two distinct model layers (fast classification and autoregressive reasoning) coordinated through the orchestrator.
