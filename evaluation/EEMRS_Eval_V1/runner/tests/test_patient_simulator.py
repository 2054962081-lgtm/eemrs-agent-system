import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from adapters.consultation_adapter import ScriptedPatientSimulator


def assert_true(value, message):
    if not value:
        raise AssertionError(message)


def case_with(slots):
    return {
        "metadata": {"patient_behavior_primary": "COOPERATIVE"},
        "information_requirements": slots,
    }


def slot(canonical, expected, disclosure="ON_ASK", certainty="PRESENT"):
    return {
        "canonical_slot": canonical,
        "semantic_intent": "clarify_" + canonical,
        "accepted_question_intents": [],
        "acceptable_semantics": [],
        "expected_value": expected,
        "disclosure": disclosure,
        "patient_certainty": certainty,
    }


def test_single_slot_duration_disclosed():
    simulator = ScriptedPatientSimulator(case_with([slot("duration", "1天")]))

    reply = simulator.reply("腹痛大概持续多久了？")

    assert_true("1天" in reply, "duration should be disclosed when asked")


def test_multi_slot_discloses_all_matches():
    simulator = ScriptedPatientSimulator(case_with([
        slot("duration", "1天"),
        slot("progression", "逐渐加重"),
    ]))

    reply = simulator.reply("腹痛持续多长时间了？有没有逐渐加重？")

    assert_true("1天" in reply, "duration should be disclosed in multi-slot reply")
    assert_true("逐渐加重" in reply, "progression should be disclosed in multi-slot reply")


def test_three_slots_do_not_collapse_to_first_or_last():
    simulator = ScriptedPatientSimulator(case_with([
        slot("duration", "1天"),
        slot("progression", "逐渐加重"),
        slot("associated_symptoms", "否认明显伴随症状"),
    ]))

    reply = simulator.reply("持续多久？是否加重？有没有发热、呕吐、腹泻？")

    assert_true("1天" in reply, "duration should be included")
    assert_true("逐渐加重" in reply, "progression should be included")
    assert_true("否认明显伴随症状" in reply, "associated symptoms should be included")


def test_on_ask_not_asked_is_not_disclosed():
    simulator = ScriptedPatientSimulator(case_with([slot("duration", "1天")]))

    reply = simulator.reply("腹痛在哪里？")

    assert_true("1天" not in reply, "duration must not be disclosed when not asked")


def test_already_disclosed_is_not_repeated():
    simulator = ScriptedPatientSimulator(case_with([slot("duration", "1天")]))

    first = simulator.reply("持续多久？")
    second = simulator.reply("再说一下持续多久？")

    assert_true("1天" in first, "first answer should disclose duration")
    assert_true("1天" not in second, "second answer should not repeat duration")


def test_unknown_and_refuse_follow_policy():
    simulator = ScriptedPatientSimulator(case_with([
        slot("duration", "记不清", certainty="UNKNOWN"),
        slot("severity", "中等", disclosure="REFUSE"),
    ]))

    reply = simulator.reply("持续多久？疼痛程度几分？")

    assert_true("记不清" in reply, "unknown slot should render uncertainty")
    assert_true("不想回答" in reply, "refuse slot should render refusal")


def test_medication_history_does_not_imply_detail():
    simulator = ScriptedPatientSimulator(case_with([
        slot("medication_allergy_history", "已使用药物，需要追问药名和使用方式"),
        slot("medication_detail", "使用过非处方退热止痛药2，按说明书短期使用一次"),
    ]))

    reply = simulator.reply("有没有基础病，或正在服用止痛药、抗凝药？")

    assert_true("已使用药物" in reply, "generic medication use should disclose medication history")
    assert_true("非处方退热止痛药2" not in reply, "generic medication use must not disclose medication detail")


def main():
    test_single_slot_duration_disclosed()
    test_multi_slot_discloses_all_matches()
    test_three_slots_do_not_collapse_to_first_or_last()
    test_on_ask_not_asked_is_not_disclosed()
    test_already_disclosed_is_not_repeated()
    test_unknown_and_refuse_follow_policy()
    test_medication_history_does_not_imply_detail()
    print("Patient Simulator Unit Test PASS")


if __name__ == "__main__":
    main()
