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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@ApplicationScoped
public class SessionImageTools {

    @Inject
    AssembleSessionRepository sessionRepository;

    @Inject
    SessionImageRepository sessionImageRepository;

    @ConfigProperty(name = "app.upload.dir")
    String uploadDir;

    @Tool(name = "upload_session_image",
          description = "上传图片到已完成的 session。将 AI 生成的图片归档到对应组装会话。")
    @Transactional
    public String uploadSessionImage(String sessionId, FileUpload imageFile) {
        try {
            UUID sid = UUID.fromString(sessionId);
            AssembleSessionEntity session = sessionRepository.findByIdOptional(sid).orElse(null);
            if (session == null) {
                throw new RuntimeException("session 不存在");
            }
            if (session.status != SessionStatus.COMPLETED) {
                throw new RuntimeException("session 未完成，不能上传图片");
            }

            if (imageFile == null || imageFile.filePath() == null) {
                throw new RuntimeException("图片文件不能为空");
            }

            String contentType = imageFile.contentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new RuntimeException("只支持图片文件");
            }

            long size = Files.size(imageFile.filePath());
            if (size == 0) {
                throw new RuntimeException("文件不能为空");
            }
            if (size > 5L * 1024 * 1024) {
                throw new RuntimeException("文件大小不能超过 5MB");
            }

            String originalName = imageFile.fileName();
            if (originalName == null || originalName.isBlank()) {
                originalName = "image";
            }
            String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName = System.currentTimeMillis() + "_" + safeName;

            java.nio.file.Path targetDir = java.nio.file.Path.of(uploadDir).resolve("sessions").resolve(sid.toString());
            java.nio.file.Path targetPath = targetDir.resolve(fileName);

            Files.createDirectories(targetDir);
            Files.copy(imageFile.filePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            SessionImageEntity image = new SessionImageEntity(sid, targetPath.toString());
            sessionImageRepository.persist(image);

            return "/api/session-images/" + sid + "/" + fileName;
        } catch (IllegalArgumentException e) {
            Log.warn("上传 session 图片失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            Log.warn("保存 session 图片失败: " + e.getMessage());
            throw new RuntimeException("保存文件失败: " + e.getMessage());
        }
    }
}
