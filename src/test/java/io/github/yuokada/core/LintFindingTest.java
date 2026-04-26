package io.github.yuokada.core;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class LintFindingTest {

    @Test
    void everyKnownRuleHasHintAndFix() {
        List<String> ruleIds = List.of(
            "W001", "W002", "W003", "W004", "W005", "W006", "W007",
            "E001", "E002", "E003", "E004", "E005");

        for (String ruleId : ruleIds) {
            LintFinding finding = new LintFinding(ruleId, LintFinding.Severity.WARNING, "message");
            assertFalse(finding.getHint().isBlank(), "hint should be populated for " + ruleId);
            assertFalse(finding.getFix().isBlank(), "fix should be populated for " + ruleId);
        }
    }
}
