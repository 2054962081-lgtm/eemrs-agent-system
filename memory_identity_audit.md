# Memory Identity Audit

## Scope

Audited `eemrs-server-master`, `agent-server`, and `frontend-vue` for identity fields and memory integration points.

## Findings

- `userId` / `user_id`: no stable login user id is present in the current REST authentication chain. The JWT principal is built from `idNumber` and does not expose a numeric user id.
- Patient primary identity in current business flow: `idNumber` is still the compatibility identity for login, appointment, medical record query, report query, and patient lookup.
- Existing hashed patient identifiers:
  - `PatientInfo.idHashCode`
  - `VisitInfo.patientIdHashCode`
  - `LabReport.patientIdHashCode`
  - `UserLogCrypto.buildIdHash(idNumber)` uses the existing SM3 helper.
- Login state:
  - Backend JWT contains `idNumber`, `type`, `role`, and optional `department`.
  - Frontend auth store persists `idNumber`, `role`, `type`, `department`, token, and token type. It does not persist `userId`.

## Modules That Directly Use ID Number

- Auth/login/register: `AuthController`, `AuthServiceAdapter`, `UserLogCrypto`, JWT security classes.
- Appointment: `AppointmentController`, `AppointmentServiceAdapter`, `GuahaoService`, `GuahaoCrypto`, frontend appointment and waiting-list views.
- Patient profile/lookup: `PatientController`, `PatientServiceAdapter`.
- Medical records: `MedicalRecordController`, `MedicalRecordServiceAdapter`, `DataOpCrypto`, medical record frontend search/editor paths.
- Lab reports: `LabReportController`, `LabReportService`, frontend lab report API types.
- Agent draft generation: `agent-server` draft request/repository stores `patient_id_number` in existing `agent_medical_record_draft` table.
- Pre-consultation frontend previously carried only `sessionId` to the agent. This change adds memory session calls through the authenticated main backend without adding an id number to the agent request.

## Memory Identity Decision

The new memory capability uses:

- Business compatibility id: current JWT `idNumber`.
- Memory key / vector filter id: `patientIdHash = UserLogCrypto.buildIdHash(idNumber)`.
- Redis key format: `memory:short:{patientIdHash}:{sessionId}`.
- Milvus metadata/filter field: `patientIdHash`.

No Redis key or Milvus metadata field introduced by this change stores a plaintext id number.

## Plaintext ID Exposure Check

- Existing business APIs and DTOs still use plaintext `idNumber` for backward compatibility.
- No new MySQL tables or SQL migration files were added.
- No plaintext id number is used in the new Redis key design.
- The new vector metadata builder only writes `patientIdHash`, `memoryLevel`, `sourceType`, `sourceId`, `department`, `eventTime`, and `createdAt`.
- No new logging of plaintext id number was added.

## Open Notes

- The Java backend now contains a `UserMemoryVectorService` wrapper with enforced `patientIdHash` filter and target collection `medical_user_memory`.
- The Python RAG service now exposes user-memory Milvus HTTP endpoints for `medical_user_memory`: `/memory/health`, `/memory/upsert`, `/memory/search`, and `/memory/delete-by-source`. Java vector memory is enabled by default and points to `http://localhost:18080`.
