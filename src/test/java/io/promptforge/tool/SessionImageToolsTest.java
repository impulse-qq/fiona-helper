package io.promptforge.tool;

import io.promptforge.entity.AssembleSessionEntity;
import io.promptforge.entity.SessionImageEntity;
import io.promptforge.entity.SessionStatus;
import io.promptforge.repository.AssembleSessionRepository;
import io.promptforge.repository.SessionImageRepository;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionImageToolsTest {

    @Mock
    AssembleSessionRepository sessionRepository;

    @Mock
    SessionImageRepository sessionImageRepository;

    @Mock
    FileUpload fileUpload;

    @InjectMocks
    SessionImageTools tools;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        Field uploadDirField = SessionImageTools.class.getDeclaredField("uploadDir");
        uploadDirField.setAccessible(true);
        uploadDirField.set(tools, tempDir.toString());
    }

    @Test
    void uploadSessionImage_invalidUuid_throwsRuntimeException() {
        assertThatThrownBy(() -> tools.uploadSessionImage("not-a-uuid", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not-a-uuid");
    }

    @Test
    void uploadSessionImage_sessionNotFound_throwsRuntimeException() {
        UUID sid = UUID.randomUUID();
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.uploadSessionImage(sid.toString(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("session 不存在");
    }

    @Test
    void uploadSessionImage_sessionNotCompleted_throwsRuntimeException() {
        UUID sid = UUID.randomUUID();
        AssembleSessionEntity session = new AssembleSessionEntity();
        session.id = sid;
        session.status = SessionStatus.IN_PROGRESS;
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> tools.uploadSessionImage(sid.toString(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("session 未完成");
    }

    @Test
    void uploadSessionImage_success() throws Exception {
        UUID sid = UUID.randomUUID();
        AssembleSessionEntity session = new AssembleSessionEntity();
        session.id = sid;
        session.status = SessionStatus.COMPLETED;
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.of(session));

        Path tempFile = tempDir.resolve("test.png");
        Files.write(tempFile, "fake-image".getBytes());

        when(fileUpload.filePath()).thenReturn(tempFile);
        when(fileUpload.contentType()).thenReturn("image/png");
        when(fileUpload.fileName()).thenReturn("test.png");

        String result = tools.uploadSessionImage(sid.toString(), fileUpload);

        assertThat(result).startsWith("/api/session-images/" + sid + "/");

        ArgumentCaptor<SessionImageEntity> captor = ArgumentCaptor.forClass(SessionImageEntity.class);
        verify(sessionImageRepository).persist(captor.capture());
        assertThat(captor.getValue().sessionId).isEqualTo(sid);
    }

    @Test
    void uploadSessionImage_notImage_rejects() {
        UUID sid = UUID.randomUUID();
        AssembleSessionEntity session = new AssembleSessionEntity();
        session.id = sid;
        session.status = SessionStatus.COMPLETED;
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.of(session));

        when(fileUpload.filePath()).thenReturn(tempDir.resolve("dummy"));
        when(fileUpload.contentType()).thenReturn("application/pdf");

        assertThatThrownBy(() -> tools.uploadSessionImage(sid.toString(), fileUpload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("只支持图片文件");
    }

    @Test
    void uploadSessionImage_emptyFile_rejects() throws Exception {
        UUID sid = UUID.randomUUID();
        AssembleSessionEntity session = new AssembleSessionEntity();
        session.id = sid;
        session.status = SessionStatus.COMPLETED;
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.of(session));

        Path tempFile = tempDir.resolve("empty.png");
        Files.createFile(tempFile);

        when(fileUpload.filePath()).thenReturn(tempFile);
        when(fileUpload.contentType()).thenReturn("image/png");

        assertThatThrownBy(() -> tools.uploadSessionImage(sid.toString(), fileUpload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件不能为空");
    }
}
