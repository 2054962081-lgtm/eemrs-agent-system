# Memory Integration Test Guide

This guide verifies the EEMRS memory path:

- MySQL long-term health profile and long-term memory details
- Redis short-term memory
- Milvus user memory collection `medical_user_memory`
- Python RAG memory HTTP APIs
- Java `/api/memory/context`
- pre-consultation prompt memory injection
- session complete archive to Milvus

The Java backend creates the long-term memory MySQL tables automatically on startup.

## 0. Create Long-Term Memory Tables

Normally no manual SQL step is required. When `eemrs-server-master` starts, `LongTermMemorySchemaInitializer` creates or repairs the required tables.

Reference SQL is still available here:

```text
eemrs-server-master/src/main/resources/sql/long_term_memory_tables.sql
```

The backend initializer creates:

- `user_health_profile`
- `user_long_term_memory`

## 1. Start Infrastructure

Start Redis and Milvus with the existing local Docker setup.

Check ports:

```powershell
Test-NetConnection -ComputerName localhost -Port 6379
Test-NetConnection -ComputerName localhost -Port 19530
```

Expected:

- Redis `TcpTestSucceeded=True`
- Milvus `TcpTestSucceeded=True`

## 2. Start Python RAG Service

```powershell
python -m rag.rag_api_server
```

The service listens on:

```text
http://localhost:18080
```

Health check for user memory collection only:

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:18080/memory/health"
```

Expected response shape:

```json
{
  "success": true,
  "collection": "medical_user_memory",
  "collection_exists": true,
  "error_message": null
}
```

If `collection_exists=false`, the first `/memory/upsert` will lazily create it.

## 3. Test Python Memory APIs

Use a fake hash for manual testing. Do not use a plaintext ID number.

```powershell
$hash = "test_patient_hash_manual_001"
$source = "manual-source-001"

$upsertBody = @{
  collection = "medical_user_memory"
  text = "user memory test: persistent cough and fever, respiratory clinic suggested."
  metadata = @{
    patientIdHash = $hash
    memoryLevel = "medium"
    sourceType = "pre_inquiry_summary"
    sourceId = $source
    department = "respiratory"
    eventTime = 1718000000000
    createdAt = 1718000001000
  }
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18080/memory/upsert" `
  -ContentType "application/json; charset=utf-8" `
  -Body $upsertBody
```

Expected:

```json
{
  "success": true,
  "collection": "medical_user_memory",
  "inserted_count": 1
}
```

Search with the required patient filter:

```powershell
$searchBody = @{
  collection = "medical_user_memory"
  query = "cough fever respiratory"
  topK = 3
  filter = "patientIdHash == '$hash'"
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18080/memory/search" `
  -ContentType "application/json; charset=utf-8" `
  -Body $searchBody
```

Expected:

- `success=true`
- `results[0].metadata.patientIdHash == $hash`
- result text matches the inserted memory

Delete by source:

```powershell
$deleteBody = @{
  collection = "medical_user_memory"
  sourceId = $source
  filter = "patientIdHash == '$hash'"
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18080/memory/delete-by-source" `
  -ContentType "application/json; charset=utf-8" `
  -Body $deleteBody
```

Expected:

```json
{
  "success": true,
  "deleted_count": 1
}
```

## 4. Start Java Main Backend

```powershell
cd eemrs-server-master
mvn spring-boot:run
```

Relevant defaults:

```properties
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
memory.vector.enabled=${MEMORY_VECTOR_ENABLED:true}
memory.vector.collection=${MEMORY_VECTOR_COLLECTION:medical_user_memory}
memory.vector.service-url=${MEMORY_VECTOR_SERVICE_URL:http://localhost:18080}
```

## 5. Start Agent Server

```powershell
cd agent-server
mvn spring-boot:run
```

Agent server listens on:

```text
http://localhost:8081
```

## 6. Start Frontend

```powershell
cd frontend-vue
npm run dev
```

Open:

```text
http://localhost:5173
```

## 7. Test `/api/memory/context`

Login as a patient in the frontend or get a patient JWT from `/api/auth/login`.

Call:

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/memory/context?sessionId=manual-session-001&query=cough%20fever" `
  -Headers @{ Authorization = "Bearer <patient-token>" }
```

Expected:

- `longTermMemory` is present
- `mediumTermMemory` is present
- `shortTermMemory` is present when the session exists in Redis
- `relatedUserMemory` contains Milvus results for the current `patientIdHash` when matching memory exists

The backend never sends plaintext ID numbers to Redis keys or Milvus metadata.

## 7.1 Test Long-Term MySQL Memory APIs

Get long-term profile:

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/memory/long/profile" `
  -Headers @{ Authorization = "Bearer <patient-token>" }
```

Save long-term profile:

```powershell
$profile = @{
  gender = "female"
  birthDate = "1990-01-01"
  bloodType = "A"
  specialStatus = "未记录"
  source = "user_confirmed"
  confirmed = 1
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/memory/long/profile" `
  -Headers @{ Authorization = "Bearer <patient-token>" } `
  -ContentType "application/json" `
  -Body $profile
```

Add a long-term allergy memory:

```powershell
$item = @{
  memoryType = "allergy"
  memoryKey = "penicillin"
  memoryValue = "penicillin allergy, rash reaction"
  severity = "medium"
  source = "user_confirmed"
  confirmed = 1
  department = "general"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/memory/long/items" `
  -Headers @{ Authorization = "Bearer <patient-token>" } `
  -ContentType "application/json" `
  -Body $item
```

Expected:

- MySQL has one active row in `user_long_term_memory`.
- Python `/memory/search` can retrieve it with the current `patientIdHash` filter.
- `/api/memory/context` shows it under `longTermMemory.allergyHistory`.

Delete it:

```powershell
Invoke-RestMethod -Method Delete `
  -Uri "http://localhost:8080/api/memory/long/items/<id>" `
  -Headers @{ Authorization = "Bearer <patient-token>" }
```

Expected:

- MySQL row is soft deleted with `active=0`.
- Milvus memory is deleted by `patientIdHash`, `sourceType=long_term_memory`, and `sourceId=<id>`.

## 8. Test Prompt Injection

In the patient pre-consultation page:

1. Start a consultation.
2. Submit a symptom question.
3. The frontend first calls `/api/memory/context`.
4. The returned `MemoryContextDTO` is sent to `agent-server` in `memoryContext`.
5. `PreConsultationService` appends these prompt sections before calling the LLM:

```text
【长期健康档案】
【近期就诊记忆】
【本次问诊状态】
【用户历史相似记忆】
【医学知识库 RAG】
```

The medical knowledge RAG still comes from `/rag/retrieve`; user memory comes only from `medical_user_memory` with the current `patientIdHash` filter.

## 9. Test Complete Archive

Complete a pre-consultation from the frontend.

Expected Java flow:

1. `POST /api/memory/short/session/{sessionId}/complete`
2. `ShortTermMemoryService` reads Redis short memory.
3. If no summary is passed, it builds a summary from `askedQuestions` and `answers`.
4. `UserMemoryVectorService.upsertMemoryVector` sends the summary to Python RAG `/memory/upsert`.
5. Milvus metadata includes `patientIdHash`.

Verify by searching:

```powershell
$searchBody = @{
  collection = "medical_user_memory"
  query = "<symptom keyword>"
  topK = 5
  filter = "patientIdHash == '<current-patient-hash>'"
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:18080/memory/search" `
  -ContentType "application/json; charset=utf-8" `
  -Body $searchBody
```

## 9.1 Run Sample Full Auto Test

After Redis, Milvus, Python RAG, and the Java backend are running, use the sample runner to create or reuse two fake patient accounts and run the full memory loop:

```powershell
.\scripts\run_memory_auto_test_sample.ps1
```

The runner sets:

```powershell
MEMORY_TEST_SAMPLE_MODE=1
MEMORY_TEST_SAMPLE_PATIENT_A_ID=AUTO_PATIENT_A_MEMORY_20260611
MEMORY_TEST_SAMPLE_PATIENT_B_ID=AUTO_PATIENT_B_MEMORY_20260611
```

These are not plaintext ID numbers. The script registers or reuses both sample patients, logs in to obtain JWTs, writes test-only memory data, verifies patient isolation, restores any previous profile when present, and cleans the auto-test long-memory items plus Milvus sources.

The detailed result is written to:

```text
memory_auto_test_report.md
```

## 10. Build Verification

```powershell
cd eemrs-server-master
mvn -q -DskipTests compile

cd ..\agent-server
mvn -q -DskipTests compile

cd ..\frontend-vue
npm run build
```

If PowerShell blocks `npm.ps1`, use:

```powershell
npm.cmd run build
```

## Latest Local Verification

Manual Python memory API verification succeeded locally:

- `/memory/health`: `success=true`
- `/memory/upsert`: inserted `1`
- `/memory/search`: returned the inserted item with matching `patientIdHash`
- `/memory/delete-by-source`: deleted `1`
