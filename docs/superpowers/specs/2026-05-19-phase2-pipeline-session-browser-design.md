# Phase 2: Pipeline-Session 产物浏览器 + Session 图片上传

## 1. 概述

Phase 2 在 Phase 1（角色卡片 Web UI）基础上，新增 **Pipeline 产物浏览器** 和 **Session 图片上传** 能力。

核心体验：用户在 Web UI 中浏览 Pipeline → 查看该 Pipeline 的所有已完成 Session（产物）→ 点进 Session 查看完整的组装 Prompt 内容和绑定的图片。

同时提供 MCP Tool，方便 AI Agent 将生成的图片直接上传到对应的 Session。

## 2. 背景与上下文

### 2.1 已有能力

- **M1-M3a**：Pipeline 内核、Slot 步进组装、评分反馈（MCP Tools）
- **Phase 1 Web UI**：角色卡片 CRUD + 头像上传
- **数据模型**：`Pipeline` → `Slot`（有序），`AssembleSession`（Pipeline 实例），`SlotPrompt`（Slot 填充内容），`SessionScore`（评分）

### 2.2 Phase 2 解决的问题

| 痛点 | Phase 2 解决方式 |
|------|-----------------|
| 看不到历史组装结果 | Pipeline 详情页展示所有 COMPLETED session |
| 不知道某个 session 组装出了什么 prompt | Session 详情页按 slot 顺序展示完整组装内容 |
| 生成的图片和 prompt 没有关联 | Session 图片上传，一张或多张图绑定到 session |
| AI Agent 生成图片后无法自动归档 | MCP Tool `upload_session_image` 让 Agent 直接上传 |

## 3. 设计目标

- **三层浏览**：Pipeline 列表 → Session 列表 → Session 详情（Prompt + 图片）
- **双通道上传**：Web UI 按钮上传 + MCP Tool 上传
- **图片一对多**：一个 Session 可以绑定多张图片（同 prompt 不同图）
- **只读为主**：Phase 2 不修改 Slot / Pipeline 定义，只浏览和上传图片

## 4. 数据模型

### 4.1 V7 Flyway 迁移

```sql
-- V7__add_session_image.sql
CREATE TABLE session_image (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES assemble_session(id) ON DELETE CASCADE,
    image_path VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_session_image_session ON session_image(session_id);
```

### 4.2 新增实体

```java
@Entity
@Table(name = "session_image")
public class SessionImageEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(name = "image_path", nullable = false, length = 512)
    public String imagePath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;
}
```

### 4.3 新增 Repository

```java
@ApplicationScoped
public class SessionImageRepository implements PanacheRepository<SessionImageEntity> {
    public List<SessionImageEntity> findBySessionId(UUID sessionId) {
        return find("sessionId", sessionId).list();
    }
}
```

## 5. REST API 设计

### 5.1 PipelineResource（新增只读）

```
GET /api/pipelines              → List<PipelineSummary>
GET /api/pipelines/{id}         → PipelineDetail
GET /api/pipelines/{id}/sessions → List<SessionSummary>  (status=COMPLETED only)
```

**SessionSummary**：
```java
public record SessionSummary(
    UUID id,
    UUID pipelineId,
    UUID characterId,
    String characterName,    // join story_character
    SessionStatus status,
    Instant createdAt,
    int imageCount
) {}
```

### 5.2 SessionResource（新增只读 + 图片上传）

```
GET /api/sessions/{id}          → SessionDetail
GET /api/sessions/{id}/images   → List<ImageItem>
POST /api/sessions/{id}/images  → multipart upload, returns imageUrl
```

**SessionDetail**：
```java
public record SessionDetail(
    UUID id,
    UUID pipelineId,
    String pipelineName,
    String worldSetting,
    UUID characterId,
    String characterName,
    SessionStatus status,
    List<SlotPromptItem> slots,    // 按 orderIndex 排序
    List<ImageItem> images,
    Instant createdAt
) {}

public record SlotPromptItem(
    UUID slotId,
    String slotName,
    int orderIndex,
    String content       // SlotPromptEntity.content
) {}

public record ImageItem(
    UUID id,
    String imageUrl
) {}
```

### 5.3 SessionImageResource（图片服务）

```
GET /api/session-images/{sessionId}/{filename} → image file
```

路径遍历防护同 `ImageResource.getImage`（`Path.normalize() + startsWith(baseDir)`）。

## 6. MCP Tool 设计

### 6.1 `upload_session_image`

```java
@Tool(name = "upload_session_image",
      description = "上传图片到已完成的 session。支持 Agent 将生成的图片归档到对应 session。")
public String uploadSessionImage(String sessionId, FileUpload imageFile)
```

**校验**：
- `sessionId` 是合法 UUID
- session 存在且 `status == COMPLETED`
- 文件类型为 `image/*`
- 文件大小 ≤ 5MB

**行为**：同 REST 上传，保存到 `uploads/sessions/{sessionId}/{timestamp}_{filename}`，返回图片 URL。

## 7. 前端组件设计

```
App.vue
├── CharacterGrid.vue          (Phase 1 已有)
├── PipelineList.vue             # 新增: Pipeline 卡片网格
│   └── PipelineCard.vue
├── SessionList.vue              # 新增: 某 Pipeline 的 Session 列表
│   └── SessionCard.vue          # 显示 characterName + createdAt + imageCount
└── SessionDetail.vue            # 新增: Session 详情
    ├── SlotPromptList.vue       # 按 orderIndex 展示 slot name + content
    └── SessionImageGallery.vue  # 图片缩略图网格 + 上传按钮
```

### 7.1 PipelineCard.vue

- 卡片样式同 CharacterCard（保持视觉一致）
- 显示：name, worldSetting, slot 数量
- 点击 → 进入 SessionList

### 7.2 SessionCard.vue

- 显示：characterName（或"无角色"）, createdAt, imageCount（图片数量角标）
- 点击 → 进入 SessionDetail

### 7.3 SessionDetail.vue

**上半部分：SlotPrompt 组装内容**
- 按 `orderIndex` 顺序列出每个 slot
- 显示 slot name + 填充的 content
- 整体 Prompt 拼接预览（可选折叠）

**下半部分：图片画廊**
- 缩略图网格（同头像的圆形/方形展示）
- 点击放大查看
- 上传按钮（`input type=file`，多文件选择）
- 上传进度/状态提示

## 8. 文件存储

沿用 Phase 1 的本地文件系统策略：

```
${user.home}/.fiona-helper/uploads/
├── characters/{characterId}/     # 角色头像 (Phase 1)
└── sessions/{sessionId}/         # Session 产物图片 (Phase 2)
```

## 9. 测试策略

### 9.1 后端单元测试

- `SessionImageRepositoryTest`：findBySessionId
- `SessionResourceTest`（REST-assured）：
  - `getSession_success_returnsDetailWithSlots`
  - `getSession_notFound_returns404`
  - `uploadImage_success`
  - `uploadImage_notImage_rejects`
  - `uploadImage_sessionNotCompleted_rejects`

### 9.2 MCP Tool 测试

- `SessionImageToolsTest`：
  - `uploadSessionImage_success`
  - `uploadSessionImage_invalidUuid_throws`
  - `uploadSessionImage_sessionNotCompleted_rejects`

## 10. 范围明确排除

| 功能 | 排除理由 |
|------|----------|
| Slot 增删改（编辑 Pipeline 结构） | Phase 3 实现，涉及 orderIndex 调整和外键约束 |
| 评分 UI（星标展示） | Phase 3 实现，与 Slot 编辑一起 |
| Session 删除 | Phase 1 设计已明确不提供删除接口，保持 |
| 图片删除 | Phase 2 范围排除，如需可在 Phase 3 追加 |
| 图片元数据（caption、标签） | 最小实现，需要时后续扩展 |

## 11. 实施任务估算

| # | 任务 | 工作量 |
|---|------|--------|
| 1 | V7 Flyway 迁移 + SessionImageEntity + Repository | 0.25d |
| 2 | SessionResource（SessionDetail + 图片上传） | 0.5d |
| 3 | PipelineResource（列表 + sessions 子查询） | 0.25d |
| 4 | SessionImageResource（图片服务） | 0.25d |
| 5 | MCP Tool `upload_session_image` | 0.25d |
| 6 | 后端单元测试 | 0.5d |
| 7 | Vue 前端：PipelineList + SessionList + SessionDetail | 0.5d |
| 8 | 端到端验证 | 0.25d |
|   | **合计** | **~3 天** |
