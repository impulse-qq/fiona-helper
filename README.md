# fiona-helper

基于 Quarkus + MCP Server 的提示模板引擎，支持 Pipeline 组装和角色管理。

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## 系统要求

- **GraalVM** (推荐): `GraalVM JDK 25.0.3+9.1` 或更高版本
  - 安装路径: `/usr/lib/jvm/graalvm-jdk-25.0.3+9.1`
  - Native Image 编译需要 GraalVM 的 `native-image` 工具
- **Java**: 兼容 Java 17+，但推荐直接使用 GraalVM
- **Maven**: 3.9+
- **PostgreSQL**: 14+

## 环境配置

项目已配置使用 GraalVM 进行编译。确保环境变量正确设置：

```bash
export GRAALVM_HOME=/usr/lib/jvm/graalvm-jdk-25.0.3+9.1
export JAVA_HOME=$GRAALVM_HOME
export PATH=$JAVA_HOME/bin:$PATH
```

验证 GraalVM 安装：

```bash
java -version
# 应输出: Oracle GraalVM 25.0.3+9.1
```

## 数据库配置

编辑 `src/main/resources/application.properties`：

```properties
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/promptforge
quarkus.datasource.username=your_username
quarkus.datasource.password=your_password
```

## Running the application in dev mode

开发模式支持热重载：

```shell
./mvnw quarkus:dev
```

> **_NOTE:_** Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application (JVM 模式)

标准 JVM 模式打包：

```shell
./mvnw package
```

产物为 `target/quarkus-app/quarkus-run.jar`，依赖位于 `target/quarkus-app/lib/`。

```shell
java -jar target/quarkus-app/quarkus-run.jar
```

### uber-jar 模式

```shell
./mvnw package -Dquarkus.package.jar.type=uber-jar
java -jar target/*-runner.jar
```

## Native Image 编译 (推荐)

本项目使用 **GraalVM Native Image** 编译为原生可执行文件，获得更快的启动速度和更低的内存占用。

### 前置条件

确保 GraalVM 已安装且 `native-image` 工具可用：

```bash
$GRAALVM_HOME/bin/native-image --version
```

### 编译 Native 可执行文件

使用项目提供的便捷脚本（自动设置 JAVA_HOME）：

```shell
./compile-native.sh
```

或手动使用 Maven：

```shell
export JAVA_HOME=/usr/lib/jvm/graalvm-jdk-25.0.3+9.1
./mvnw package -Pnative -DskipTests
```

### 运行 Native 可执行文件

```shell
./target/fiona-helper-1.0.0-SNAPSHOT-runner
```

Native 可执行文件特点：
- 启动时间: < 50ms
- 内存占用: 显著低于 JVM 模式
- 文件大小: 约 90MB（包含所有依赖）

### 容器内 Native 编译

如果没有本地 GraalVM，可在容器内编译：

```shell
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

### Native 编译配置

Native 编译相关配置位于 `src/main/resources/application.properties`：

```properties
# Native Image 额外构建参数
quarkus.native.additional-build-args=-H:+ReportExceptionStackTraces
quarkus.native.container-build=false
```

## MCP Server

本项目以 **MCP Server** 模式运行，通过 HTTP 暴露 Tools 供外部 Agent 调用。

### MCP Tools

| Tool | 功能描述 |
|------|----------|
| `create_session` | 创建组装会话，返回 sessionId 和第一个 Slot 信息 |
| `get_world_setting` | 获取当前 Pipeline 的世界观设定 |
| `get_character_setting` | 获取当前 Session 关联角色的基础设计 |
| `get_slot` | 查询指定 Slot 的信息（含已完成列表、下一步指引） |
| `insert_slot_value` | 填充当前 Slot 的值（严格步进控制），要求显式传入世界观 |
| `assemble_prompt` | 最终组装，按 orderIndex 拼接所有草稿值为完整 Prompt |
| `list_pipelines` | 列出可用 Pipeline 摘要(id, name, description, worldSetting) |
| `get_pipeline` | 获取 Pipeline 详情含所有 Slot 的有序定义 |
| `submit_score` | (M3a) 对已完成 session 提交评分(1-5)；同 createdBy 重提交=覆盖 |
| `get_score` | (M3a) 查询某 session 的所有评分 + 平均分 + 评分人数 |

### 组装流程

```
Agent → create_session → get_world_setting → get_character_setting
    → [对每个 Slot] get_slot → insert_slot_value → assemble_prompt
```

## 项目结构

```
src/main/java/io/promptforge/
├── entity/          # JPA 实体 (Pipeline, Slot, Character, Session, Draft)
├── repository/      # Panache 仓库层
├── service/         # 业务逻辑层 (PipelineAssemblerService)
├── tool/            # MCP Tools 端点
└── dto/             # 数据传输对象
```

## Related Guides

- Hibernate ORM with Panache ([guide](https://quarkus.io/guides/hibernate-orm-panache)): Simplified JPA/Hibernate data access layer with active record and repository patterns
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST
- Flyway ([guide](https://quarkus.io/guides/flyway)): Handle your database schema migrations
- JDBC Driver - PostgreSQL ([guide](https://quarkus.io/guides/datasource)): Connect to the PostgreSQL database via JDBC
- Quarkus MCP Server ([docs](https://docs.quarkiverse.io/quarkus-mcp-server/dev/index.html)): MCP Server implementation for Quarkus
