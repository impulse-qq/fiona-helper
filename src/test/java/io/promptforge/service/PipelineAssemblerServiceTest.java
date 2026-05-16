package io.promptforge.service;

import io.promptforge.dto.AssembleResult;
import io.promptforge.dto.CharacterSettingResponse;
import io.promptforge.dto.InsertResult;
import io.promptforge.dto.SessionResponse;
import io.promptforge.dto.SlotResponse;
import io.promptforge.dto.WorldSettingResponse;
import io.promptforge.entity.AssembleSessionEntity;
import io.promptforge.entity.CharacterEntity;
import io.promptforge.entity.ConstraintType;
import io.promptforge.entity.PipelineEntity;
import io.promptforge.entity.SessionStatus;
import io.promptforge.entity.SlotDraftEntity;
import io.promptforge.entity.SlotEntity;
import io.promptforge.repository.AssembleSessionRepository;
import io.promptforge.repository.CharacterRepository;
import io.promptforge.repository.PipelineRepository;
import io.promptforge.repository.SlotDraftRepository;
import io.promptforge.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineAssemblerServiceTest {

    @Mock PipelineRepository pipelineRepository;
    @Mock SlotRepository slotRepository;
    @Mock CharacterRepository characterRepository;
    @Mock AssembleSessionRepository sessionRepository;
    @Mock SlotDraftRepository slotDraftRepository;

    @InjectMocks PipelineAssemblerService service;

    private UUID pipelineId;
    private PipelineEntity pipeline;

    @BeforeEach
    void setUp() {
        pipelineId = UUID.randomUUID();
        pipeline = new PipelineEntity("test-pipeline", "desc");
        pipeline.id = pipelineId;
        pipeline.worldSetting = "赛博朋克";

        // sessionRepository.persist 给 session 分配 id
        doAnswer(inv -> {
            AssembleSessionEntity s = inv.getArgument(0);
            if (s.id == null) {
                s.id = UUID.randomUUID();
            }
            return null;
        }).when(sessionRepository).persist(any(AssembleSessionEntity.class));

        // slotDraftRepository.persist 给 draft 分配 id
        doAnswer(inv -> {
            SlotDraftEntity d = inv.getArgument(0);
            if (d.id == null) {
                d.id = UUID.randomUUID();
            }
            return null;
        }).when(slotDraftRepository).persist(any(SlotDraftEntity.class));
    }

    private SlotEntity slot(String name, int order, ConstraintType type, String def) {
        SlotEntity s = new SlotEntity(name, order, type, def);
        s.id = UUID.randomUUID();
        s.description = name + " 描述";
        return s;
    }

    private AssembleSessionEntity sessionFor(UUID pipeId, int idx, SessionStatus status) {
        AssembleSessionEntity s = new AssembleSessionEntity();
        s.id = UUID.randomUUID();
        s.pipelineId = pipeId;
        s.currentSlotIndex = idx;
        s.status = status;
        return s;
    }

    // ---------------- createSession ----------------

    @Nested
    @DisplayName("createSession")
    class CreateSessionTests {

        @Test
        @DisplayName("Pipeline 不存在抛异常")
        void pipelineNotFound() {
            UUID unknown = UUID.randomUUID();
            when(pipelineRepository.findByIdOptional(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createSession(unknown, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Pipeline 不存在");
        }

        @Test
        @DisplayName("传入 characterId 但 character 不存在抛异常")
        void characterNotFound() {
            UUID charId = UUID.randomUUID();
            when(pipelineRepository.findByIdOptional(pipelineId)).thenReturn(Optional.of(pipeline));
            when(characterRepository.findByIdOptional(charId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createSession(pipelineId, charId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("角色不存在");
        }

        @Test
        @DisplayName("Pipeline 无 Slot 抛 IllegalStateException")
        void pipelineWithNoSlots() {
            when(pipelineRepository.findByIdOptional(pipelineId)).thenReturn(Optional.of(pipeline));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.createSession(pipelineId, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Pipeline 没有配置任何 Slot");
        }

        @Test
        @DisplayName("正常创建,首 Slot 为 FREE,返回 CREATED 状态")
        void createWithFreeFirst() {
            SlotEntity s1 = slot("角色", 0, ConstraintType.FREE, null);
            SlotEntity s2 = slot("服装", 1, ConstraintType.FREE, null);
            when(pipelineRepository.findByIdOptional(pipelineId)).thenReturn(Optional.of(pipeline));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(s1, s2));

            SessionResponse resp = service.createSession(pipelineId, null);

            assertThat(resp.sessionId()).isNotNull();
            assertThat(resp.status()).isEqualTo(SessionStatus.CREATED);
            assertThat(resp.firstSlot()).isNotNull();
            assertThat(resp.firstSlot().name()).isEqualTo("角色");
            assertThat(resp.nextStep()).isNotNull();
        }

        @Test
        @DisplayName("首 Slot 是 FIXED,自动跳过推进到第一个 FREE")
        void createAutoSkipsFixedSlots() {
            SlotEntity sFixed = slot("固定", 0, ConstraintType.FIXED, "fixed-value");
            SlotEntity sFree = slot("自由", 1, ConstraintType.FREE, null);
            when(pipelineRepository.findByIdOptional(pipelineId)).thenReturn(Optional.of(pipeline));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(sFixed, sFree));

            SessionResponse resp = service.createSession(pipelineId, null);

            assertThat(resp.status()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(resp.firstSlot().name()).isEqualTo("自由");
            // 应该已经为 FIXED slot 持久化了 draft
            verify(slotDraftRepository, times(1)).persist(any(SlotDraftEntity.class));
        }

        @Test
        @DisplayName("全部 FIXED 时直接 COMPLETED")
        void createAllFixedDirectlyCompleted() {
            SlotEntity f1 = slot("固定1", 0, ConstraintType.FIXED, "v1");
            SlotEntity f2 = slot("固定2", 1, ConstraintType.FIXED, "v2");
            when(pipelineRepository.findByIdOptional(pipelineId)).thenReturn(Optional.of(pipeline));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(f1, f2));

            SessionResponse resp = service.createSession(pipelineId, null);

            assertThat(resp.status()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(resp.firstSlot()).isNull();
            verify(slotDraftRepository, times(2)).persist(any(SlotDraftEntity.class));
        }
    }

    // ---------------- insertSlotValue ----------------

    @Nested
    @DisplayName("insertSlotValue")
    class InsertSlotValueTests {

        @Test
        @DisplayName("worldSetting 为空时拒绝")
        void rejectsEmptyWorldSetting() {
            UUID sid = UUID.randomUUID();
            UUID slotId = UUID.randomUUID();

            InsertResult r1 = service.insertSlotValue(sid, slotId, "v", null);
            InsertResult r2 = service.insertSlotValue(sid, slotId, "v", "  ");

            assertThat(r1.success()).isFalse();
            assertThat(r1.message()).contains("get_world_setting");
            assertThat(r2.success()).isFalse();
        }

        @Test
        @DisplayName("value 为 null 拒绝")
        void rejectsNullValue() {
            InsertResult r = service.insertSlotValue(UUID.randomUUID(), UUID.randomUUID(), null, "world");
            assertThat(r.success()).isFalse();
            assertThat(r.message()).contains("value 不能为空");
        }

        @Test
        @DisplayName("value 超过 500 字符拒绝")
        void rejectsTooLongValue() {
            String tooLong = "x".repeat(501);
            InsertResult r = service.insertSlotValue(UUID.randomUUID(), UUID.randomUUID(), tooLong, "world");
            assertThat(r.success()).isFalse();
            assertThat(r.message()).contains("500 字符");
        }

        @Test
        @DisplayName("会话不存在抛异常")
        void sessionNotFound() {
            UUID sid = UUID.randomUUID();
            when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.insertSlotValue(sid, UUID.randomUUID(), "v", "world"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("会话已 COMPLETED 时拒绝")
        void rejectsWhenAlreadyCompleted() {
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.COMPLETED);
            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));

            InsertResult r = service.insertSlotValue(sess.id, UUID.randomUUID(), "v", "world");
            assertThat(r.success()).isFalse();
            assertThat(r.message()).contains("会话已完成");
        }

        @Test
        @DisplayName("跳步: slotId 不是当前 slot 时拒绝")
        void rejectsOutOfOrderSlot() {
            SlotEntity s1 = slot("first", 0, ConstraintType.FREE, null);
            SlotEntity s2 = slot("second", 1, ConstraintType.FREE, null);
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.CREATED);

            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(s1, s2));

            // 尝试填第二个 slot
            InsertResult r = service.insertSlotValue(sess.id, s2.id, "value", "world");

            assertThat(r.success()).isFalse();
            assertThat(r.message()).contains("first");
        }

        @Test
        @DisplayName("重复填充已完成的 slot 时返回提示并指向下一个")
        void rejectsAlreadyFilled() {
            SlotEntity s1 = slot("first", 0, ConstraintType.FREE, null);
            SlotEntity s2 = slot("second", 1, ConstraintType.FREE, null);
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.IN_PROGRESS);

            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(s1, s2));
            when(slotDraftRepository.findBySessionIdAndSlotId(sess.id, s1.id))
                    .thenReturn(Optional.of(new SlotDraftEntity(sess.id, s1.id, "已填的值")));

            InsertResult r = service.insertSlotValue(sess.id, s1.id, "新值", "world");

            assertThat(r.success()).isFalse();
            assertThat(r.message()).contains("first").contains("second");
        }

        @Test
        @DisplayName("成功填充推进到下一个 slot")
        void successfulInsertAdvancesIndex() {
            SlotEntity s1 = slot("first", 0, ConstraintType.FREE, null);
            SlotEntity s2 = slot("second", 1, ConstraintType.FREE, null);
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.CREATED);

            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(s1, s2));
            when(slotDraftRepository.findBySessionIdAndSlotId(sess.id, s1.id))
                    .thenReturn(Optional.empty());

            InsertResult r = service.insertSlotValue(sess.id, s1.id, "角色描述", "赛博朋克");

            assertThat(r.success()).isTrue();
            assertThat(r.sessionStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(r.nextStep()).isNotNull();
            assertThat(r.nextStep().slotName()).isEqualTo("second");
            assertThat(sess.currentSlotIndex).isEqualTo(1);
        }

        @Test
        @DisplayName("成功填充后下一个为 FIXED 时自动跳过")
        void successfulInsertAutoSkipsFollowingFixed() {
            SlotEntity s1 = slot("free1", 0, ConstraintType.FREE, null);
            SlotEntity sFixed = slot("fixed", 1, ConstraintType.FIXED, "fixed-default");
            SlotEntity s3 = slot("free2", 2, ConstraintType.FREE, null);
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.CREATED);

            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(s1, sFixed, s3));
            when(slotDraftRepository.findBySessionIdAndSlotId(sess.id, s1.id)).thenReturn(Optional.empty());

            InsertResult r = service.insertSlotValue(sess.id, s1.id, "v1", "赛博朋克");

            assertThat(r.success()).isTrue();
            assertThat(sess.currentSlotIndex).isEqualTo(2); // 跳过了 FIXED
            assertThat(r.nextStep().slotName()).isEqualTo("free2");
            // free1 的 draft + fixed 的 draft = 2 次 persist
            verify(slotDraftRepository, times(2)).persist(any(SlotDraftEntity.class));
        }

        @Test
        @DisplayName("最后一个 slot 填充后状态 COMPLETED")
        void completesOnLastSlot() {
            SlotEntity s1 = slot("only", 0, ConstraintType.FREE, null);
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.CREATED);

            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(s1));
            when(slotDraftRepository.findBySessionIdAndSlotId(sess.id, s1.id)).thenReturn(Optional.empty());

            InsertResult r = service.insertSlotValue(sess.id, s1.id, "v", "world");

            assertThat(r.success()).isTrue();
            assertThat(r.sessionStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(r.nextStep()).isNull();
            assertThat(sess.status).isEqualTo(SessionStatus.COMPLETED);
        }
    }

    // ---------------- assemblePrompt ----------------

    @Nested
    @DisplayName("assemblePrompt")
    class AssemblePromptTests {

        @Test
        @DisplayName("会话不存在抛异常")
        void sessionNotFound() {
            UUID sid = UUID.randomUUID();
            when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assemblePrompt(sid))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Pipeline 无 Slot 时失败")
        void emptyPipelineFails() {
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.COMPLETED);
            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(Collections.emptyList());

            AssembleResult r = service.assemblePrompt(sess.id);
            assertThat(r.success()).isFalse();
        }

        @Test
        @DisplayName("会话未完成时失败")
        void incompleteSessionFails() {
            SlotEntity s1 = slot("first", 0, ConstraintType.FREE, null);
            SlotEntity s2 = slot("second", 1, ConstraintType.FREE, null);
            AssembleSessionEntity sess = sessionFor(pipelineId, 1, SessionStatus.IN_PROGRESS);

            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(s1, s2));

            AssembleResult r = service.assemblePrompt(sess.id);

            assertThat(r.success()).isFalse();
            assertThat(r.message()).contains("second");
        }

        @Test
        @DisplayName("成功组装: 按 orderIndex 顺序空格拼接")
        void successAssemblesInOrder() {
            SlotEntity s1 = slot("first", 0, ConstraintType.FREE, null);
            SlotEntity s2 = slot("second", 1, ConstraintType.FREE, null);
            SlotEntity s3 = slot("third", 2, ConstraintType.FREE, null);
            AssembleSessionEntity sess = sessionFor(pipelineId, 3, SessionStatus.COMPLETED);

            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(s1, s2, s3));

            List<SlotDraftEntity> drafts = new ArrayList<>();
            drafts.add(new SlotDraftEntity(sess.id, s1.id, "角色描述"));
            drafts.add(new SlotDraftEntity(sess.id, s2.id, "服装描述"));
            drafts.add(new SlotDraftEntity(sess.id, s3.id, "场景描述"));
            when(slotDraftRepository.findBySessionId(sess.id)).thenReturn(drafts);

            AssembleResult r = service.assemblePrompt(sess.id);

            assertThat(r.success()).isTrue();
            assertThat(r.prompt()).isEqualTo("角色描述 服装描述 场景描述");
        }

        @Test
        @DisplayName("支持 8 个 Slot 的 Pipeline (M1 验收标准)")
        void supportsEightSlots() {
            List<SlotEntity> slots = new ArrayList<>();
            List<SlotDraftEntity> drafts = new ArrayList<>();
            UUID sid = UUID.randomUUID();
            for (int i = 0; i < 8; i++) {
                SlotEntity sl = slot("slot" + i, i, ConstraintType.FREE, null);
                slots.add(sl);
                drafts.add(new SlotDraftEntity(sid, sl.id, "v" + i));
            }
            AssembleSessionEntity sess = sessionFor(pipelineId, 8, SessionStatus.COMPLETED);
            sess.id = sid;

            when(sessionRepository.findByIdOptional(sid)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(slots);
            when(slotDraftRepository.findBySessionId(sid)).thenReturn(drafts);

            AssembleResult r = service.assemblePrompt(sid);

            assertThat(r.success()).isTrue();
            assertThat(r.prompt()).isEqualTo("v0 v1 v2 v3 v4 v5 v6 v7");
        }
    }

    // ---------------- read-only helpers ----------------

    @Nested
    @DisplayName("read-only 工具")
    class ReadOnlyTests {

        @Test
        @DisplayName("getWorldSetting 返回 Pipeline 的 worldSetting")
        void getWorldSettingReturnsValue() {
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.CREATED);
            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(pipelineRepository.findByIdOptional(pipelineId)).thenReturn(Optional.of(pipeline));

            WorldSettingResponse r = service.getWorldSetting(sess.id);

            assertThat(r.worldSetting()).isEqualTo("赛博朋克");
        }

        @Test
        @DisplayName("getCharacterSetting 返回 null 字段当无关联角色")
        void getCharacterSettingNullCharacter() {
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.CREATED);
            sess.characterId = null;
            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));

            CharacterSettingResponse r = service.getCharacterSetting(sess.id);

            assertThat(r.characterId()).isNull();
            assertThat(r.name()).isNull();
        }

        @Test
        @DisplayName("getCharacterSetting 返回角色字段")
        void getCharacterSettingWithCharacter() {
            UUID charId = UUID.randomUUID();
            CharacterEntity c = new CharacterEntity("Ghost", "短发, 义肢", "冷静");
            c.id = charId;
            AssembleSessionEntity sess = sessionFor(pipelineId, 0, SessionStatus.CREATED);
            sess.characterId = charId;

            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(characterRepository.findByIdOptional(charId)).thenReturn(Optional.of(c));

            CharacterSettingResponse r = service.getCharacterSetting(sess.id);

            assertThat(r.name()).isEqualTo("Ghost");
            assertThat(r.baseDesign()).contains("义肢");
            assertThat(r.personality()).isEqualTo("冷静");
        }

        @Test
        @DisplayName("getSlot 返回当前 slot 信息 + 进度 + 下一步")
        void getSlotReturnsProgress() {
            SlotEntity s1 = slot("first", 0, ConstraintType.FREE, null);
            SlotEntity s2 = slot("second", 1, ConstraintType.FREE, null);
            AssembleSessionEntity sess = sessionFor(pipelineId, 1, SessionStatus.IN_PROGRESS);

            when(sessionRepository.findByIdOptional(sess.id)).thenReturn(Optional.of(sess));
            when(slotRepository.findByPipelineId(pipelineId)).thenReturn(List.of(s1, s2));
            when(slotDraftRepository.findBySessionId(sess.id))
                    .thenReturn(List.of(new SlotDraftEntity(sess.id, s1.id, "已填值")));

            SlotResponse r = service.getSlot(sess.id, s2.id);

            assertThat(r.slot().name()).isEqualTo("second");
            assertThat(r.progress().isCurrent()).isTrue();
            assertThat(r.progress().completedCount()).isEqualTo(1);
            assertThat(r.progress().totalCount()).isEqualTo(2);
            assertThat(r.completedSlots()).hasSize(1);
            assertThat(r.nextStep()).isNotNull();
        }
    }
}
