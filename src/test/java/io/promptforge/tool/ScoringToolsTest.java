package io.promptforge.tool;

import io.promptforge.service.SessionScoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoringToolsTest {

    @Mock SessionScoringService scoringService;

    @InjectMocks ScoringTools tools;

    /** G5: submit_score UUID 非法 → IllegalArgumentException catch → RuntimeException */
    @Test
    void submitScore_invalidUuid_throwsRuntimeException() {
        assertThatThrownBy(() -> tools.submitScore("not-a-uuid", 5, null, "user-A"))
            .isInstanceOf(RuntimeException.class);
    }

    /** G5: get_score UUID 非法 → IllegalArgumentException catch → RuntimeException */
    @Test
    void getScore_invalidUuid_throwsRuntimeException() {
        assertThatThrownBy(() -> tools.getScore("bad-uuid"))
            .isInstanceOf(RuntimeException.class);
    }
}
