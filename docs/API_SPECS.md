# سند مشخصات قراردادهای واسط و پروتکل‌های ارتباطی (API & Protocol Specifications)

## نام محصول: پلتفرم هوشمند تحلیل و ریشه‌یابی رخدادها در سیستم‌های توزیع‌شده (Enterprise AIOps Platform)
* **نسخه سند:** 1.0.0
* **وضعیت:** پیش‌نویس جهت بررسی (Draft for Review)
* **پروتکل پایه ارتباطات بین‌سرویسی:** **gRPC بر بستر HTTP/2 با سریالایزیشن Protocol Buffers (Protobuf)**
* **معمار سیستم:** مهدی یاسین‌زاده

---

## ۱. معماری ارتباطات مبتنی بر gRPC (Inter-Service Communication Architecture)

تمامی ارتباطات داخلی میان مؤلفه‌های پلتفرم (`aiops-orchestrator`, `aiops-laya-detector` و سرویس‌های ورودی) بر پایه **پروتکل ارتباطی gRPC و باینری Protobuf** انجام می‌شود:

### مزایای کلیدی gRPC در سیستم پایش لاگ:
1. **سرعت و تاخیر ناچیز (Sub-Millisecond Overhead):** سریالایزیشن باینری تا ۱۰ برابر سریع‌تر و فشرده‌تر از متون حجیم JSON است.
2. **پشتیبانی از استریمینگ دوطرفه (Bidirectional & Server/Client Streaming):** انتقال پیوسته استریم لاگ‌ها بدون نیاز به باز و بسته کردن مداوم سوکت‌های TCP.
3. **انطباق نوع داده قوی (Strict Type-Safety):** کدهای کلاینت جاوا (در اسپرینگ‌بوت) و سرور پایتون از روی یک فایل مرجع واحد `.proto` تولید می‌شوند.

```
+-------------------------------------------------------------------------------------------------+
|                                 Inter-Service gRPC Flow Architecture                            |
|                                                                                                 |
|   [Target Application / Shippers]                                                               |
|                 │                                                                               |
|                 ▼ (gRPC LogStreamService - HTTP/2 Streaming)                                    |
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

## ۲. قرارداد سرویس تشخیص سریع ناهنجاری (`detector.proto`)

این فایل پروتوباف (`.proto`) قرارداد رسمی ارتباط بین هسته **Spring Boot** و سرویس پایتونی **Laya** است:

```protobuf
syntax = "proto3";

package aiops.detector.v1;

option java_multiple_files = true;
option java_package = "ir.mehdiyasinzadeh.aiops.grpc.detector";
option java_outer_classname = "DetectorProto";

// سرویس تشخیص ناهنجاری لایه اول
service AnomalyDetectorService {
  // ارزیابی یک پنجره لاگ به صورت تک‌فراخوانی (Unary)
  rpc ClassifyWindow (ClassifyWindowRequest) returns (ClassifyWindowResponse);

  // ارزیابی جریانی و بدون وقفه پنجره‌های لاگ (Client Streaming)
  rpc StreamClassifyWindows (stream ClassifyWindowRequest) returns (stream ClassifyWindowResponse);
}

// پیام درخواست ارزیابی پنجره لاگ
message ClassifyWindowRequest {
  string window_id = 1;
  string service_name = 2;
  repeated string log_lines = 3;
  int64 start_line = 4;
  int64 end_line = 5;
  int64 timestamp_epoch_ms = 6;
}

// وضعیت خروجی طبقه‌بندی
enum ClassificationLabel {
  LABEL_UNSPECIFIED = 0;
  NORMAL = 1;
  ERROR = 2;
}

// پیام پاسخ ارزیابی
message ClassifyWindowResponse {
  string window_id = 1;
  ClassificationLabel label = 2;
  float confidence = 3;
  string rule_triggered = 4;
  int64 evaluation_duration_micros = 5;
}
```

---

## ۳. قرارداد استریم دریافت لاگ‌ها (`ingestion.proto`)

جهت دریافت پرسرعت، بدون وقفه و ناهمگام لاگ‌ها از سرویس‌ها یا ایجنت‌ها به هسته اسپرینگ‌بوت:

```protobuf
syntax = "proto3";

package aiops.ingestion.v1;

option java_multiple_files = true;
option java_package = "ir.mehdiyasinzadeh.aiops.grpc.ingestion";
option java_outer_classname = "IngestionProto";

// سرویس استریم دریافت لاگ
service LogIngestionService {
  // ارسال جریانی خطوط لاگ از سیستم‌های مبدا
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

## ۴. پروتکل تعامل هوشمند چندمرحله‌ای با مدل استنتاج (LLM Agentic Protocol)

تعامل هسته اسپرینگ‌بوت با مدل زبانی لوکال بر پایه **اسکیمای ساختاریافته JSON** و پروتکل تعاملی دومرحله‌ای انجام می‌پذیرد:

### ۴.۱. دستورالعمل پایه سیستم (System Prompt)
```json
{
  "system": "You are an elite Senior Site Reliability Engineer (SRE) and Enterprise Systems Diagnostics Expert. Your task is to analyze real-world system errors. You must reply strictly in valid JSON format."
}
```

### ۴.۲. نوبت اول (Turn 1: ارسال رخداد اولیه)
**پیام ارسالی توسط Orchestrator:**
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

### ۴.۳. اسکیمای پاسخ مدل در حالت نیاز به زمینه بیشتر (`NEED_MORE_LOGS`)
اگر مدل برای تشخیص دقیق به سطرهای قبل یا بعد نیاز داشته باشد، دقیقاً این ساختار را برمی‌گرداند:
```json
{
  "status": "NEED_MORE_LOGS",
  "direction": "before",          // "before" | "after" | "both"
  "lines_count": 15,
  "reason": "توضیح صریح علت نیاز به خطوط ماقبل یا مابعد"
}
```

### ۴.۴. نوبت دوم (Turn 2: تزریق پویای خطوط تکمیلی)
ارکستریتور خطوط درخواستی را از استخر بافر استخراج کرده و پیام تکمیلی را می‌فرستد:
```text
Here are the additional requested surrounding log lines from the buffer:
{additional_lines}

Synthesize the complete picture and provide your final DIAGNOSED JSON report.
```

### ۴.۵. اسکیمای پاسخ نهایی و گزارش عیب‌یابی (`DIAGNOSED`)
```json
{
  "status": "DIAGNOSED",
  "severity": "CRITICAL",         // "CRITICAL" | "HIGH" | "MEDIUM" | "LOW"
  "root_cause_summary": "خلاصه علت ریشه‌ای خطا به زبان فارسی در حداکثر یک جمله صریح",
  "detailed_analysis": "تحلیل فنی دلیل وقوع خطا به زبان فارسی در ۲ الی ۳ جمله",
  "actionable_remediation": [
    "گام اجرایی اول جهت رفع قطعی باگ",
    "گام اجرایی دوم جهت رفع قطعی باگ"
  ]
}
```
