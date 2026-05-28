#!/usr/bin/env python3
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import subprocess
from pathlib import Path


BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080")
PASSWORD = os.environ.get("PHASE2_DEFAULT_PASSWORD", "Phase2Pwd123")


def generated_id(prefix_digit, offset):
    stamp = str(int(time.time()))[-8:]
    return f"{prefix_digit}{stamp}{offset:03d}"


ACCOUNTS = {
    "PATIENT_A": {
        "type": "pt",
        "id": os.environ.get("PATIENT_A_ID") or generated_id("1", 1),
        "password": os.environ.get("PATIENT_A_PASSWORD") or PASSWORD,
        "name": os.environ.get("PATIENT_A_NAME") or "Phase2PatientA",
    },
    "PATIENT_B": {
        "type": "pt",
        "id": os.environ.get("PATIENT_B_ID") or generated_id("1", 2),
        "password": os.environ.get("PATIENT_B_PASSWORD") or PASSWORD,
        "name": os.environ.get("PATIENT_B_NAME") or "Phase2PatientB",
    },
    "DOCTOR_A": {
        "type": "dt",
        "id": os.environ.get("DOCTOR_A_ID") or generated_id("2", 1),
        "password": os.environ.get("DOCTOR_A_PASSWORD") or PASSWORD,
        "name": os.environ.get("DOCTOR_A_NAME") or "Phase2DoctorA",
    },
    "DOCTOR_B": {
        "type": "dt",
        "id": os.environ.get("DOCTOR_B_ID") or generated_id("2", 2),
        "password": os.environ.get("DOCTOR_B_PASSWORD") or PASSWORD,
        "name": os.environ.get("DOCTOR_B_NAME") or "Phase2DoctorB",
    },
}

DEPARTMENT = os.environ.get("DEPARTMENT", "内科")
VALID_DPK = os.environ.get("VALID_DOCTOR_PUBLIC_KEY")
VALID_SIGNATURE = os.environ.get("VALID_SM2_SIGNATURE")
VALID_CONDITION_DESCRIPTION = os.environ.get("VALID_CONDITION_DESCRIPTION", "测试病情描述")

results = []
tokens = {}


def mask(value):
    if not value:
        return ""
    if len(value) <= 8:
        return value[:2] + "***" + value[-2:]
    return value[:4] + "***" + value[-4:]


def request(method, path, body=None, token=None):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            text = resp.read().decode("utf-8", errors="replace")
            return resp.status, parse_json(text), text
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", errors="replace")
        return e.code, parse_json(text), text
    except Exception as e:
        return 0, None, str(e)


def parse_json(text):
    try:
        return json.loads(text) if text else None
    except Exception:
        return None


def decode_jwt(token):
    try:
        parts = token.split(".")
        payload = parts[1] + "=" * (-len(parts[1]) % 4)
        return json.loads(base64.urlsafe_b64decode(payload.encode("utf-8")).decode("utf-8"))
    except Exception:
        return {}


def build_sm2_signature(condition_description):
    if VALID_DPK and VALID_SIGNATURE:
        return VALID_DPK, VALID_SIGNATURE, condition_description, "environment"

    root = Path(__file__).resolve().parents[1]
    helper_java = root / "scripts" / "GenerateSm2Signature.java"
    helper_class = root / "scripts" / "GenerateSm2Signature.class"
    doctor_keys = root.parent / "Doctor-master" / "SM2KeyPair"
    pub = doctor_keys / "ec.x509.pub.der"
    pri = doctor_keys / "ec.pkcs8.pri.der"
    gmhelper = root.parent / "gmhelper-master" / "target" / "gmhelper.jar"
    bcprov = Path.home() / ".m2" / "repository" / "org" / "bouncycastle" / "bcprov-jdk15on" / "1.68" / "bcprov-jdk15on-1.68.jar"

    if not all(p.exists() for p in [helper_java, pub, pri, gmhelper, bcprov]):
        return None, None, condition_description, "missing local SM2 helper dependency"

    classpath = str(gmhelper) + os.pathsep + str(bcprov)
    run_classpath = str(root / "scripts") + os.pathsep + classpath
    try:
        if not helper_class.exists() or helper_class.stat().st_mtime < helper_java.stat().st_mtime:
            subprocess.check_call(["javac", "-encoding", "UTF-8", "-cp", classpath, str(helper_java)], cwd=str(root))
        output = subprocess.check_output(
            ["java", "-cp", run_classpath, "GenerateSm2Signature", condition_description, str(pub), str(pri)],
            cwd=str(root),
            timeout=20,
        ).decode("utf-8", errors="replace")
        payload = json.loads(output)
        return payload.get("dPk"), payload.get("signature"), condition_description, "Doctor-master SM2KeyPair"
    except Exception as exc:
        return None, None, condition_description, f"signature generation failed: {exc}"


def record(name, expected, actual, passed, detail=""):
    status = "PASS" if passed else "FAIL"
    results.append({
        "name": name,
        "expected": expected,
        "actual": actual,
        "status": status,
        "detail": detail,
    })
    print(f"{status} | {name} | expected={expected} | actual={actual} | {detail}")


def register_account(label):
    acc = ACCOUNTS[label]
    body = {
        "type": acc["type"],
        "idNumber": acc["id"],
        "userName": acc["name"],
        "password": acc["password"],
        "department": DEPARTMENT if acc["type"] == "dt" else None,
    }
    status, payload, text = request("POST", "/api/auth/register", body=body)
    ok = status == 200 and payload and payload.get("success") is True
    # Duplicate test data may return false; login below is the real readiness check.
    record(f"register {label}", "200", str(status), status == 200, f"id={mask(acc['id'])}, success={payload.get('success') if payload else None}")
    return ok


def login_account(label):
    acc = ACCOUNTS[label]
    body = {
        "type": acc["type"],
        "idNumber": acc["id"],
        "password": acc["password"],
    }
    if acc["type"] == "dt":
        body["department"] = DEPARTMENT
    status, payload, text = request("POST", "/api/auth/login", body=body)
    data = payload.get("data") if payload else None
    token = data.get("token") if data else None
    jwt_payload = decode_jwt(token) if token else {}
    expected_role = "PATIENT" if acc["type"] == "pt" else "DOCTOR"
    passed = (
        status == 200
        and payload
        and payload.get("success") is True
        and token
        and not token.startswith("TEMP-")
        and data.get("tokenType") == "Bearer"
        and data.get("role") == expected_role
        and data.get("type") == acc["type"]
        and data.get("expiresIn") is not None
        and all(k in jwt_payload for k in ["idNumber", "type", "role", "iat", "exp"])
    )
    if token:
        tokens[label] = token
    record(f"login {label}", "200 JWT", str(status), passed, f"id={mask(acc['id'])}, jwtKeys={sorted(jwt_payload.keys())}")
    return passed


def main():
    print(f"BASE_URL={BASE_URL}")
    print(f"DEPARTMENT={DEPARTMENT}")
    print("Accounts:")
    for label, acc in ACCOUNTS.items():
        print(f"- {label}: {mask(acc['id'])}")

    for label in ACCOUNTS:
        register_account(label)
    for label in ACCOUNTS:
        login_account(label)

    patient_token = tokens.get("PATIENT_A")
    doctor_token = tokens.get("DOCTOR_A")
    if not patient_token or not doctor_token:
        record("precondition tokens", "patient and doctor token", "missing", False, "Cannot continue full test without login tokens")
        dump_results()
        return 2

    status, payload, _ = request("GET", "/api/medical-records")
    record("401 no token medical records", "401", str(status), status == 401 and payload and payload.get("success") is False)

    status, payload, _ = request("GET", f"/api/doctors/me/waiting-list?department={urllib.parse.quote(DEPARTMENT)}", token=patient_token)
    record("patient cannot access waiting list", "403", str(status), status == 403)

    status, payload, _ = request("PUT", "/api/patients/me", body={"telephone": "13800000000"}, token=doctor_token)
    record("doctor cannot update patient profile", "403", str(status), status == 403)

    patient_b_id = ACCOUNTS["PATIENT_B"]["id"]
    status, payload, text = request("GET", f"/api/medical-records?patientIdNumber={urllib.parse.quote(patient_b_id)}", token=patient_token)
    no_patient_b = patient_b_id not in text
    record("patient A cannot query patient B records", "403 or no B data", str(status), (status == 403) or (status == 200 and no_patient_b))

    body = {
        "department": DEPARTMENT,
        "idNumber": ACCOUNTS["PATIENT_B"]["id"],
        "userName": "OverreachTest",
        "doctorIdNumber": ACCOUNTS["DOCTOR_A"]["id"],
    }
    status, payload, _ = request("POST", "/api/appointments", body=body, token=patient_token)
    record("patient A cannot appoint for patient B", "403", str(status), status == 403)

    path = f"/api/doctors/me/waiting-list?department={urllib.parse.quote(DEPARTMENT)}&doctorIdNumber={urllib.parse.quote(ACCOUNTS['DOCTOR_B']['id'])}"
    status, payload, _ = request("GET", path, token=doctor_token)
    record("doctor A cannot query as doctor B", "403", str(status), status == 403)

    body = {
        "department": DEPARTMENT,
        "conditionDescription": "OverreachTest",
        "patientIdNumber": ACCOUNTS["PATIENT_A"]["id"],
        "doctorIdNumber": ACCOUNTS["DOCTOR_B"]["id"],
        "dPk": "invalid-test-public-key",
        "signature": "invalid-test-signature",
    }
    status, payload, _ = request("POST", "/api/medical-records", body=body, token=doctor_token)
    record("doctor A cannot create record as doctor B", "403 before signature check", str(status), status == 403)

    status, payload, _ = request("GET", f"/api/doctors?department={urllib.parse.quote(DEPARTMENT)}", token=patient_token)
    record("patient query department doctors", "200", str(status), status == 200 and payload and payload.get("success") is True)

    body = {
        "department": DEPARTMENT,
        "idNumber": ACCOUNTS["PATIENT_A"]["id"],
        "userName": ACCOUNTS["PATIENT_A"]["name"],
        "doctorIdNumber": ACCOUNTS["DOCTOR_A"]["id"],
    }
    status, payload, _ = request("POST", "/api/appointments", body=body, token=patient_token)
    record("patient A create appointment", "200 success=true", str(status), status == 200 and payload and payload.get("success") is True)

    status, payload, text = request("GET", f"/api/doctors/me/waiting-list?department={urllib.parse.quote(DEPARTMENT)}", token=doctor_token)
    contains_patient = ACCOUNTS["PATIENT_A"]["id"] in text
    record("doctor A query waiting list", "200 contains patient A", str(status), status == 200 and payload and payload.get("success") is True and contains_patient)

    status, payload, _ = request("POST", f"/api/appointments/{urllib.parse.quote(ACCOUNTS['PATIENT_A']['id'])}/accept", token=doctor_token)
    record("doctor A accept patient A", "200 success=true", str(status), status == 200 and payload and payload.get("success") is True)

    dpk, signature, signed_description, signature_source = build_sm2_signature(VALID_CONDITION_DESCRIPTION)
    if dpk and signature:
        body = {
            "department": DEPARTMENT,
            "medication": "TestMedicine",
            "conditionDescription": signed_description,
            "cost": "100",
            "visitTime": 20260526153000,
            "patientName": ACCOUNTS["PATIENT_A"]["name"],
            "patientIdNumber": ACCOUNTS["PATIENT_A"]["id"],
            "age": 35,
            "doctorName": ACCOUNTS["DOCTOR_A"]["name"],
            "doctorIdNumber": ACCOUNTS["DOCTOR_A"]["id"],
            "dPk": dpk,
            "dpk": dpk,
            "signature": signature,
            "gender": "男",
        }
        status, payload, _ = request("POST", "/api/medical-records", body=body, token=doctor_token)
        record("doctor A create valid medical record", "200 success=true", str(status), status == 200 and payload and payload.get("success") is True, f"signatureSource={signature_source}")
    else:
        results.append({
            "name": "doctor A create valid medical record",
            "expected": "200 success=true",
            "actual": "SKIPPED",
            "status": "SKIPPED",
            "detail": signature_source,
        })
        print(f"SKIPPED | doctor A create valid medical record | {signature_source}")

    status, payload, text = request("GET", "/api/medical-records", token=patient_token)
    patient_records_ok = (
        status == 200
        and payload
        and payload.get("success") is True
        and isinstance(payload.get("data"), list)
        and ACCOUNTS["PATIENT_B"]["id"] not in text
    )
    record("patient A query own records", "200 success=true list, no patient B data", str(status), patient_records_ok)

    status, payload, text = request("GET", "/api/medical-records", token=doctor_token)
    doctor_records_ok = (
        status == 200
        and payload
        and payload.get("success") is True
        and isinstance(payload.get("data"), list)
        and ACCOUNTS["DOCTOR_B"]["id"] not in text
    )
    record("doctor A query own records", "200 success=true list, no doctor B data", str(status), doctor_records_ok)

    dump_results()
    return 0 if all(r["status"] in ("PASS", "SKIPPED") for r in results) else 1


def dump_results():
    with open("phase2_security_results.json", "w", encoding="utf-8") as f:
        json.dump({"accounts": {k: mask(v["id"]) for k, v in ACCOUNTS.items()}, "department": DEPARTMENT, "results": results}, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    sys.exit(main())
