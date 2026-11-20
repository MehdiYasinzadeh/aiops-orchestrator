# System Design Document (SDD)

## نام محصول: پلتفرم هوشمند تحلیل و ریشه‌یابی رخدادها در سیستم‌های توزیع‌شده (Enterprise AIOps Platform)
* **نسخه سند:** 1.0.0
* **وضعیت:** پیش‌نویس جهت بررسی (Draft for Review)
* **سند مرجع:** [PRD.md](file:///Users/mehdi/IdeaProjects/aiops-orchestrator/docs/PRD.md)
* **معمار سیستم:** مهدی یاسین‌زاده

---

## ۱. نمای کلی معماری (High-Level Architecture Overview)

معماری پلتفرم بر پایه مدل شناختی دو لایه (**Two-Tier Cognitive Architecture: System 1 & System 2**) طراحی شده است تا تعادل بهینه‌ای میان سرعت غربال‌گری، مصرف بهینه منابع پردازشی و عمق استدلال تشخیصی برقرار کند:

* **لایه ۱ (System 1 - Anomaly Gatekeeper):** یک ارزیاب فوق‌سریع و غیرخودبازگشتی که پنجره‌های لاگ را در کسری از میلی‌ثانیه بررسی کرده و موارد عادی را از رخدادهای مشکوک تفکیک می‌کند.
* **لایه ۲ (System 2 - Deep Reasoning Engine):** یک موتور استنتاجی زبانی محلی (Local LLM) که صرفاً رخدادهای تاییدشده را تحلیل کرده و به صورت تعاملی و چندمرحله‌ای ریشه‌یابی عمیق انجام می‌دهد.
* **هسته ارکستراسیون (Core Orchestrator):** هماهنگ‌کننده کل فرآیند، مدیریت زمینه لاگ‌ها، تجمیع خطاها و ذخیره‌سازی نتایج.

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
|                                                       | 4. System-1 Client (REST)               |  |
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
                              v (REST POST /v1/classify)                       v (REST /api/chat)   v (SQL Query)
             +----------------------------------+            +-------------------+    +--------------------+
             |       aiops-laya-detector        |            |  Local LLM Runtime|    | aiops-observability|
             |       (FastAPI / Python)         |            |  (Local Dedicated)|    | (Grafana Dashboard)|
             | - Non-autoregressive forward pass|            | - Deep RCA Engine |    | - Incident Table   |
             | - Sub-millisecond evaluation     |            | - Multi-Turn Loop |    | - Real-time Alert  |
             +----------------------------------+            +-------------------+    +--------------------+
```

---

## ۲. تفکیک ماژول‌ها و وظایف (Module Breakdown & Component Design)

پلتفرم به سه ریپازیتوری کاملاً مستقل تفکیک شده است:

### ۲.۱. هسته هماهنگ‌کننده (`aiops-orchestrator`)
این ماژول، ستون فقرات پلتفرم است و به زبان **Java 21 و Spring Boot 3.x** توسعه می‌یابد:
1. **موتور دریافت لاگ (Ingestion Engine):**
   * دریافت استریم لاگ‌ها به صورت غیرمسدودکننده (Non-blocking I/O).
   * نگهداری شناسه موقعیت پردازش (Offset Persistence) جهت بازیابی دقیق پس از ری‌استارت بدون مفقود شدن رخدادها.
2. **استخر حافظه زمینه پویا (Dynamic Context Buffer):**
   * نگهداری بافر متحرک از سطرهای ماقبل و مابعد هر رخداد در حافظه رم با ساختار داده بافر حلقوی (Circular RingBuffer).
3. **لایه استخراج امضا و تجمیع خطاها (Signature Extractor & Deduplicator):**
   * استخراج خودکار ریشه خطا و نقطه وقوع در کد نرم‌افزاری به عنوان امضای رخداد (`RootException@Class.Method`).
   * تجمیع رخدادهای تکراری ناشی از یک امضا و به‌روزرسانی شمارنده وقوع (Occurrence Count) جهت کاهش بار محاسباتی.
4. **کنترلر تعاملی چندمرحله‌ای (Agentic Multi-Turn Controller):**
   * ارسال رخداد همراه با زمینه اولیه به مدل استدلال لایه دوم.
   * پردازش تصمیمات مدل: در صورت دریافت سیگنال `NEED_MORE_LOGS`، استخراج دینامیک سطرهای درخواستی از بافر و ارسال مجدد به مدل در نوبت دوم.
5. **لایه ماندگاری و پایش (Persistence & Metrics):**
   * ذخیره رخدادهای نهایی در پایگاه داده PostgreSQL با استفاده از Spring Data JPA.
   * انتشار شاخص‌های کارایی (تعداد حوادث، زمان پاسخ، نرخ فیلترینگ) از طریق Spring Boot Actuator.

### ۲.۲. میکروسرویس غربال‌گری سریع (`aiops-laya-detector`)
این ماژول سبک و تخصصی به زبان **Python 3.11 و FastAPI** پیاده‌سازی می‌شود:
* اجرای مدل غیرخودبازگشتی (Non-autoregressive) جهت ارزیابی پنجره‌های لاگ در یک Forward Pass ساده بدون تولید متن.
* ارائه وب‌سرویس REST با اندپوینت `POST /v1/classify` با زمان پاسخ‌دهی زیر ۵ میلی‌ثانیه.
* تفکیک قطعی رخدادهای عادی (`Normal`) از موارد مشکوک (`Error`).

### ۲.۳. بسته استقرار و مانیتورینگ (`aiops-observability`)
این مخزن شامل نیازمندی‌های استقرار، دیتابیس و نمایش ریل‌تایم است:
* پیکربندی Docker Compose جهت راه‌اندازی پایگاه داده PostgreSQL و داشبورد Grafana.
* قالب‌های پیش‌ساخته JSON برای داشبورد گرافانا با تمرکز بر نمایش: زمان رخداد، نام سرویس، امضای خطا، تعداد تکرار، علت ریشه‌ای و گام‌های عملیاتی رفع باگ.

### ۲.۴. موتور استنتاج محلی لایه دوم (Local LLM Runtime)
* اجرای مدل زبانی متخصص تحلیل کد به صورت محلی و کاملاً ایزوله از اینترنت.
* پشتیبانی از پروتکل استاندارد REST API (`/api/chat`).
* استقلال از معماری سخت‌افزاری (Hardware-Agnostic) و قابلیت اجرا روی سرورهای محلی سازمان.

---

## ۳. جریان داده و دیاگرام‌های توالی (Data Flow & Sequence Scenarios)

### سناریوی ۱: پردازش لاگ عادی (مسیر سریع - Fast Path)
```
[Log Stream] ---> [Orchestrator] ---> [Laya Detector]
                         |                   |
                         | <--- (Normal) ----+
                         v
                  [Dropped / Ignored] (Zero LLM Overhead)
```

### سناریوی ۲: رخداد بحرانی با زمینه کافی (تک نوبت - Single Turn)
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
                         v [Save & Alert]
                  [PostgreSQL DB] ---> [Grafana Live Table]
```

### سناریوی ۳: رخداد پیچیده نیازمند زمینه بیشتر (دو نوبت - Multi-Turn Agentic Loop)
```
[Orchestrator] ---------------- [Turn 1: Incident Log] ---------------> [Local LLM]
                                                                               |
                               <--- [NEED_MORE_LOGS: direction, count] -------+
       |
       v (Extract extra lines dynamically from buffer)
[Orchestrator] ---------------- [Turn 2: Extended Context] ------------> [Local LLM]
                                                                               |
                               <--- [DIAGNOSED: Final JSON RCA] --------------+
       |
       +---> [Persist to PostgreSQL] ---> [Update Grafana Dashboard]
```

---

## ۴. مدل داده و ساختار پایگاه داده (Data Model & Schema)

جدول پایگاه داده `incidents` در PostgreSQL به عنوان مخزن مرکزی ثبت حوادث تعریف می‌شود:

```sql
CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    incident_signature VARCHAR(255) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,          -- CRITICAL, HIGH, MEDIUM, LOW
    status VARCHAR(20) NOT NULL,            -- OPEN, INVESTIGATING, RESOLVED
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    occurrence_count INTEGER DEFAULT 1,
    root_cause_summary TEXT NOT NULL,
    actionable_remediation JSONB NOT NULL,   -- Array of concrete action steps
    diagnostic_turns INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_signature_service UNIQUE (incident_signature, service_name)
);

CREATE INDEX idx_incidents_last_seen ON incidents (last_seen_at DESC);
CREATE INDEX idx_incidents_severity ON incidents (severity);
```

---

## ۵. تاب‌آوری، مدیریت خطا و الگوهای مقیاس‌پذیری (Resilience & Reliability)

1. **الگوی قطع‌کننده مدار (Circuit Breaker):**  
   در صورت بروز وقفه یا کندی در سرویس Laya یا LLM، فراخوانی‌ها توسط ابزار مدیریت تاب‌آوری (مانند Resilience4j) کنترل شده و از قفل شدن نخ‌های پردازشی (Thread Blockage) جلوگیری می‌شود.
2. **سیاست تنزل باوقار (Graceful Degradation):**  
   اگر سرویس لایه اول به هر دلیلی در دسترس نباشد، ارکستریتور به طور خودکار به یک فیلتر مبتنی بر قوانین بحرانی (Fallback Rule Filter) سوئیچ می‌کند تا فرآیند پایش لاگ‌ها متوقف نگردد.
3. **دریافت ناهمگام و غیرمسدودکننده (Non-blocking I/O):**  
   جریان خواندن و بافر لاگ‌ها به صورت کاملاً غیرمسدودکننده از فرآیند تحلیل استنتاجی جدا است تا افزایش زمان تحلیل در لایه دوم هیچ تاثیری بر سرعت جذب لاگ‌ها نگذارد.

---

## ۶. امنیت، جداسازی شبکه و حفظ حریم خصوصی (Security & Isolation)

* **ایزولاسیون کامل زیرساخت:** تمامی ماژول‌ها در بستر شبکه محلی و امن سازمان (Isolated Docker Bridge یا Kubernetes Private Pod Network) با یکدیگر ارتباط برقرار می‌کنند.
* **عدم وابستگی به دسترسی اینترنت:** کل فرآیند دریافت، فیلترینگ، ریشه‌یابی و مصورسازی بدون نیاز به هیچ‌گونه اتصال اینترنتی انجام می‌پذیرد.
* **جداسازی مرز پردازش:** مدل‌های زبانی به صورت On-Premises اجرا شده و هیچ اطلاعاتی خارج از مرز زیرساخت سازمان منتقل نمی‌گردد.
