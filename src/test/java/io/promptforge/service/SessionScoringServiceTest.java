package io.promptforge.service;

import io.promptforge.dto.ScoreResponse;
import io.promptforge.dto.ScoreSubmitResult;
import io.promptforge.entity.AssembleSessionEntity;
import io.promptforge.entity.SessionScoreEntity;
import io.promptforge.entity.SessionStatus;
import io.promptforge.repository.AssembleSessionRepository;
import io.promptforge.repository.SessionScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionScoringServiceTest {

    @Mock SessionScoreRepository scoreRepository;
    @Mock AssembleSessionRepository sessionRepository;

    @InjectMocks SessionScoringService service;

    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();

        // scoreRepository.persist 给 score 分配 id
        doAnswer(inv -> {
            SessionScoreEntity s = inv.getArgument(0);
            if (s.id == null) {
                s.id = UUID.randomUUID();
            }
            return null;
        }).when(scoreRepository).persist(any(SessionScoreEntity.class));
    }

    private AssembleSessionEntity sessionWithStatus(SessionStatus status) {
        AssembleSessionEntity s = new AssembleSessionEntity();
        s.id = sessionId;
        s.pipelineId = UUID.randomUUID();
        s.status = status;
        return s;
    }

    // ---------- submitScore happy paths ----------

    @Test
    void submitScore_firstTime_success() {
        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.COMPLETED)));
        when(scoreRepository.findBySessionIdAndCreatedBy(sessionId, "user-A"))
            .thenReturn(Optional.empty());

        ScoreSubmitResult r = service.submitScore(sessionId, 5, "very good", "user-A");

        assertThat(r.success()).isTrue();
        assertThat(r.isUpdate()).isFalse();
        assertThat(r.scoreId()).isNotNull();
        verify(scoreRepository).persist(any(SessionScoreEntity.class));
    }

    @Test
    void submitScore_sameCreatedBy_updates() {
        SessionScoreEntity existing = new SessionScoreEntity(sessionId, 3, "old", "user-A");
        existing.id = UUID.randomUUID();
        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.COMPLETED)));
        when(scoreRepository.findBySessionIdAndCreatedBy(sessionId, "user-A"))
            .thenReturn(Optional.of(existing));

        ScoreSubmitResult r = service.submitScore(sessionId, 5, "new", "user-A");

        assertThat(r.success()).isTrue();
        assertThat(r.isUpdate()).isTrue();
        assertThat(r.scoreId()).isEqualTo(existing.id);
        assertThat(existing.overallScore).isEqualTo(5);
        assertThat(existing.comment).isEqualTo("new");
    }

    @Test
    void submitScore_differentCreatedBy_inserts() {
        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.COMPLETED)));
        when(scoreRepository.findBySessionIdAndCreatedBy(sessionId, "user-B"))
            .thenReturn(Optional.empty());

        ScoreSubmitResult r = service.submitScore(sessionId, 4, null, "user-B");

        assertThat(r.success()).isTrue();
        assertThat(r.isUpdate()).isFalse();
        verify(scoreRepository).persist(any(SessionScoreEntity.class));
    }

    // ---------- submitScore validation rejects ----------

    @Test
    void submitScore_overallScoreZero_rejects() {
        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.COMPLETED)));

        ScoreSubmitResult r = service.submitScore(sessionId, 0, null, "user-A");

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("1-5");
        verify(scoreRepository, never()).persist(any(SessionScoreEntity.class));
    }

    @Test
    void submitScore_overallScoreSix_rejects() {
        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.COMPLETED)));

        ScoreSubmitResult r = service.submitScore(sessionId, 6, null, "user-A");

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("1-5");
        verify(scoreRepository, never()).persist(any(SessionScoreEntity.class));
    }

    /** G1: overall null 拒绝 */
    @Test
    void submitScore_overallScoreNull_rejects() {
        ScoreSubmitResult r = service.submitScore(sessionId, null, null, "user-A");

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("不能为空").contains("1-5");
        verify(scoreRepository, never()).persist(any(SessionScoreEntity.class));
    }

    @Test
    void submitScore_createdByNull_rejects() {
        ScoreSubmitResult r = service.submitScore(sessionId, 5, null, null);

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("createdBy");
        verify(scoreRepository, never()).persist(any(SessionScoreEntity.class));
    }

    @Test
    void submitScore_createdByEmpty_rejects() {
        ScoreSubmitResult r = service.submitScore(sessionId, 5, null, "");

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("createdBy");
        verify(scoreRepository, never()).persist(any(SessionScoreEntity.class));
    }

    /** G2: createdBy 仅空白 拒绝 */
    @Test
    void submitScore_createdByBlank_rejects() {
        ScoreSubmitResult r = service.submitScore(sessionId, 5, null, "   ");

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("createdBy");
        verify(scoreRepository, never()).persist(any(SessionScoreEntity.class));
    }

    @Test
    void submitScore_sessionNotFound_rejects() {
        when(sessionRepository.findByIdOptional(sessionId)).thenReturn(Optional.empty());

        ScoreSubmitResult r = service.submitScore(sessionId, 5, null, "user-A");

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("session 不存在");
        verify(scoreRepository, never()).persist(any(SessionScoreEntity.class));
    }

    @Test
    void submitScore_sessionStatusCreated_rejects() {
        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.CREATED)));

        ScoreSubmitResult r = service.submitScore(sessionId, 5, null, "user-A");

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("未完成").contains("CREATED");
        verify(scoreRepository, never()).persist(any(SessionScoreEntity.class));
    }

    /** G3: session.status=IN_PROGRESS 拒绝 */
    @Test
    void submitScore_sessionStatusInProgress_rejects() {
        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.IN_PROGRESS)));

        ScoreSubmitResult r = service.submitScore(sessionId, 5, null, "user-A");

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("未完成").contains("IN_PROGRESS");
        verify(scoreRepository, never()).persist(any(SessionScoreEntity.class));
    }

    // ---------- getScore ----------

    @Test
    void getScore_emptyList_returnsNullAvg() {
        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.COMPLETED)));
        when(scoreRepository.findBySessionId(sessionId)).thenReturn(List.of());

        ScoreResponse r = service.getScore(sessionId);

        assertThat(r.sessionId()).isEqualTo(sessionId);
        assertThat(r.scores()).isEmpty();
        assertThat(r.avgScore()).isNull();
        assertThat(r.scoreCount()).isZero();
    }

    /** G4: 单条评分边界 */
    @Test
    void getScore_singleScore_returnsExactAvg() {
        SessionScoreEntity s = new SessionScoreEntity(sessionId, 4, "ok", "user-A");
        s.id = UUID.randomUUID();
        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.COMPLETED)));
        when(scoreRepository.findBySessionId(sessionId)).thenReturn(List.of(s));

        ScoreResponse r = service.getScore(sessionId);

        assertThat(r.scoreCount()).isEqualTo(1);
        assertThat(r.avgScore()).isEqualTo(4.0);
        assertThat(r.scores()).hasSize(1);
    }

    @Test
    void getScore_multipleScores_returnsAvg() {
        SessionScoreEntity s1 = new SessionScoreEntity(sessionId, 5, null, "user-A");
        SessionScoreEntity s2 = new SessionScoreEntity(sessionId, 4, null, "user-B");
        SessionScoreEntity s3 = new SessionScoreEntity(sessionId, 3, null, "user-C");
        s1.id = UUID.randomUUID();
        s2.id = UUID.randomUUID();
        s3.id = UUID.randomUUID();

        when(sessionRepository.findByIdOptional(sessionId))
            .thenReturn(Optional.of(sessionWithStatus(SessionStatus.COMPLETED)));
        when(scoreRepository.findBySessionId(sessionId)).thenReturn(List.of(s1, s2, s3));

        ScoreResponse r = service.getScore(sessionId);

        assertThat(r.scoreCount()).isEqualTo(3);
        assertThat(r.avgScore()).isEqualTo(4.0);
        assertThat(r.scores()).hasSize(3);
    }

    @Test
    void getScore_sessionNotFound_throws() {
        when(sessionRepository.findByIdOptional(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getScore(sessionId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("session 不存在");
    }
}
