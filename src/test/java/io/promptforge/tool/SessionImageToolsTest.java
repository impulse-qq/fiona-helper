package io.promptforge.tool;

import io.promptforge.entity.AssembleSessionEntity;
import io.promptforge.entity.SessionImageEntity;
import io.promptforge.entity.SessionStatus;
import io.promptforge.repository.AssembleSessionRepository;
import io.promptforge.repository.SessionImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionImageToolsTest {

    @Mock
    AssembleSessionRepository sessionRepository;

    @Mock
    SessionImageRepository sessionImageRepository;

    @InjectMocks
    SessionImageTools tools;

    @Test
    void registerSessionImage_invalidUuid_throwsRuntimeException() {
        assertThatThrownBy(() -> tools.registerSessionImage("not-a-uuid", "image.png"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无效的 session ID");
    }

    @Test
    void registerSessionImage_sessionNotFound_throwsRuntimeException() {
        UUID sid = UUID.randomUUID();
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.registerSessionImage(sid.toString(), "image.png"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("session 不存在");
    }

    @Test
    void registerSessionImage_sessionNotCompleted_throwsRuntimeException() {
        UUID sid = UUID.randomUUID();
        AssembleSessionEntity session = new AssembleSessionEntity();
        session.id = sid;
        session.status = SessionStatus.IN_PROGRESS;
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> tools.registerSessionImage(sid.toString(), "image.png"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("session 未完成");
    }

    @Test
    void registerSessionImage_emptyFilename_throwsRuntimeException() {
        UUID sid = UUID.randomUUID();
        AssembleSessionEntity session = new AssembleSessionEntity();
        session.id = sid;
        session.status = SessionStatus.COMPLETED;
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> tools.registerSessionImage(sid.toString(), ""))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件名不能为空");
    }

    @Test
    void registerSessionImage_nullFilename_throwsRuntimeException() {
        UUID sid = UUID.randomUUID();
        AssembleSessionEntity session = new AssembleSessionEntity();
        session.id = sid;
        session.status = SessionStatus.COMPLETED;
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> tools.registerSessionImage(sid.toString(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("文件名不能为空");
    }

    @Test
    void registerSessionImage_success() {
        UUID sid = UUID.randomUUID();
        AssembleSessionEntity session = new AssembleSessionEntity();
        session.id = sid;
        session.status = SessionStatus.COMPLETED;
        when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.of(session));

        String result = tools.registerSessionImage(sid.toString(), "image.png");

        assertThat(result).startsWith("/api/session-images/" + sid + "/");
        assertThat(result).endsWith("image.png");

        ArgumentCaptor<SessionImageEntity> captor = ArgumentCaptor.forClass(SessionImageEntity.class);
        verify(sessionImageRepository).persist(captor.capture());
        assertThat(captor.getValue().sessionId).isEqualTo(sid);
        assertThat(captor.getValue().imagePath).isEqualTo("image.png");
    }
}
