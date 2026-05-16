package io.promptforge.service;

import io.promptforge.dto.*;
import io.promptforge.entity.*;
import io.promptforge.repository.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PipelineAssemblerService {

    @Inject
    PipelineRepository pipelineRepository;

    @Inject
    SlotRepository slotRepository;

    @Inject
    CharacterRepository characterRepository;

    @Inject
    AssembleSessionRepository sessionRepository;

    @Inject
    SlotDraftRepository slotDraftRepository;

    @Transactional
    public SessionResponse createSession(UUID pipelineId, UUID characterId) {
        Optional<PipelineEntity> pipelineOpt = pipelineRepository.findByIdOptional(pipelineId);
        if (pipelineOpt.isEmpty()) {
            throw new IllegalArgumentException("Pipeline 不存在");
        }

        if (characterId != null) {
            Optional<CharacterEntity> characterOpt = characterRepository.findByIdOptional(characterId);
            if (characterOpt.isEmpty()) {
                throw new IllegalArgumentException("角色不存在");
            }
        }

        AssembleSessionEntity session = new AssembleSessionEntity();
        session.pipelineId = pipelineId;
        session.characterId = characterId;
        session.status = SessionStatus.CREATED;
        session.currentSlotIndex = 0;
        sessionRepository.persist(session);

        List<SlotEntity> slots = slotRepository.findByPipelineId(pipelineId);
        if (slots.isEmpty()) {
            throw new IllegalStateException("Pipeline 没有配置任何 Slot");
        }

        // FIXED slots are auto-completed
        int firstFreeIndex = 0;
        for (int i = 0; i < slots.size(); i++) {
            SlotEntity slot = slots.get(i);
            if (slot.constraintType == ConstraintType.FIXED) {
                SlotDraftEntity draft = new SlotDraftEntity(session.id, slot.id, slot.defaultValue != null ? slot.defaultValue : "");
                slotDraftRepository.persist(draft);
                session.currentSlotIndex = i + 1;
                firstFreeIndex = i + 1;
            } else {
                break;
            }
        }

        if (session.currentSlotIndex >= slots.size()) {
            session.status = SessionStatus.COMPLETED;
        } else if (session.currentSlotIndex > 0) {
            session.status = SessionStatus.IN_PROGRESS;
        }
        sessionRepository.persist(session);

        if (session.status == SessionStatus.COMPLETED) {
            return new SessionResponse(
                    session.id,
                    session.status,
                    null,
                    null,
                    "所有 Slot 均为 FIXED，已自动完成，请调用 assemble_prompt"
            );
        }

        SlotEntity firstSlot = slots.get(firstFreeIndex);
        SlotInfo firstSlotInfo = toSlotInfo(firstSlot);
        NextStep nextStep = toNextStep(firstSlot);

        return new SessionResponse(
                session.id,
                session.status,
                firstSlotInfo,
                nextStep,
                "请先调用 get_world_setting 获取世界观，调用 get_character_setting 获取当前角色基础设计"
        );
    }

    public WorldSettingResponse getWorldSetting(UUID sessionId) {
        AssembleSessionEntity session = sessionRepository.findByIdOptional(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));

        PipelineEntity pipeline = pipelineRepository.findByIdOptional(session.pipelineId)
                .orElseThrow(() -> new IllegalStateException("Pipeline 数据异常"));

        return new WorldSettingResponse(pipeline.worldSetting);
    }

    public CharacterSettingResponse getCharacterSetting(UUID sessionId) {
        AssembleSessionEntity session = sessionRepository.findByIdOptional(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));

        if (session.characterId == null) {
            return new CharacterSettingResponse(null, null, null, null);
        }

        CharacterEntity character = characterRepository.findByIdOptional(session.characterId)
                .orElseThrow(() -> new IllegalStateException("角色数据异常"));

        return new CharacterSettingResponse(character.id, character.name, character.baseDesign, character.personality);
    }

    public SlotResponse getSlot(UUID sessionId, UUID slotId) {
        AssembleSessionEntity session = sessionRepository.findByIdOptional(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));

        List<SlotEntity> slots = slotRepository.findByPipelineId(session.pipelineId);
        SlotEntity targetSlot = slots.stream()
                .filter(s -> s.id.equals(slotId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Slot 不存在"));

        List<SlotDraftEntity> drafts = slotDraftRepository.findBySessionId(sessionId);
        Map<UUID, SlotDraftEntity> draftBySlotId = new HashMap<>();
        for (SlotDraftEntity draft : drafts) {
            draftBySlotId.put(draft.slotId, draft);
        }

        boolean isCurrent = session.currentSlotIndex < slots.size()
                && slots.get(session.currentSlotIndex).id.equals(slotId);

        int completedCount = drafts.size();
        int totalCount = slots.size();
        String currentSlotName = session.currentSlotIndex < slots.size()
                ? slots.get(session.currentSlotIndex).name : null;

        List<CompletedSlot> completedSlots = new ArrayList<>();
        for (SlotEntity slot : slots) {
            SlotDraftEntity draft = draftBySlotId.get(slot.id);
            if (draft != null) {
                completedSlots.add(new CompletedSlot(slot.id, slot.name, slot.orderIndex, draft.value));
            }
        }

        NextStep nextStep = null;
        if (session.currentSlotIndex < slots.size()) {
            SlotEntity nextSlot = slots.get(session.currentSlotIndex);
            nextStep = toNextStep(nextSlot);
        }

        return new SlotResponse(
                toSlotInfo(targetSlot),
                new ProgressInfo(isCurrent, completedCount, totalCount, currentSlotName),
                completedSlots,
                nextStep
        );
    }

    private static final int MAX_VALUE_LENGTH = 500;

    @Transactional
    public InsertResult insertSlotValue(UUID sessionId, UUID slotId, String value, String worldSetting) {
        long startNanos = System.nanoTime();
        if (worldSetting == null || worldSetting.isBlank()) {
            return new InsertResult(false, "请先调用 get_world_setting 获取世界观，并在提交时显式传入", null, null);
        }

        if (value == null) {
            return new InsertResult(false, "value 不能为空", null, null);
        }

        if (value.length() > MAX_VALUE_LENGTH) {
            return new InsertResult(false,
                    String.format("value 长度不能超过 %d 字符（当前 %d 字符）", MAX_VALUE_LENGTH, value.length()),
                    null, null);
        }

        AssembleSessionEntity session = sessionRepository.findByIdOptional(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));

        if (session.status == SessionStatus.COMPLETED) {
            return new InsertResult(false, "会话已完成，无法继续填充", SessionStatus.COMPLETED, null);
        }

        List<SlotEntity> slots = slotRepository.findByPipelineId(session.pipelineId);
        if (slots.isEmpty()) {
            return new InsertResult(false, "Pipeline 没有配置任何 Slot", session.status, null);
        }

        if (session.currentSlotIndex >= slots.size()) {
            session.status = SessionStatus.COMPLETED;
            return new InsertResult(false, "所有 Slot 已完成", SessionStatus.COMPLETED, null);
        }

        SlotEntity currentSlot = slots.get(session.currentSlotIndex);

        if (!slotId.equals(currentSlot.id)) {
            return new InsertResult(false,
                    String.format("请先完成 '%s'（%s）", currentSlot.name, currentSlot.description != null ? currentSlot.description : ""),
                    session.status, toNextStep(currentSlot));
        }

        // Check if already filled
        Optional<SlotDraftEntity> existingDraft = slotDraftRepository.findBySessionIdAndSlotId(sessionId, slotId);
        if (existingDraft.isPresent()) {
            if (session.currentSlotIndex + 1 < slots.size()) {
                SlotEntity nextSlot = slots.get(session.currentSlotIndex + 1);
                return new InsertResult(false,
                        String.format("'%s' 已完成，请继续 '%s'", currentSlot.name, nextSlot.name),
                        session.status, toNextStep(nextSlot));
            } else {
                return new InsertResult(false, "'" + currentSlot.name + "' 已完成，请调用 assemble_prompt", SessionStatus.COMPLETED, null);
            }
        }

        SlotDraftEntity draft = new SlotDraftEntity(sessionId, slotId, value);
        slotDraftRepository.persist(draft);

        session.currentSlotIndex++;

        // Auto-skip FIXED slots
        while (session.currentSlotIndex < slots.size()
                && slots.get(session.currentSlotIndex).constraintType == ConstraintType.FIXED) {
            SlotEntity fixedSlot = slots.get(session.currentSlotIndex);
            SlotDraftEntity fixedDraft = new SlotDraftEntity(sessionId, fixedSlot.id,
                    fixedSlot.defaultValue != null ? fixedSlot.defaultValue : "");
            slotDraftRepository.persist(fixedDraft);
            session.currentSlotIndex++;
        }

        if (session.currentSlotIndex >= slots.size()) {
            session.status = SessionStatus.COMPLETED;
            sessionRepository.persist(session);
            logLatency("insertSlotValue", startNanos, sessionId,
                    "slot=" + currentSlot.name + " status=COMPLETED");
            return new InsertResult(true, currentSlot.name + " 已保存", SessionStatus.COMPLETED, null);
        }

        session.status = SessionStatus.IN_PROGRESS;
        sessionRepository.persist(session);

        SlotEntity nextSlot = slots.get(session.currentSlotIndex);
        logLatency("insertSlotValue", startNanos, sessionId,
                "slot=" + currentSlot.name + " next=" + nextSlot.name);
        return new InsertResult(true, currentSlot.name + " 已保存", SessionStatus.IN_PROGRESS, toNextStep(nextSlot));
    }

    public AssembleResult assemblePrompt(UUID sessionId) {
        long startNanos = System.nanoTime();
        AssembleSessionEntity session = sessionRepository.findByIdOptional(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));

        List<SlotEntity> slots = slotRepository.findByPipelineId(session.pipelineId);
        if (slots.isEmpty()) {
            return AssembleResult.failure("Pipeline 没有配置任何 Slot");
        }

        if (session.status != SessionStatus.COMPLETED) {
            SlotEntity currentSlot = slots.get(session.currentSlotIndex);
            return AssembleResult.failure(
                    String.format("请先完成 '%s'（%s）", currentSlot.name, currentSlot.description != null ? currentSlot.description : ""));
        }

        List<SlotDraftEntity> drafts = slotDraftRepository.findBySessionId(sessionId);
        StringBuilder promptBuilder = new StringBuilder();

        for (int i = 0; i < slots.size(); i++) {
            SlotEntity slot = slots.get(i);
            final int index = i;
            SlotDraftEntity draft = drafts.stream()
                    .filter(d -> d.slotId.equals(slot.id))
                    .findFirst()
                    .orElse(null);

            if (draft != null) {
                if (index > 0) {
                    promptBuilder.append(" ");
                }
                promptBuilder.append(draft.value);
            }
        }

        String prompt = promptBuilder.toString().trim();
        logLatency("assemblePrompt", startNanos, sessionId,
                "slots=" + slots.size() + " promptLen=" + prompt.length());
        return AssembleResult.success(prompt);
    }

    private static void logLatency(String op, long startNanos, UUID sessionId, String extra) {
        long elapsedMicros = (System.nanoTime() - startNanos) / 1_000L;
        Log.infof("op=%s sessionId=%s elapsedMicros=%d %s", op, sessionId, elapsedMicros, extra);
    }

    private SlotInfo toSlotInfo(SlotEntity slot) {
        return new SlotInfo(
                slot.id,
                slot.name,
                slot.orderIndex,
                slot.constraintType,
                slot.defaultValue,
                slot.wordLimit
        );
    }

    private NextStep toNextStep(SlotEntity slot) {
        return new NextStep(slot.id, slot.name, slot.description);
    }
}
