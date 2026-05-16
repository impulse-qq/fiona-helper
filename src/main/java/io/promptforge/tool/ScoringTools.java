package io.promptforge.tool;

import io.promptforge.dto.ScoreResponse;
import io.promptforge.dto.ScoreSubmitResult;
import io.promptforge.service.SessionScoringService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;

import java.util.UUID;

public class ScoringTools {

    @Inject
    SessionScoringService scoringService;

    @io.quarkiverse.mcp.server.Tool(
        name = "submit_score",
        description = "对某次已完成的 session 提交评分(1-5)。同一 createdBy 重提交会覆盖原评分。"
    )
    public ScoreSubmitResult submitScore(String sessionId, Integer overallScore, String comment, String createdBy) {
        try {
            UUID sid = UUID.fromString(sessionId);
            return scoringService.submitScore(sid, overallScore, comment, createdBy);
        } catch (IllegalArgumentException e) {
            Log.warn("提交评分失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @io.quarkiverse.mcp.server.Tool(
        name = "get_score",
        description = "查询某 session 的所有评分(返回评分列表 + 平均分 + 评分人数)。"
    )
    public ScoreResponse getScore(String sessionId) {
        try {
            UUID sid = UUID.fromString(sessionId);
            return scoringService.getScore(sid);
        } catch (IllegalArgumentException e) {
            Log.warn("查询评分失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}
