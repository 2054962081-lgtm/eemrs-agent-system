# Long-Term Memory Design

## Why MySQL Is The Source Of Truth

Long-term health information is stable clinical context: allergies, chronic diseases, family history, surgery history, long-term medication, and special population status. It must be editable, auditable, and queryable by patient scope.

This project now uses:

- MySQL: authoritative long-term health profile and memory detail storage.
- Redis: one-session short-term pre-consultation state and unconfirmed long-term memory candidates.
- Milvus: semantic retrieval index for confirmed long-term, medium-term, and archived short-term summaries.

Milvus is not the source of truth. If Milvus content and MySQL content differ, MySQL wins.

## Tables

The Java backend initializes these tables automatically at startup through `LongTermMemorySchemaInitializer`.

Reference SQL file:

```text
eemrs-server-master/src/main/resources/sql/long_term_memory_tables.sql
```

The SQL file is kept as a readable reference and fallback script; normal startup does not require manual execution.

### `user_health_profile`

Stores one active base health profile per patient.

Important fields:

- `patient_id_hash`: required patient scope key. No plaintext id number is stored.
- `id_number_hash`: compatibility hash for the current idNumber-based system.
- `gender`, `birth_date`, `height_cm`, `weight_kg`, `blood_type`: basic profile fields.
- `special_status`: pregnancy, lactation, child, elderly, immunocompromised, etc.
- `source`: `user_confirmed`, `doctor_record`, `system_extract`, etc.
- `confirmed`: whether the patient or clinician confirmed the data.
- `active`: soft-delete flag.

### `user_long_term_memory`

Stores detail memory items.

Supported `memory_type` values:

- `allergy`
- `family_history`
- `past_history`
- `chronic_disease`
- `surgery_history`
- `medication`
- `special_status`

Important fields:

- `patient_id_hash`: required patient scope key.
- `memory_type`: memory category.
- `memory_key`: keyword such as penicillin or hypertension.
- `memory_value`: detail content.
- `severity`: optional severity.
- `relation`: optional family relation.
- `evidence`: source description.
- `source`, `confirmed`, `active`: provenance and lifecycle fields.

## Privacy Boundary

The current login chain still uses `idNumber`, but memory business logic uses `PatientIdentityResolver` to convert the current authenticated user into `patientIdHash`.

Rules:

- Redis keys use `patientIdHash`, never plaintext id number.
- Milvus metadata uses `patientIdHash`, never plaintext id number.
- MySQL long-term memory tables use `patient_id_hash` and optional `id_number_hash`, never plaintext id number.
- Logs only record operation state, counts, ids, and memory type. They do not log plaintext id numbers.

## Candidate Confirmation Flow

Potential long-term memories from pre-consultation must not be auto-written to MySQL.

Flow:

1. A backend or AI extraction step identifies a candidate.
2. Candidate is stored in Redis:

```text
memory:long:candidate:{patientIdHash}:{sessionId}
```

3. Frontend or Postman calls `GET /api/memory/long/candidates?sessionId=...`.
4. User confirms with `POST /api/memory/long/candidates/{candidateId}/confirm`.
5. Backend writes the confirmed item to MySQL.
6. Backend upserts a semantic summary to Milvus `medical_user_memory`.
7. Rejected candidates are removed from Redis and are not written to MySQL.

## Prompt Integration

`GET /api/memory/context` now reads `longTermMemory` from MySQL through `LongTermMemoryService`.

The frontend calls `/api/memory/context` before calling the agent pre-consultation API, then passes `MemoryContextDTO` to `agent-server`.

`agent-server` injects the memory context into the final system prompt with these sections:

```text
【长期健康档案】
【近期就诊记忆】
【本次问诊状态】
【用户历史相似记忆】
【医学知识库 RAG】
```

If a field is missing, the backend returns `未记录`; the agent must not invent missing history.

## Milvus Sync

When a long-term memory item is added or updated:

- Java calls `UserMemoryVectorService.upsertMemoryVector`.
- `memoryLevel = long`
- `sourceType = long_term_memory`
- `sourceId = MySQL id`
- `department = general` unless a request provides a department.
- metadata includes `patientIdHash`.

When a long-term memory item is soft-deleted:

- MySQL record is updated to `active=0`.
- Java calls `deleteMemoryBySourceId(scope, "long_term_memory", id)`.
- Python RAG deletes by `patientIdHash`, `sourceType`, and `sourceId`.

## API Examples

Get profile:

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/memory/long/profile" `
  -Headers @{ Authorization = "Bearer <patient-token>" }
```

Save profile:

```powershell
$body = @{
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
  -Body $body
```

Add allergy:

```powershell
$body = @{
  memoryType = "allergy"
  memoryKey = "penicillin"
  memoryValue = "penicillin allergy, rash reaction"
  severity = "medium"
  source = "user_confirmed"
  confirmed = 1
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/memory/long/items" `
  -Headers @{ Authorization = "Bearer <patient-token>" } `
  -ContentType "application/json" `
  -Body $body
```

Check prompt context:

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/memory/context?sessionId=s1&query=allergy" `
  -Headers @{ Authorization = "Bearer <patient-token>" }
```

## Current Limits

- SQL is supplied as a reference script because this project does not currently use Flyway or Liquibase. The Java backend also creates/repairs the tables on startup.
- Candidate extraction from model output is not fully automated in this change. The Redis candidate APIs are ready for frontend or AI extraction integration.
- Frontend now has API methods for long-term memory operations, but no full management page has been built yet.
- Milvus sync is best-effort from the Java backend so clinical workflows are not blocked if the vector service is temporarily unavailable.
