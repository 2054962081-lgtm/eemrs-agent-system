from typing import Any, Dict, List


class ScriptedPatientSimulator:
    def __init__(self, case: Dict[str, Any]):
        self.case = case
        self.answered: set[str] = set()
        self.off_topic_used = False

    def match_slots(self, question: str) -> List[Dict[str, Any]]:
        text = (question or "").lower()
        matched = []
        for slot in self.case["information_requirements"]:
            canonical = slot["canonical_slot"]
            if canonical in self.answered or slot.get("disclosure") == "SPONTANEOUS":
                continue
            cues = [
                canonical,
                slot["semantic_intent"],
                *slot.get("accepted_question_intents", []),
                *slot.get("acceptable_semantics", []),
                *_slot_aliases(canonical),
            ]
            if any(str(cue).lower() in text or str(cue) in question for cue in cues if cue):
                matched.append(slot)
        return matched

    def reply(self, agent_question: str) -> str:
        behavior = self.case["metadata"].get("patient_behavior_primary")
        if behavior == "OFF_TOPIC" and not self.off_topic_used:
            self.off_topic_used = True
            return "我还想问一下挂号停车和排队时间，不过症状还是刚才说的那些。"
        slots = self.match_slots(agent_question)
        if not slots:
            return "这个我暂时没有更多补充。"
        disclosures = []
        for slot in slots:
            self.answered.add(slot["canonical_slot"])
            if slot.get("disclosure") == "REFUSE":
                disclosures.append("这个我暂时不想回答。")
            elif slot.get("patient_certainty") == "UNKNOWN":
                disclosures.append("这个我记不清，也不太确定。")
            else:
                disclosures.append(str(slot.get("expected_value", "已提供相关信息")))
        return "；".join(disclosures)


def _slot_aliases(canonical: str) -> List[str]:
    aliases = {
        "duration": ["多久", "多长时间", "持续时间", "开始时间", "从什么时候开始", "什么时候开始"],
        "severity": ["严重", "程度", "几分", "疼痛程度"],
        "progression": ["加重", "减轻", "变化", "进展"],
        "associated_symptoms": ["伴随", "有没有发烧", "有没有发热", "呕吐", "腹泻", "便血", "黑便"],
        "special_population": ["年龄", "怀孕", "孕", "月经", "老人", "儿童"],
        "medication_allergy_history": ["有没有用药", "是否用药", "有没有吃药", "正在服用", "过敏"],
        "medication_detail": ["什么药", "药名", "用量", "怎么用", "如何服用", "使用方式", "吃的什么药"],
    }
    return aliases.get(canonical, [])


def to_initial_payload(case: Dict[str, Any], session_id: str, round_no: int, history: List[Dict[str, str]]) -> Dict[str, Any]:
    return {
        "mode": case["metadata"].get("mode", "QUICK").lower(),
        "sessionId": session_id,
        "question": case["initial_user_input"]["text"] if round_no == 1 else history[-1]["content"],
        "round": round_no,
        "history": history[:-1],
        "memoryContext": None,
    }
