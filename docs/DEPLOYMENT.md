# 部署文档

## 环境要求

- **GraalVM**: `GraalVM JDK 25.0.3+9.1` 或更高版本
  - 路径: `/usr/lib/jvm/graalvm-jdk-25.0.3+9.1`
- **PostgreSQL**: 14+
- **Linux**: x86_64 架构（Native Image 构建目标）

## 数据库初始化

```sql
CREATE DATABASE promptforge;
CREATE USER impulse WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE promptforge TO impulse;
```

Flyway 会在应用启动时自动执行迁移脚本。

## 部署模式

### 模式一：Native 可执行文件（推荐）

Native Image 提供最快的启动速度和最低的内存占用，适合生产环境。

#### 编译

使用项目提供的脚本：

```bash
cd /home/impulse/workspace/railgun/fiona-helper2/prompt-forge
./compile-native.sh
```

或手动编译：

```bash
export JAVA_HOME=/usr/lib/jvm/graalvm-jdk-25.0.3+9.1
export PATH=$JAVA_HOME/bin:$PATH
mvn clean package -Pnative -DskipTests
```

#### 产物

- 可执行文件: `target/fiona-helper-1.0.0-SNAPSHOT-runner`
- 大小: 约 90MB
- 启动时间: < 50ms

#### 运行

```bash
./target/fiona-helper-1.0.0-SNAPSHOT-runner
```

#### Systemd 服务示例

```ini
# /etc/systemd/system/fiona-helper.service
[Unit]
Description=Fiona Helper MCP Server
After=network.target postgresql.service

[Service]
Type=simple
User=impulse
WorkingDirectory=/opt/fiona-helper
ExecStart=/opt/fiona-helper/fiona-helper-1.0.0-SNAPSHOT-runner
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

### 模式二：JVM 模式

适合开发和调试场景。

#### 编译

```bash
export JAVA_HOME=/usr/lib/jvm/graalvm-jdk-25.0.3+9.1
mvn clean package
```

#### 运行

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

### 模式三：Dev 模式

开发调试使用，支持热重载：

```bash
mvn quarkus:dev
```

## 配置

### application.properties

```properties
# Database
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/promptforge
quarkus.datasource.username=impulse
quarkus.datasource.password=impulse2026!

# Hibernate
quarkus.hibernate-orm.database.generation=validate
quarkus.hibernate-orm.log.sql=false

# Flyway
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true

# Logging
quarkus.log.level=INFO
quarkus.log.category."io.promptforge".level=DEBUG

# Native Image
quarkus.native.additional-build-args=-H:+ReportExceptionStackTraces
quarkus.native.container-build=false
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `JAVA_HOME` | GraalVM 安装路径 | `/usr/lib/jvm/graalvm-jdk-25.0.3+9.1` |
| `GRAALVM_HOME` | GraalVM 安装路径（Native 编译） | 同 `JAVA_HOME` |

## 健康检查

Native 可执行文件启动后，MCP Server 通过 HTTP 提供服务：

```bash
# 检查 MCP Server 状态
curl http://localhost:8080/mcp/sse

# Quarkus 健康检查
curl http://localhost:8080/q/health
```

## 常见问题

### Native 编译失败

确保 `JAVA_HOME` 指向 GraalVM：

```bash
$JAVA_HOME/bin/java -version
# 应输出 Oracle GraalVM 而非 OpenJDK
```

### 数据库连接失败

检查 `application.properties` 中的数据库配置，确保 PostgreSQL 服务正在运行。

### MCP 工具未注册

确保 `quarkus-mcp-server-http` 依赖已正确引入，且应用成功启动。
