package io.promptforge.tool;

import io.promptforge.entity.AssembleSessionEntity;
import io.promptforge.entity.SessionStatus;
import io.promptforge.repository.AssembleSessionRepository;
import io.promptforge.repository.SessionImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionImageToolsTest {

    @Mock
    AssembleSessionRepository sessionRepository;

    @Mock
    SessionImageRepository sessionImageRepository;

    @InjectMocks
    SessionImageTools tools;

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
}
