# Changelog

## [1.0.0-SNAPSHOT] - 2026-05-22

### Added
- **Web UI — Phase 1: Character Cards**
  - Vue 3 + Vite 前端框架，深色主题 UI
  - 角色卡片展示（头像、名称、性格标签）
  - 角色创建/编辑/删除表单
  - 头像上传功能（拖拽 + 预览）
  - REST API: `GET/POST/PUT/DELETE /api/characters`, `POST /api/characters/{id}/avatar`
  - 数据库迁移 V6: `story_character.avatar_path`

- **Web UI — Phase 2: Pipeline + Session 浏览**
  - Pipeline 列表页面（卡片展示，含世界观、slot 数量）
  - Session 列表页面（按 Pipeline 过滤，显示角色、日期、图片数）
  - Session 详情页面（组装内容、产物图片画廊、图片上传）
  - REST API: `GET /api/pipelines`, `GET /api/pipelines/{id}/sessions`
  - REST API: `GET /api/sessions/{id}`, `GET/POST /api/sessions/{id}/images`

- **Session 图片归档**
  - `upload_session_image` MCP Tool（上传图片到已完成 session）
  - `SessionImageResource` 提供图片文件访问（路径遍历防护）
  - 数据库迁移 V7: `session_image` 表

- **Skill 文档**
  - `SKILL.md` — 将项目封装为 Agent 可调用的 Skill

### Fixed
- 空 catch 块添加日志警告（`ImageResource` 旧头像删除失败）
- 提取重复的图片 URL 构建逻辑到 `SessionImageEntity.getImageUrl()`

## Test Plan
- [x] 53 个单元测试全部通过（0 失败）
