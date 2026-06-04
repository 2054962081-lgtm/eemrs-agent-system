# EEMRS Agent Server

Java 17 Spring Boot service for patient pre-consultation and medical record draft generation.

## LLM Provider

The default LLM provider is DeepSeek cloud API. Ollama is still available for local/private workloads and can be selected by environment variables.

The default model is `deepseek-v4-flash`.

## Configure DeepSeek

PowerShell:

```powershell
$env:DEEPSEEK_API_KEY="your-api-key"
$env:LLM_DEFAULT_PROVIDER="deepseek"
$env:PRE_CONSULTATION_LLM_PROVIDER="deepseek"
$env:MEDICAL_RECORD_DRAFT_LLM_PROVIDER="deepseek"
```

CMD:

```bat
set DEEPSEEK_API_KEY=your-api-key
set LLM_DEFAULT_PROVIDER=deepseek
set PRE_CONSULTATION_LLM_PROVIDER=deepseek
set MEDICAL_RECORD_DRAFT_LLM_PROVIDER=deepseek
```

Do not commit the real DeepSeek API key to GitHub, `application.yml`, frontend files, README files, or logs.

## Start

```powershell
cd agent-server
mvn spring-boot:run
```

## Configure Database

Medical record draft generation writes to MySQL table `agent_medical_record_draft`.

By default, `agent-server` reuses the local datasource settings from:

```text
../eemrs-server-master/src/main/resources/application.yml
```

The draft table is created automatically during startup with `CREATE TABLE IF NOT EXISTS`.

Pre-consultation can work without database access, but draft generation cannot be saved if the database credentials are missing or wrong.

You can still override the datasource explicitly.

PowerShell:

```powershell
$env:AGENT_DB_URL="jdbc:mysql://localhost:3306/eemrs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:AGENT_DB_USERNAME="root"
$env:AGENT_DB_PASSWORD="your-mysql-password"
```

CMD:

```bat
set AGENT_DB_URL=jdbc:mysql://localhost:3306/eemrs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
set AGENT_DB_USERNAME=root
set AGENT_DB_PASSWORD=your-mysql-password
```

## Health Check

Open:

```text
http://localhost:8081/api/agent/health
```

The response includes LLM routing status and whether DeepSeek is configured. It never returns the API key.

## Test Pre-Consultation

```text
POST http://localhost:8081/api/agent/pre-consultation
```

## Switch Back To Ollama

PowerShell:

```powershell
$env:PRE_CONSULTATION_LLM_PROVIDER="ollama"
$env:MEDICAL_RECORD_DRAFT_LLM_PROVIDER="ollama"
```

CMD:

```bat
set PRE_CONSULTATION_LLM_PROVIDER=ollama
set MEDICAL_RECORD_DRAFT_LLM_PROVIDER=ollama
```

Ollama defaults:

```text
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3-vl:8b
```

## Fallback

Fallback is disabled by default:

```text
LLM_FALLBACK_ENABLED=false
LLM_FALLBACK_PROVIDER=ollama
```

When fallback is disabled, a DeepSeek failure returns a friendly error directly. When enabled, the service retries with the configured fallback provider.

## Compliance Note

DeepSeek cloud API receives patient-provided pre-consultation content. Production use requires de-identification, authorization, audit, and compliance review. This setup is for local development MVP validation.
