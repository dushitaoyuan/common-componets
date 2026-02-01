# Common Components 项目文档

## 项目概述

Common Components 是一个 Java 后端通用组件库项目，提供两个核心功能模块：

1. **rest-common-search** - 通用条件搜索组件
2. **common-audit-log** - 审计日志组件

### 技术栈

- **语言**: Java 8
- **构建工具**: Maven
- **框架**: Spring Boot 2.7.18
- **ORM**: MyBatis-Plus 3.5.9
- **数据库**: MySQL, SQLite, H2
- **其他依赖**:
    - Lombok
    - Apache Commons (Collections4, Lang3)
    - Guava
    - FastJSON2
    - Graceful Response (统一返回值)

## 模块结构

### 1. rest-common-search (通用搜索组件)

提供统一的搜索协议，支持前后端通用的搜索条件定义，支持分页和列表条件搜索。
<a href="./rest-common-search/doc/ReadMe.md">通用搜索组件 </a>

**核心功能**:
- 通用搜索 DTO → 搜索 VO 转换
- 通用搜索 DTO → SQL 转换
- 完善的安全控制（校验列名称和 SQL 参数合法性）

**关键类**:
- `SearchCondition` - 搜索条件定义
- `PageRequest<T>` - 分页请求封装
- `SearchConditionBeanUtil` - 搜索条件转换为 Bean
- `SearchConditionSqlUtil` - 搜索条件转换为 SQL
- `SqlCheckUtil` - SQL 安全校验

**支持的查询操作**:
- EQUAL, LIKE, LEFT_LIKE, RIGHT_LIKE
- IN, NOT_IN
- BETWEEN_AND
- 复合条件（AND/OR 组合）


### 2. common-audit-log (审计日志组件)

<a href="./common-audit-log/doc/操作日志使用手册.md">审计日志组件 </a>


基于 AOP 的审计日志组件，支持操作日志的自动收集、差异比对和存储。

**子模块**:
- `common-audit-log-core` - 核心功能（AOP、注解、差异比对）
- `common-audit-log-runtime` - 运行时配置和实现
- `common-audit-log-web` - Web 应用示例和演示

**核心注解**:
- `@OperateLog` - 操作日志注解，支持 SpEL 表达式
- `@SimpleOperateLog` - 简化版操作日志注解
- `@LogDiff` - 数据差异比对注解

**特性**:
- **注解驱动**：通过简单的注解即可实现日志记录
- **SpEL 表达式支持**：动态填充日志操作描述,操作分类,操作数据id等信息
- **差异比对**：支持操作前后数据差异自动比对
- **支持多层级嵌套调用日志**：支持多层级嵌套调用日志
- **条件性记录**：支持根据条件决定是否记录日志
- **异步收集**：支持异步日志收集，不影响主业务性能
- **配套查询系统(可选)**： 支持多维条件查询日志,开箱即用

**支持的数据库**:
- SQLite (默认)
- MySQL
- H2