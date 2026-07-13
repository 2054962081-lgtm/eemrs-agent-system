param(
    [string]$BackendUrl = "http://localhost:8080",
    [string]$RagUrl = "http://localhost:18080",
    [string]$PatientAId = "AUTO_PATIENT_A_MEMORY_20260611",
    [string]$PatientBId = "AUTO_PATIENT_B_MEMORY_20260611",
    [string]$Password = "MemoryAutoTest@2026"
)

$ErrorActionPreference = "Stop"

$env:MEMORY_BACKEND_URL = $BackendUrl
$env:MEMORY_RAG_URL = $RagUrl
$env:MEMORY_TEST_SAMPLE_MODE = "1"
$env:MEMORY_TEST_SAMPLE_PATIENT_A_ID = $PatientAId
$env:MEMORY_TEST_SAMPLE_PATIENT_B_ID = $PatientBId
$env:MEMORY_TEST_SAMPLE_PASSWORD = $Password

python "$PSScriptRoot\memory_auto_test.py"
