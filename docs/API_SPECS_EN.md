# API & Protocol Specifications: Inter-Service gRPC & Agentic LLM Protocols

## Product Name: Enterprise AIOps Incident & Root Cause Analysis Platform
* **Document Version:** 1.0.0
* **Status:** Approved
* **Inter-Service Transport Protocol:** **gRPC over HTTP/2 with Protocol Buffers (Protobuf) Serialization**
* **System Architect:** Mehdi Yasinzadeh

---

## 1. Inter-Service gRPC Architecture & Communication Flow

All internal communication across platform services (`aiops-orchestrator`, `aiops-laya-detector`, and high-throughput log shippers) is implemented using **gRPC over HTTP/2 and Protocol Buffers**:

### Key Advantages in Enterprise Log Monitoring:
1. **Sub-Millisecond Overhead & High Throughput:** Binary Protobuf serialization operates up to 10x faster and substantially more compact than bulky JSON payloads.
2. **Streaming Capabilities (Client/Server & Bidirectional Streaming):** Continuous, non-blocking log transmission over persistent HTTP/2 multiplexed streams without repetitive TCP handshake overhead.
3. **Strict Polyglot Type-Safety:** Strong contract enforcement where Java client stubs (Spring Boot) and Python server stubs are generated from authoritative `.proto` definitions.

```
+-------------------------------------------------------------------------------------------------+
|                                 Inter-Service gRPC Flow Architecture                            |
|                                                                                                 |
|   [Target Applications / Log Shippers]                                                          |
|                 │                                                                               |
|                 ▼ (gRPC LogIngestionService - HTTP/2 Streaming)                                 |
|   ┌─────────────────────────────┐                                                               |
|   │     aiops-orchestrator      │ <==== (gRPC AnomalyDetectorService) ====> [aiops-laya-detector] |
|   │    (Spring Boot 3 / Java)   │                                           [ (Python gRPC Srv) ]
|   └─────────────┬───────────────┘                                                               |
|                 │                                                                               |
|                 ▼ (Local REST API / Strict JSON Protocol)                                       |
|   ┌─────────────────────────────┐                                                               |
|   │      Local LLM Runtime      │                                                               |
|   └─────────────────────────────┘                                                               |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Fast Anomaly Detector Protobuf Contract (`detector.proto`)

The authoritative Protocol Buffer (`.proto`) definition governing communication between the **Spring Boot Orchestrator** and the **Python Laya Detector**:

```protobuf
syntax = "proto3";

package aiops.detector.v1;

option java_multiple_files = true;
option java_package = "ir.mehdiyasinzadeh.aiops.grpc.detector";
option java_outer_classname = "DetectorProto";

// Tier-1 Ultra-Fast Anomaly Detection Service
service AnomalyDetectorService {
  // Evaluates a single candidate log window (Unary RPC)
  rpc ClassifyWindow (ClassifyWindowRequest) returns (ClassifyWindowResponse);

  // Evaluates continuous streaming log windows (Client Streaming RPC)
  rpc StreamClassifyWindows (stream ClassifyWindowRequest) returns (stream ClassifyWindowResponse);
}

// Request payload containing windowed log lines
message ClassifyWindowRequest {
  string window_id = 1;
  string service_name = 2;
  repeated string log_lines = 3;
  int64 start_line = 4;
  int64 end_line = 5;
  int64 timestamp_epoch_ms = 6;
}

// Classification state label
enum ClassificationLabel {
  LABEL_UNSPECIFIED = 0;
  NORMAL = 1;
  ERROR = 2;
}

// Response payload with evaluation verdict
message ClassifyWindowResponse {
  string window_id = 1;
  ClassificationLabel label = 2;
  float confidence = 3;
  string rule_triggered = 4;
  int64 evaluation_duration_micros = 5;
}
```

---

## 3. High-Throughput Log Ingestion Protobuf Contract (`ingestion.proto`)

Defines the high-throughput asynchronous log intake contract from target services or lightweight shippers into the Spring Boot Orchestrator:

```protobuf
syntax = "proto3";

package aiops.ingestion.v1;

option java_multiple_files = true;
option java_package = "ir.mehdiyasinzadeh.aiops.grpc.ingestion";
option java_outer_classname = "IngestionProto";

// Log Streaming Ingestion Service
service LogIngestionService {
  // Continuous streaming log transmission from emitting sources
  rpc StreamLogs (stream LogBatchRequest) returns (LogIngestionAck);
}

message LogEntry {
  int64 timestamp_epoch_ms = 1;
  string log_level = 2;         // INFO, ERROR, WARN, DEBUG
  string thread_name = 3;
  string logger_name = 4;
  string message = 5;
  repeated string stack_trace = 6;
}

message LogBatchRequest {
  string source_service = 1;
  string environment = 2;
  int64 batch_sequence = 3;
  repeated LogEntry entries = 4;
}

message LogIngestionAck {
  bool acknowledged = 1;
  int64 last_processed_sequence = 2;
  int64 server_time_ms = 3;
}
```

---

## 4. Multi-Turn Agentic LLM Reasoning Protocol

The structured protocol governing interaction between the Spring Boot Orchestrator and the Local Reasoning Model uses strict, strongly typed JSON schemas:

### 4.1. System Prompt Instruction
```json
{
  "system": "You are an elite Senior Site Reliability Engineer (SRE) and Enterprise Systems Diagnostics Expert. Your task is to analyze real-world system errors. You must reply strictly in valid JSON format."
}
```

### 4.2. Turn 1 (Initial Incident Dispatch)
**Dispatched by Orchestrator:**
```text
[INCIDENT REPORT]
Service: {service_name}
Signature: {incident_signature}
Occurrences: {count} times

--- SURROUNDING CONTEXT (Prior Lines) ---
{context_before}

--- ERROR & STACK TRACE ---
{stack_trace_lines}

Evaluate if this context is sufficient to diagnose. If yes, respond with DIAGNOSED. If critical context is missing, respond with NEED_MORE_LOGS.
```

### 4.3. Response Schema for Additional Context Requests (`NEED_MORE_LOGS`)
If the reasoning engine requires preceding or subsequent contextual lines, it emits strictly:
```json
{
  "status": "NEED_MORE_LOGS",
  "direction": "before",          // "before" | "after" | "both"
  "lines_count": 15,
  "reason": "Clear explanation of what forensic evidence is missing"
}
```

### 4.4. Turn 2 (Dynamic Context Injection)
The Orchestrator extracts requested lines from the dynamic rolling context buffer and submits:
```text
Here are the additional requested surrounding log lines from the buffer:
{additional_lines}

Synthesize the complete picture and provide your final DIAGNOSED JSON report.
```

### 4.5. Final Diagnostic Report Schema (`DIAGNOSED`)
```json
{
  "status": "DIAGNOSED",
  "severity": "CRITICAL",         // "CRITICAL" | "HIGH" | "MEDIUM" | "LOW"
  "root_cause_summary": "Concise summary of underlying failure in a single sentence",
  "detailed_analysis": "Technical explanation of the fault chain in 2 to 3 sentences",
  "actionable_remediation": [
    "Step 1: First concrete remediation action",
    "Step 2: Second concrete remediation action"
  ]
}
```
