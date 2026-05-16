package io.promptforge.tool;

import io.promptforge.dto.*;
import io.promptforge.service.PipelineAssemblerService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;

import java.util.UUID;

public class PipelineAssemblerTools {

    @Inject
    PipelineAssemblerService assemblerService;

    @io.quarkiverse.mcp.server.Tool(name = "create_session", description = "创建一个新的 Pipeline 组装会话，返回 sessionId 和第一个需要填充的 Slot 信息")
    public SessionResponse createSession(String pipelineId, String characterId) {
        try {
            UUID pid = UUID.fromString(pipelineId);
            UUID cid = characterId != null && !characterId.isBlank() ? UUID.fromString(characterId) : null;
            return assemblerService.createSession(pid, cid);
        } catch (IllegalArgumentException e) {
            Log.warn("创建会话失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @io.quarkiverse.mcp.server.Tool(name = "get_world_setting", description = "获取当前 Pipeline 的世界观设定，作为全局上下文供所有 Slot 参考")
    public WorldSettingResponse getWorldSetting(String sessionId) {
        try {
            return assemblerService.getWorldSetting(UUID.fromString(sessionId));
        } catch (IllegalArgumentException e) {
            Log.warn("获取世界观失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @io.quarkiverse.mcp.server.Tool(name = "get_character_setting", description = "获取当前 Session 关联角色的基础设计/人设，作为角色全局上下文")
    public CharacterSettingResponse getCharacterSetting(String sessionId) {
        try {
            return assemblerService.getCharacterSetting(UUID.fromString(sessionId));
        } catch (IllegalArgumentException e) {
            Log.warn("获取角色设定失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @io.quarkiverse.mcp.server.Tool(name = "get_slot", description = "查询指定 Slot 的信息，包含当前进度、已完成 Slot 列表和下一步指引")
    public SlotResponse getSlot(String sessionId, String slotId) {
        try {
            return assemblerService.getSlot(UUID.fromString(sessionId), UUID.fromString(slotId));
        } catch (IllegalArgumentException e) {
            Log.warn("获取 Slot 失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @io.quarkiverse.mcp.server.Tool(name = "insert_slot_value", description = "填充当前 Slot 的值。必须显式传入世界观以证明已了解背景。服务端会严格按顺序控制流程，禁止跳步")
    public InsertResult insertSlotValue(String sessionId, String slotId, String value, String worldSetting) {
        try {
            return assemblerService.insertSlotValue(
                    UUID.fromString(sessionId),
                    UUID.fromString(slotId),
                    value,
                    worldSetting
            );
        } catch (IllegalArgumentException e) {
            Log.warn("插入 Slot 值失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @io.quarkiverse.mcp.server.Tool(name = "assemble_prompt", description = "最终组装：按顺序拼接所有已填充的 Slot 值为完整 Prompt。只有所有 Slot 完成后才能调用")
    public AssembleResult assemblePrompt(String sessionId) {
        try {
            return assemblerService.assemblePrompt(UUID.fromString(sessionId));
        } catch (IllegalArgumentException e) {
            Log.warn("组装 Prompt 失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @io.quarkiverse.mcp.server.Tool(name = "list_pipelines", description = "列出可用 Pipeline 摘要 (id, name, description, worldSetting)。limit 默认 50,最大 200")
    public java.util.List<io.promptforge.dto.PipelineSummary> listPipelines(Integer limit, Integer offset) {
        int lim = limit != null ? limit : 50;
        int off = offset != null ? offset : 0;
        return assemblerService.listPipelines(lim, off);
    }

    @io.quarkiverse.mcp.server.Tool(name = "get_pipeline", description = "获取 Pipeline 详情含所有 Slot 的有序定义 (slot 的 description 是给 Agent 的填写指引)")
    public io.promptforge.dto.PipelineDetail getPipeline(String pipelineId) {
        try {
            return assemblerService.getPipelineDetail(UUID.fromString(pipelineId));
        } catch (IllegalArgumentException e) {
            Log.warn("获取 Pipeline 详情失败: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}
