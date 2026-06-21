package io.promptforge.tool;

import io.promptforge.entity.AssembleSessionEntity;
import io.promptforge.entity.SessionImageEntity;
import io.promptforge.entity.SessionStatus;
import io.promptforge.repository.AssembleSessionRepository;
import io.promptforge.repository.SessionImageRepository;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class SessionImageTools {

    @Inject
    AssembleSessionRepository sessionRepository;

    @Inject
    SessionImageRepository sessionImageRepository;

    @Tool(name = "register_session_image",
          description = "登记图片文件名到已完成的 session。仅记录文件名，实际文件需单独上传。")
    @Transactional
    public String registerSessionImage(String sessionId, String filename) {
        UUID sid;
        try {
            sid = UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            Log.warn("登记 session 图片失败: " + e.getMessage());
            throw new RuntimeException("无效的 session ID: " + e.getMessage());
        }

        AssembleSessionEntity session = sessionRepository.findByIdOptional(sid).orElse(null);
        if (session == null) {
            throw new RuntimeException("session 不存在");
        }
        if (session.status != SessionStatus.COMPLETED) {
            throw new RuntimeException("session 未完成，不能登记图片");
        }

        if (filename == null || filename.isBlank()) {
            throw new RuntimeException("文件名不能为空");
        }

        String safeName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");

        SessionImageEntity image = new SessionImageEntity(sid, safeName);
        sessionImageRepository.persist(image);

        return image.getImageUrl();
    }
}
