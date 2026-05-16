# M3a 评分反馈系统设计文档

## 1. 概述

M3a 在已完成的 M1（Pipeline 内核 + MCP 步进式组装）和 M2（slot_prompt 统一池）之上,为 **assemble_session 粒度的最终输出** 引入评分反馈机制。

通过新增 2 个 MCP 工具(`submit_score` / `get_score`),允许 Agent 或外部调用方对一次已完成的组装会话提交 1-5 分整体评分,并按 `created_by` 维度去重——同一调用方对同一 session 重复评分时,行为为覆盖更新。

M3a **不包含** 组件推荐、多模型适配(M3b)、细粒度 slot_prompt 评分。这些范围在前期讨论中显式排除。

## 2. 背景与上下文

### 2.1 与原 M3a 设计的偏离

原 office-hours design 文档(`impulse-unknown-design-20260516-123125.md`)中,M3a 评分关联的是 `prompt_result` 表:

```sql
prompt_score (
  prompt_result_id UUID NOT NULL REFERENCES prompt_result(id),
  overall_score INT,
  consistency_score INT,
  ...
)
```

但实际项目演进中:
- M1 阶段 `prompt_result` 从未实现,被 `assemble_session + slot_draft` 替代;
- M2 阶段 `slot_draft` 又被 `slot_prompt` 替代(V4 迁移)。

当前系统的"输出资产"实际是:
1. **`assemble_session`**:整次组装会话(粗粒度,对应"这次出的图整体感觉如何");
2. **`slot_prompt`**:单个 slot 的内容(细粒度,可被未来 session 复用)。

### 2.2 M3a 范围决策

经讨论确认:

| 维度 | 决策 | 理由 |
|------|------|------|
| 评分粒度 | 只评 `assemble_session`(粗粒度) | 贴近原设计意图(对最终产物打分),接口简单 |
| 组件推荐 | 不实现 | 推荐算法定义不明确,缺少使用数据;先收集评分作为基础 |
| 评分维度 | 只评 `overall` 总分 | 二维评分(overall + consistency)接口复杂度收益低 |
| 重复评分 | 按 `created_by` 去重(覆盖) | 同一 Agent/用户多次提交=修改;不同 created_by 各算一票 |
| M3b 多模型 | 不实现 | 当前业务场景仅需自然语言输出 |

## 3. 设计目标

- **接入成本极低**:新增 2 个 MCP 工具,Agent 调用单参数列表 ≤4 个
- **评分不污染原数据**:`assemble_session` / `slot_prompt` 保持不可变,评分独立表
- **支持多评分人**:`created_by` 维度允许同一 session 收集多份评分,聚合得到平均分
- **重提交语义清晰**:同一 `created_by` 覆盖,而非追加历史
- **拒绝未完成评分**:session 状态必须为 `COMPLETED` 才允许评分

## 4. 数据模型

### 4.1 新增表 `session_score`

```sql
-- V5__add_session_score.sql
CREATE TABLE session_score (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID NOT NULL REFERENCES assemble_session(id) ON DELETE CASCADE,
    overall_score INT  NOT NULL CHECK (overall_score BETWEEN 1 AND 5),
    comment       TEXT,
    created_by    VARCHAR(64) NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at    TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (session_id, created_by)
);

CREATE INDEX idx_session_score_session ON session_score(session_id);
```

要点:
- `session_id` 上 `ON DELETE CASCADE`,即使 M1 未提供删除接口也保持语义正确;
- `UNIQUE (session_id, created_by)` 由 DB 强制去重,Service 层先 find 再 update/insert 配合;
- M3a 不预留 `overall_score` 单独索引(plan-eng-review Step 0 决策:严格最小,避免死代码;未来加 `list_low_scored_sessions` 时再随 V6 补)。

### 4.2 新增实体 `SessionScoreEntity`

```java
@Entity
@Table(name = "session_score")
public class SessionScoreEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(name = "overall_score", nullable = false)
    public int overallScore;

    @Column(name = "comment", columnDefinition = "TEXT")
    public String comment;

    @Column(name = "created_by", nullable = false, length = 64)
    public String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;

    public SessionScoreEntity() {}
}
```

### 4.3 Repository

```java
@ApplicationScoped
public class SessionScoreRepository implements PanacheRepository<SessionScoreEntity> {

    /** 按 (session, createdBy) 查询(用于覆盖重提交) */
    public Optional<SessionScoreEntity> findBySessionIdAndCreatedBy(UUID sessionId, String createdBy) {
        return find("sessionId = ?1 AND createdBy = ?2", sessionId, createdBy).firstResultOptional();
    }

    /** 查询某 session 的所有评分(用于 get_score 聚合) */
    public List<SessionScoreEntity> findBySessionId(UUID sessionId) {
        return find("sessionId", sessionId).list();
    }
}
```

## 5. 服务层

### 5.1 `SessionScoringService`

独立服务,不污染 `PipelineAssemblerService`。

**与 `AssembleSessionRepository` 共享契约**:`SessionScoringService` **仅读取 `AssembleSessionEntity.status` 字段** 做 COMPLETED 校验,不依赖其他字段。这层契约通过代码注释 `// CONTRACT: SessionScoringService only reads .status` 标记于 service 文件头,以防止后续修改 AssembleSessionEntity 时误伤评分模块。

**错误传播策略**:与 M1 `PipelineAssemblerService.insertSlotValue` 一致 —— **业务规则错误一律走软失败**,返回 `ScoreSubmitResult{success:false, message:"..."}`。仅 UUID 解析错误等"前置参数错误"在 `ScoringTools` 层 catch `IllegalArgumentException` 后抛 `RuntimeException`(MCP 层会转成协议错误)。

```java
@ApplicationScoped
public class SessionScoringService {

    // CONTRACT: SessionScoringService only reads AssembleSessionEntity.status field.
    // Adding/removing other fields on AssembleSessionEntity should NOT affect this service.

    @Inject SessionScoreRepository scoreRepository;
    @Inject AssembleSessionRepository sessionRepository;

    /**
     * 提交评分。同一 createdBy 重提交 → 覆盖;不同 createdBy → 新增。
     * 校验:
     *   - createdBy 非空非空白
     *   - overallScore 在 [1,5]
     *   - session 存在且 status == COMPLETED
     */
    @Transactional
    public ScoreSubmitResult submitScore(UUID sessionId, int overallScore,
                                         String comment, String createdBy) { /* ... */ }

    /**
     * 查询某 session 的所有评分 + 聚合统计。
     * 返回 { sessionId, scores: [...], avgScore, scoreCount }。
     */
    public ScoreResponse getScore(UUID sessionId) { /* ... */ }
}
```

### 5.2 submitScore 行为表

| 场景 | overallScore | createdBy | session 状态 | 已存在评分? | 行为 |
|------|--------------|-----------|--------------|-------------|------|
| 正常首次评分 | 1-5 | 非空 | COMPLETED | 否 | INSERT,返回 success=true, isUpdate=false |
| 同 createdBy 重提交 | 1-5 | 非空 | COMPLETED | 是 | UPDATE overall/comment/updated_at,返回 isUpdate=true |
| 不同 createdBy 提交 | 1-5 | 非空 | COMPLETED | 否(对当前 createdBy) | INSERT 新记录 |
| 越界分数 | 0 / 6 / null | 任意 | 任意 | 任意 | 拒绝,返回 success=false |
| 空 createdBy | 1-5 | 空或 null | 任意 | 任意 | 拒绝,返回 success=false |
| session 不存在 | 1-5 | 非空 | - | - | 拒绝,返回 success=false |
| session 未完成 | 1-5 | 非空 | CREATED / IN_PROGRESS | 任意 | 拒绝,返回 success=false |

### 5.3 getScore 行为

- session 不存在 → 抛 `IllegalArgumentException`
- session 无评分 → `{ scores: [], avgScore: null, scoreCount: 0 }`
- session 有 N 条评分 → `{ scores: [...], avgScore: <平均值,保留 2 位小数>, scoreCount: N }`

## 6. MCP 工具

新建 `ScoringTools.java` 类(与 `PipelineAssemblerTools` 并列):

```java
public class ScoringTools {

    @Inject SessionScoringService scoringService;

    @Tool(name = "submit_score",
          description = "对某次已完成的 session 提交评分(1-5)。同一 createdBy 重提交会覆盖原评分。")
    public ScoreSubmitResult submitScore(String sessionId,
                                          Integer overallScore,
                                          String comment,
                                          String createdBy) { /* ... */ }

    @Tool(name = "get_score",
          description = "查询某 session 的所有评分(返回评分列表 + 平均分 + 评分人数)。")
    public ScoreResponse getScore(String sessionId) { /* ... */ }
}
```

参数说明:
- `sessionId`: 必填,UUID 字符串
- `overallScore`: 必填,1-5 整数
- `comment`: 可选,自由文本(无长度上限,DB 为 TEXT)
- `createdBy`: 必填,评分人标识(由 Agent 自报,M1 阶段无认证强校验)

## 7. DTO

```java
public record ScoreSubmitResult(
    boolean success,
    String message,
    UUID scoreId,    // 创建/更新后的评分 ID
    boolean isUpdate // true=覆盖更新,false=首次创建
) {}

public record ScoreItem(
    UUID id,
    int overallScore,
    String comment,
    String createdBy,
    Instant createdAt,
    Instant updatedAt
) {}

public record ScoreResponse(
    UUID sessionId,
    List<ScoreItem> scores,
    Double avgScore,    // null 当 scoreCount == 0
    int scoreCount
) {}
```

## 8. 测试范围

`SessionScoringServiceTest` + `ScoringToolsTest` 使用 Mockito,沿用 M1 的纯单元测试模式(不依赖 Testcontainers)。**plan-eng-review Section 3 决策**:补全 5 个边界 GAP,从 10 个测试扩展到 15 个。

### SessionScoringServiceTest (13 个方法)

| 测试方法 | 场景 | 来源 |
|----------|------|------|
| `submitScore_firstTime_success` | 正常首次评分 | 原始 |
| `submitScore_sameCreatedBy_updates` | 同 createdBy 重提交 → 覆盖,isUpdate=true | 原始 |
| `submitScore_differentCreatedBy_inserts` | 不同 createdBy → 各自新增 | 原始 |
| `submitScore_overallScoreZero_rejects` | overall=0 拒绝 | 原始 |
| `submitScore_overallScoreSix_rejects` | overall=6 拒绝 | 原始 |
| `submitScore_overallScoreNull_rejects` | **G1 补**:overall=null 拒绝(MCP Integer 包装可能为 null) | **GAP G1** |
| `submitScore_createdByNull_rejects` | createdBy=null 拒绝 | 原始 |
| `submitScore_createdByEmpty_rejects` | createdBy="" 拒绝 | 原始 |
| `submitScore_createdByBlank_rejects` | **G2 补**:createdBy="   " 仅空白 拒绝 | **GAP G2** |
| `submitScore_sessionNotFound_rejects` | session 不存在拒绝 | 原始 |
| `submitScore_sessionStatusCreated_rejects` | session.status=CREATED 拒绝 | 原始(split) |
| `submitScore_sessionStatusInProgress_rejects` | **G3 补**:session.status=IN_PROGRESS 拒绝 | **GAP G3** |
| `getScore_emptyList_returnsNullAvg` | 无评分 → avgScore=null, scoreCount=0 | 原始 |
| `getScore_singleScore_returnsExactAvg` | **G4 补**:1 条评分 → avg=该分,scoreCount=1 | **GAP G4** |
| `getScore_multipleScores_returnsAvg` | 多条 [5,4,3] → avg=4.0, scoreCount=3 | 原始 |
| `getScore_sessionNotFound_throws` | session 不存在 → IllegalArgumentException | 原始 |

### ScoringToolsTest (2 个方法) — **GAP G5**

| 测试方法 | 场景 |
|----------|------|
| `submitScore_invalidUuid_throwsRuntimeException` | sessionId 不是合法 UUID → IllegalArgumentException → RuntimeException(Tools 层 catch) |
| `getScore_invalidUuid_throwsRuntimeException` | 同上 getScore 路径 |

**测试合计:15 SessionScoringServiceTest + 2 ScoringToolsTest = 17 个测试方法**(略多于 ask 时承诺的 15,因 G3 拆 CREATED/IN_PROGRESS 后多 1 个,G1/G2 拆原"overallScoreOutOfRange"和"emptyCreatedBy"后多 2 个)。

## 9. 文档同步(Step 0 前置债清理)

M3a 启动前必须清理 M2 完成时遗留的文档过时项:

| 文件 | 过时描述 | 改为 |
|------|----------|------|
| `docs/ARCHITECTURE.md` 核心实体段 | `SlotDraftEntity 存储 Agent 填充的值,按 Session 隔离` | `SlotPromptEntity:`session_id IS NULL` 为预设/手维护;非空为某次 session 沉淀` |
| `docs/ARCHITECTURE.md` 数据流图 | `SlotDraftEntity (sessionId, slotId, value)` | `SlotPromptEntity (sessionId, slotId, characterId, content)` |
| `docs/MCP_API.md` create_session 输出 | `firstSlot.defaultValue` | 删除字段(代码已删除) |
| `docs/MCP_API.md` get_slot 输出 | `slot.defaultValue` | 删除字段 |

## 10. 实施任务清单

| # | 任务 | 工作量 | 产出 |
|---|------|--------|------|
| 1 | 同步 ARCHITECTURE.md + MCP_API.md(Step 0) | 0.5d | 2 个文档 PR |
| 2 | V5 Flyway 迁移 | 0.25d | `V5__add_session_score.sql` |
| 3 | `SessionScoreEntity` + `SessionScoreRepository` | 0.25d | 2 个 Java 文件 |
| 4 | `SessionScoringService`(submitScore + getScore) | 0.5d | 1 个 Java 文件 |
| 5 | `ScoringTools` + DTO(ScoreSubmitResult / ScoreResponse / ScoreItem) | 0.5d | 4 个 Java 文件 |
| 6 | 单元测试 `SessionScoringServiceTest`(10 场景) | 0.5d | 1 个测试类 |
| 7 | `docs/MCP_API.md` 追加 submit_score / get_score 章节;`README.md` 工具数 8→10 | 0.25d | 2 个文档 |
| 8 | 本地启动 + MCP inspector 联调 | 0.25d | 验收记录 |
|   | **合计** | **~3 天** |

## 11. 验收标准

| 验收项 | 标准 |
|--------|------|
| Agent 通过 MCP 提交评分 | `submit_score` 返回 `success=true`,DB 写入 1 条记录 |
| 未完成 session 拒绝 | 返回 `success=false`,错误信息含 "session 未完成"或当前 status |
| overall 越界拒绝 | 返回 `success=false`,错误信息含 "1-5 范围"提示 |
| createdBy 空拒绝 | 返回 `success=false`,错误信息含 "createdBy 不能为空" |
| 同 createdBy 覆盖 | DB 仍为 1 条,`isUpdate=true`,`updated_at` 更新 |
| 不同 createdBy 各算一票 | DB 多条记录,`get_score` 聚合返回平均值 |
| `get_score` 平均值正确 | 3 条评分 [5,4,3] → avgScore=4.0 |
| session 不存在拒绝 | 错误信息含 "session 不存在" |
| 单元测试 | 10 个测试方法全部通过 |
| 文档 | ARCHITECTURE / MCP_API 与代码一致;新增评分章节完整 |

## 11.5 数据流图与失败模式(plan-eng-review F1+F2)

### submit_score 数据流

```
Agent
  │ assemble_prompt(sessionId) → completed prompt
  │ (人观察生成的图,决定评分)
  │ submit_score(sessionId, overallScore=5, comment, createdBy="user-A")
  ▼
ScoringTools.submitScore
  │ (UUID parsing, 解析失败→IllegalArgumentException→RuntimeException)
  ▼
SessionScoringService.submitScore (@Transactional)
  ├─ validate createdBy 非空, score 1-5
  │   └─ 失败 → return ScoreSubmitResult.failure (软失败)
  ├─ AssembleSessionRepository.findByIdOptional
  │   └─ 不存在 → return ScoreSubmitResult.failure
  ├─ check session.status == COMPLETED
  │   └─ 非 COMPLETED → return ScoreSubmitResult.failure
  ├─ SessionScoreRepository.findBySessionIdAndCreatedBy
  │   ├─ exists → UPDATE (overall, comment, updated_at)
  │   └─ not exists → INSERT
  │       └─ 并发竞态时 → ConstraintViolationException → 500 (M3a 接受)
  └─ return ScoreSubmitResult.success { scoreId, isUpdate }
```

### get_score 数据流

```
Agent: get_score(sessionId)
  ▼
ScoringTools.getScore
  │ (UUID parsing)
  ▼
SessionScoringService.getScore (只读)
  ├─ AssembleSessionRepository.findByIdOptional
  │   └─ 不存在 → throw IllegalArgumentException
  ├─ SessionScoreRepository.findBySessionId
  └─ 聚合: scores[] + avgScore (null when empty) + scoreCount
  ▼
return ScoreResponse
```

### 失败模式表

| 代码路径 | 故障场景 | 是否有测试 | 是否有错误处理 | 用户体验 |
|----------|----------|-----------|---------------|---------|
| submit_score | DB 不可用 | ❌(框架级,M1/M2 都没专门测) | ❌ 框架透传 → 500 | Agent 看到 MCP 通用错误,需重试 |
| submit_score | 并发同 (session, createdBy) 重提交 | ❌(M3a 范围排除) | ❌ M3a 接受 500 | Agent 看到 500,需重试,DB 日志可查 UNIQUE 违反 |
| submit_score | UUID 格式非法 | ✅ (在 ScoringTools 层 catch) | ✅ throw RuntimeException | MCP 协议错误,信息明确 |
| submit_score | session 不存在 | ✅ (testcase 中) | ✅ 软失败 | Agent 看到明确"session 不存在" |
| submit_score | session 未完成 | ✅ (testcase 中) | ✅ 软失败 | Agent 看到明确"请先完成 session" |
| submit_score | createdBy 空 | ✅ (testcase 中) | ✅ 软失败 | Agent 看到明确"createdBy 不能为空" |
| submit_score | overall 越界 | ✅ (testcase 中) | ✅ 软失败 | Agent 看到明确"1-5 范围" |
| get_score | session 不存在 | ✅ (testcase 中) | ✅ IllegalArgumentException | MCP 协议错误,信息明确 |
| get_score | session 无评分 | ✅ (testcase 中) | ✅ 返回 empty + null avg | Agent 看到 scoreCount=0,行为可识别 |

**critical gap 判定**:并发竞态(submit_score)无测试 + 无错误处理 + 用户看到的不是软失败而是 500。按 skill 标准是 **critical gap**,但已通过 F5 决策明确接受(M3a 范围排除)。文档已在风险表标注。

## 11.6 代码风格约束(plan-eng-review Section 2)

### Q1) ScoringTools 严格沿用 M1 try-catch 样板

```java
@Tool(name = "submit_score", description = "...")
public ScoreSubmitResult submitScore(String sessionId, Integer overallScore, String comment, String createdBy) {
    try {
        UUID sid = UUID.fromString(sessionId);
        return scoringService.submitScore(sid, overallScore, comment, createdBy);
    } catch (IllegalArgumentException e) {
        Log.warn("提交评分失败: " + e.getMessage());
        throw new RuntimeException(e.getMessage());
    }
}
```

不要发明新的错误风格;不要把 UUID 解析校验下沉到 service 层(M1 在 Tools 层做这层 catch)。

### Q2) Service 注解一致性

- `submitScore` 写 `@Transactional` 显式声明事务边界(insert + update 都涉及 DB 写入)
- `getScore` **不写** 任何事务注解(只读方法,与 M1 `getSlot` / `getPipelineDetail` 风格一致)

### Q5) createdBy 不做 service 层长度校验(用户决策)

- service 层仅校验"非空非空白"
- 超过 64 字符 → DB 抛 `SQLException` → 500 透传
- 这是 M3a 接受的已知缺口,与 F4 "业务规则错误软失败 / DB 错误透传"原则一致

### Q6) comment 不做长度限制(用户决策)

- DB 字段为 `TEXT`,无上限
- service 层不做长度校验
- 已知 abuse 风险:外部脚本可写入 MB 级字符串
- 未来若发现 abuse 再加上限(V6 改 schema + service 校验)

### Q8) ScoringTools 类无需特殊注解

参照 `PipelineAssemblerTools`:M1 的工具类**没有**任何类级注解,仅靠 `@Tool` 方法注解被 `quarkus-mcp-server-http` 1.12.0 自动扫描注册。ScoringTools 沿用同样模式 —— 不加 `@ApplicationScoped` 等多余注解。

### P1) 性能日志埋点 — 不加(plan-eng-review Section 4 决策)

- M1 PipelineAssemblerService 的 `logLatency()` 是因 Pipeline 组装是热路径而设。
- 评分操作低频(用户每次组装最多评一次),不需要 elapsedMicros 监控。
- M3a service 方法保持简洁,不加日志埋点。
- 未来若发现评分变热(如批量评分场景),可补 Micrometer 而非沿用 logLatency。

## 12. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 没有认证,Agent 可任意伪造 createdBy 刷分 | 数据可信度低 | M3a 接受:M1/M2 都没认证,M3a 不应该独自补;后续 milestone 引入 API Key 时统一处理 |
| 用户对评分维度后悔(只 overall 不够) | M3a 验收完无法回滚 | V5 schema 仅含 overall_score,后续增维度可通过 `V6__add_consistency_score.sql` 追加列,不破坏现有数据 |
| `session_score.session_id ON DELETE CASCADE` 但 M1 无删除 session 的接口 | 当前无影响 | 保留正确语义,M2/M3 任何 milestone 加 session 删除接口时自动生效 |
| 评分高峰期写入热点(同 session 多评分) | 当前无量化目标 | `UNIQUE (session_id, created_by)` 索引足够,M3a 不预优化;监控指标 `op=submitScore elapsedMicros=...` 沿用 M1 的日志埋点风格 |
| 并发重提交同一 (session_id, createdBy) 触发 ConstraintViolation → 500 | Agent 看到 500 而非软失败 | M3a 接受该风险(plan-eng-review F5 决策):MCP 客户端 串行调用为常态,Agent 自报 createdBy 同时并发的场景几乎不存在;真出现 500 时,排查路径为查 DB 日志中的 UNIQUE 约束违反 |

## 13. 不在 M3a 范围(明确排除)

- 组件推荐 `list_slot_prompts` MCP 工具 → 留待数据积累后的下个 milestone
- 细粒度 slot_prompt 评分 → 同上
- 评分维度扩展(consistency / fidelity / detail)→ 留待真实需求驱动
- 低分自动标记 needs_review 持久化 → 当前不实现,不写回 session 状态。未来加 `list_low_scored_sessions` MCP 工具时,需配套 V6 补 `idx_session_score_overall` 索引
- M3b 多模型适配(gpt-image-2 + Midjourney)→ 业务暂不需要
- 评分权限/认证 → 与 M1/M2 保持一致,不在 M3a 引入
- 评分历史追溯(查看同 createdBy 的覆盖前版本)→ 当前为覆盖语义;如需历史,后续追加 `session_score_history` 表

## 14. 后续 Milestone 衔接

进入 M3 之后的下一阶段(假设命名 M4)时:
- 若要做组件推荐,可基于 `session_score` + `slot_prompt.session_id` 关联,反推哪些 slot_prompt 出现在高分 session 中
- 若要细粒度评分,新增 `slot_prompt_score` 表,与 `session_score` 共存
- 若要引入认证,统一为 API Key 自动注入 `created_by`,而非由 Agent 自报
