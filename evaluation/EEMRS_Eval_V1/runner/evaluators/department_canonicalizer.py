from __future__ import annotations


UNKNOWN = "UNKNOWN"


_ALIASES = {
    "gastroenterology": "Gastroenterology",
    "消化内科": "Gastroenterology",
    "消化科": "Gastroenterology",
    "胃肠内科": "Gastroenterology",
    "general surgery": "General Surgery",
    "普通外科": "General Surgery",
    "普外科": "General Surgery",
    "emergency": "Emergency",
    "emergency medicine": "Emergency",
    "急诊科": "Emergency",
    "急诊": "Emergency",
    "cardiology": "Cardiology",
    "心内科": "Cardiology",
    "心血管内科": "Cardiology",
    "urology": "Urology",
    "泌尿外科": "Urology",
    "neurology": "Neurology",
    "神经内科": "Neurology",
    "respiratory medicine": "Respiratory Medicine",
    "呼吸内科": "Respiratory Medicine",
    "nephrology": "Nephrology",
    "肾内科": "Nephrology",
    "dermatology": "Dermatology",
    "皮肤科": "Dermatology",
    "orthopedics": "Orthopedics",
    "骨科": "Orthopedics",
    "psychiatry": "Psychiatry",
    "精神科": "Psychiatry",
    "精神心理科": "Psychiatry",
    "general medicine": "General Medicine",
    "全科医学科": "General Medicine",
    "全科": "General Medicine",
    "dental": "Dental",
    "口腔科": "Dental",
    "cosmetic surgery": "Cosmetic Surgery",
    "整形外科": "Cosmetic Surgery",
    "美容外科": "Cosmetic Surgery",
}


def canonicalize_department(raw_department: str | None) -> str:
    if raw_department is None:
        return UNKNOWN
    value = str(raw_department).strip()
    if not value:
        return UNKNOWN
    normalized = " ".join(value.lower().split())
    if normalized in _ALIASES:
        return _ALIASES[normalized]
    compact = normalized.replace(" ", "")
    for alias, canonical in _ALIASES.items():
        if alias in compact or alias in normalized:
            return canonical
    return value


def canonicalize_departments(raw_departments: list[str] | tuple[str, ...] | None) -> list[str]:
    if not raw_departments:
        return []
    return [canonicalize_department(item) for item in raw_departments]
