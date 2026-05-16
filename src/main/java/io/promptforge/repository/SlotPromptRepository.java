package io.promptforge.repository;

import io.promptforge.entity.SlotPromptEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SlotPromptRepository implements PanacheRepository<SlotPromptEntity> {

    /** 查询某次 session 的所有 slot_prompt(用于 assemblePrompt 拼接) */
    public List<SlotPromptEntity> findBySessionId(UUID sessionId) {
        return find("sessionId", sessionId).list();
    }

    /** 查询某次 session 在某 slot 的 prompt(用于 insertSlotValue 重复检查) */
    public Optional<SlotPromptEntity> findBySessionIdAndSlotId(UUID sessionId, UUID slotId) {
        return find("sessionId = ?1 AND slotId = ?2", sessionId, slotId).firstResultOptional();
    }

    /**
     * 查询某 slot 的预设内容(session_id IS NULL),用于 FIXED 自动跳过时填充。
     * 优先匹配指定 character_id 的预设,否则取 character_id IS NULL 的通用预设。
     */
    public Optional<SlotPromptEntity> findPresetBySlotId(UUID slotId, UUID characterId) {
        if (characterId != null) {
            Optional<SlotPromptEntity> charSpecific = find(
                    "slotId = ?1 AND characterId = ?2 AND sessionId IS NULL",
                    slotId, characterId).firstResultOptional();
            if (charSpecific.isPresent()) {
                return charSpecific;
            }
        }
        return find("slotId = ?1 AND characterId IS NULL AND sessionId IS NULL", slotId)
                .firstResultOptional();
    }
}
