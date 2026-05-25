# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication Rule

- 使用汉语对话（Use Chinese language for all communication with the user）

## Project Overview

fiona-helper 是一个基于 Quarkus + MCP Server 的提示模板引擎。支持 Pipeline 组装（分步填充 Slot）和角色管理，通过 MCP Tools 供外部 Agent 调用。

## Tech Stack

- **Runtime:** Quarkus 3.35.3
- **Language:** Java 17+ (推荐 GraalVM JDK 25.0.3+9.1)
- **Build:** Maven 3.9+
- **Database:** PostgreSQL 14+
- **ORM:** Hibernate ORM with Panache
- **Migrations:** Flyway
- **REST:** quarkus-rest-jackson
- **MCP:** quarkus-mcp-server-http 1.12.0
- **Testing:** JUnit 5 + Mockito 5.14.2 + AssertJ 3.26.3

## Common Commands

```bash
# 开发模式（热重载）
./mvnw quarkus:dev

# 编译打包（JVM 模式）
./mvnw package

# 运行打包后的应用
java -jar target/quarkus-app/quarkus-run.jar

# Native 编译
./mvnw package -Pnative -DskipTests
# 或使用便捷脚本
./compile-native.sh

# 运行 Native 可执行文件
./target/fiona-helper-1.0.0-SNAPSHOT-runner

# 运行所有测试
./mvnw test

# 运行单个测试类
./mvnw test -Dtest=PipelineAssemblerServiceTest

# 运行单个测试方法
./mvnw test -Dtest=PipelineAssemblerServiceTest#testMethodName
```

## Architecture

### Core Concepts

- **Pipeline（流水线）**：由多个有序 Slot 组成。每个 Pipeline 有一个世界观设定（worldSetting）。PipelineEntity 与 SlotEntity 是一对多关系，通过 `orderIndex` 排序。
- **Slot（槽位）**：Pipeline 中的单个步骤。包含 name、orderIndex、constraintType（约束类型）、description（描述）、wordLimit（字数限制）。
- **Character（角色）**：包含 name、baseDesign（基础设计）、personality（性格）。用于会话组装时注入角色设定。
- **AssembleSession（组装会话）**：一次 Pipeline 组装的实例。按步进方式填充 Slot，status 记录进度（CREATED → IN_PROGRESS → COMPLETED）。
- **SlotPrompt（Slot 内容池）**：存储 Slot 的填充内容。session_id=NULL 表示预设/手维护内容；非空表示某次 session 的沉淀内容。支持按 character_id 过滤。
- **SessionScore（会话评分）**：对已完成 session 的评分（1-5 分），按 created_by 去重（覆盖更新）。

### Package Structure

```
io.promptforge/
├── entity/          # JPA 实体（PanacheEntityBase）
├── repository/      # PanacheRepository 实现
├── service/         # 业务逻辑层
│   ├── PipelineAssemblerService    # 核心：session 创建、slot 步进填充、prompt 组装
│   └── SessionScoringService       # 评分提交与查询
├── tool/            # MCP Tools 端点（@Tool 注解）
│   ├── PipelineAssemblerTools      # 组装流程相关工具
│   └── ScoringTools                # 评分相关工具
└── dto/             # 数据传输对象（Java record）
```

### Key Design Patterns

- **步进控制（Step Control）**：`insertSlotValue` 严格按 `currentSlotIndex` 顺序填充，不允许跳过或回退。每次填充后 `currentSlotIndex++`。
- **软失败（Soft Failure）**：业务规则错误返回 DTO 包装（如 `InsertResult{success=false, message="..."}`），而非抛异常。只有参数解析错误（如非法 UUID）才会抛异常被 MCP 层捕获。
- **服务层隔离**：SessionScoringService 与 PipelineAssemblerService 完全独立，各自处理不同领域。
- **错误传播**：Tools 层 catch `IllegalArgumentException` 后抛 `RuntimeException`，由 MCP 协议层转换为标准错误响应。

### MCP Tool Flow

```
Agent → create_session(pipelineId, characterId) → sessionId + firstSlot
    → get_world_setting(sessionId) → worldSetting
    → get_character_setting(sessionId) → characterName, baseDesign, personality
    → [对每个 Slot]
        get_slot(sessionId) → slotInfo, completedSlots, nextStep
        insert_slot_value(sessionId, slotId, value, worldSetting) → success/失败
    → assemble_prompt(sessionId) → 完整 prompt
    → submit_score(sessionId, overallScore, comment, createdBy) → success
    → get_score(sessionId) → scores[], avgScore, scoreCount
```

### Database Migrations

迁移文件位于 `src/main/resources/db/migration/`：
- V1：Pipeline + Slot + Character 初始表
- V2：AssembleSession + SlotDraft（后迁移为 SlotPrompt）
- V3：Slot 字段长度调整
- V4：SlotPrompt 统一池 + 移除 defaultValue
- V5：SessionScore 评分表

新增实体字段必须配合同版本的 Flyway 迁移。

### Testing Approach

- 纯单元测试模式（Mockito），不使用 Testcontainers
- Service 层测试：Mock Repository，验证业务逻辑
- Tools 层测试：Mock Service，验证参数转换和错误传播
- 代码已采用统一的 try-catch 样板处理 UUID 解析（参考 `PipelineAssemblerTools`）

## Important Constraints

- **无认证**：所有接口无 auth，created_by 由调用方自报
- **无 session 删除接口**：目前不提供删除 assemble_session 的功能
- **评分只支持 overall**：不支持多维度评分
- **Native Image 编译**：项目支持 GraalVM Native Image，启动 < 50ms。修改反射/动态代理相关代码时需验证 native 编译
