package io.promptforge.service;

import io.promptforge.dto.ScoreItem;
import io.promptforge.dto.ScoreResponse;
import io.promptforge.dto.ScoreSubmitResult;
import io.promptforge.entity.AssembleSessionEntity;
import io.promptforge.entity.SessionScoreEntity;
import io.promptforge.entity.SessionStatus;
import io.promptforge.repository.AssembleSessionRepository;
import io.promptforge.repository.SessionScoreRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Session 粒度评分服务(M3a 引入)。
 *
 * CONTRACT: SessionScoringService only reads {@link AssembleSessionEntity#status} field.
 * 添加/删除/修改 AssembleSessionEntity 其他字段不应影响本服务的语义。
 *
 * 错误传播策略(plan-eng-review F4 决策):
 *   - 业务规则错误(参数越界、session 状态非 COMPLETED、createdBy 空)→ 软失败,返回 ScoreSubmitResult(success=false, ...)
 *   - UUID 解析错误等参数层错误 → 在 {@link io.promptforge.tool.ScoringTools} catch 后抛 RuntimeException
 *
 * 并发竞态(plan-eng-review F5 决策):
 *   - 同 (sessionId, createdBy) 并发提交可能触发 ConstraintViolationException → 500 透传,M3a 范围接受
 */
@ApplicationScoped
public class SessionScoringService {

    @Inject
    SessionScoreRepository scoreRepository;

    @Inject
    AssembleSessionRepository sessionRepository;

    @Transactional
    public ScoreSubmitResult submitScore(UUID sessionId, Integer overallScore, String comment, String createdBy) {
        if (createdBy == null || createdBy.isBlank()) {
            return new ScoreSubmitResult(false, "createdBy 不能为空", null, false);
        }
        if (overallScore == null) {
            return new ScoreSubmitResult(false, "overallScore 不能为空,必须在 1-5 范围", null, false);
        }
        if (overallScore < 1 || overallScore > 5) {
            return new ScoreSubmitResult(false,
                "overallScore 必须在 1-5 范围(当前: " + overallScore + ")", null, false);
        }

        AssembleSessionEntity session = sessionRepository.findByIdOptional(sessionId).orElse(null);
        if (session == null) {
            return new ScoreSubmitResult(false, "session 不存在", null, false);
        }

        if (session.status != SessionStatus.COMPLETED) {
            return new ScoreSubmitResult(false,
                "session 未完成,当前状态: " + session.status + ",需先完成所有 slot 后再评分",
                null, false);
        }

        Optional<SessionScoreEntity> existing = scoreRepository.findBySessionIdAndCreatedBy(sessionId, createdBy);
        if (existing.isPresent()) {
            SessionScoreEntity sc = existing.get();
            sc.overallScore = overallScore;
            sc.comment = comment;
            scoreRepository.persist(sc);
            return new ScoreSubmitResult(true, "评分已更新", sc.id, true);
        }

        SessionScoreEntity sc = new SessionScoreEntity(sessionId, overallScore, comment, createdBy);
        scoreRepository.persist(sc);
        return new ScoreSubmitResult(true, "评分已提交", sc.id, false);
    }

    public ScoreResponse getScore(UUID sessionId) {
        AssembleSessionEntity session = sessionRepository.findByIdOptional(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("session 不存在"));

        List<SessionScoreEntity> scores = scoreRepository.findBySessionId(sessionId);
        List<ScoreItem> items = scores.stream()
            .map(s -> new ScoreItem(s.id, s.overallScore, s.comment, s.createdBy, s.createdAt, s.updatedAt))
            .toList();

        Double avg = scores.isEmpty()
            ? null
            : Math.round(scores.stream().mapToInt(s -> s.overallScore).average().orElse(0.0) * 100.0) / 100.0;

        return new ScoreResponse(sessionId, items, avg, scores.size());
    }
}
