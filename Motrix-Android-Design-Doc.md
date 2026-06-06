# Motrix Android - 技术设计文档

> 版本：1.0 | 日期：2026-06-06

---

## 目录

1. [架构设计](#1-架构设计)
2. [核心类接口定义](#2-核心类接口定义)
3. [数据库表结构设计](#3-数据库表结构设计)
4. [UI/UX 设计规范](#4-uiux-设计规范)
5. [第三方库选型清单](#5-第三方库选型清单)
6. [集成 Aria2 vs 自研 HTTP 方案对比与推荐](#6-集成-aria2-vs-自研-http-方案对比与推荐)
7. [后台任务与恢复机制设计](#7-后台任务与恢复机制设计)
8. [磁力链接处理流程](#8-磁力链接处理流程)
9. [安卓版本兼容性要点](#9-安卓版本兼容性要点)
10. [GitHub Action 自动构建 APK 方案设计](#10-github-action-自动构建-apk-方案设计)
11. [附录：手动编译环境搭建与打包 APK 教程](#11-附录手动编译环境搭建与打包-apk-教程)

---

## 1. 架构设计

### 1.1 整体架构概述

采用 **MVVM + Clean Architecture** 分层架构，将应用分为三层：

```
┌─────────────────────────────────────────────────────┐
│                   Presentation Layer                 │
│  (Jetpack Compose UI + ViewModel + StateFlow)       │
├─────────────────────────────────────────────────────┤
│                   Domain Layer                       │
│  (Use Cases / Interactors + Repository Interfaces)  │
├─────────────────────────────────────────────────────┤
│                   Data Layer                         │
│  (Repository Impl + Room DB + Aria2 Engine + FS)     │
└─────────────────────────────────────────────────────┘
```

### 1.2 模块划分

```
com.motrix.android/
├── app/                          # Application 壳模块
│   ├── di/                       # Hilt 依赖注入配置
│   ├── MotrixApp.kt              # Application 类
│   └── MainActivity.kt           # 单 Activity 入口
│
├── core/                         # 核心基础模块
│   ├── database/                 # Room 数据库定义
│   │   ├── MotrixDatabase.kt
│   │   ├── dao/
│   │   ├── entity/
│   │   └── converter/
│   ├── engine/                   # 下载引擎抽象层
│   │   ├── DownloadEngine.kt     # 引擎接口
│   │   ├── Aria2Engine.kt        # Aria2 引擎实现
│   │   ├── Aria2ProcessManager.kt
│   │   └── model/                # 引擎数据模型
│   ├── network/                  # 网络层
│   │   ├── Aria2RpcClient.kt     # JSON-RPC 客户端
│   │   └── WebSocketClient.kt    # WebSocket 通知
│   ├── storage/                  # 存储层
│   │   ├── FileStorageManager.kt
│   │   └── MediaStoreHelper.kt
│   └── common/                   # 通用工具
│       ├── util/
│       ├── extension/
│       └── theme/                # 主题与设计令牌
│
├── feature/                      # 功能模块
│   ├── tasklist/                 # 任务列表（主页）
│   │   ├── TaskListScreen.kt
│   │   ├── TaskListViewModel.kt
│   │   └── components/
│   ├── newtask/                  # 新建下载
│   │   ├── NewTaskScreen.kt
│   │   └── NewTaskViewModel.kt
│   ├── taskdetail/               # 任务详情
│   │   ├── TaskDetailScreen.kt
│   │   └── TaskDetailViewModel.kt
│   ├── settings/                 # 偏好设置
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── notification/             # 通知管理
│       └── DownloadNotificationManager.kt
│
└── service/                      # 后台服务
    ├── DownloadService.kt        # 前台服务
    └── DownloadWorker.kt         # WorkManager Worker
```

### 1.3 数据流向

```
UI Event → ViewModel → UseCase → Repository → Engine/DB
                                              ↓
UI State ← ViewModel ← UseCase ← Repository ← Engine/DB
```

- **单向数据流**：UI 通过 Intent/Event 驱动 ViewModel，ViewModel 暴露 StateFlow 供 UI 订阅
- **引擎通信**：通过 Aria2 JSON-RPC over WebSocket 与本地 aria2c 进程通信
- **状态同步**：WebSocket 推送 + 定时轮询双保险

---

## 2. 核心类接口定义

### 2.1 下载引擎接口

```kotlin
/**
 * 下载引擎抽象接口，解耦具体实现
 */
interface DownloadEngine {
    /** 初始化引擎（启动 aria2c 进程） */
    suspend fun initialize(config: EngineConfig): Result<Unit>

    /** 关闭引擎 */
    suspend fun shutdown(): Result<Unit>

    /** 添加 HTTP/FTP 下载任务 */
    suspend fun addHttpTask(request: HttpTaskRequest): Result<String>

    /** 添加磁力链接 / Torrent 下载任务 */
    suspend fun addMagnetTask(request: MagnetTaskRequest): Result<String>

    /** 添加 Torrent 文件下载任务 */
    suspend fun addTorrentTask(request: TorrentTaskRequest): Result<String>

    /** 暂停任务 */
    suspend fun pauseTask(gid: String): Result<Unit>

    /** 恢复任务 */
    suspend fun resumeTask(gid: String): Result<Unit>

    /** 删除任务（含文件可选） */
    suspend fun removeTask(gid: String, removeFile: Boolean): Result<Unit>

    /** 暂停全部 */
    suspend fun pauseAll(): Result<Unit>

    /** 恢复全部 */
    suspend fun resumeAll(): Result<Unit>

    /** 获取任务状态 */
    suspend fun getTaskStatus(gid: String): Result<TaskStatus>

    /** 获取全局统计 */
    suspend fun getGlobalStat(): Result<GlobalStat>

    /** 获取活跃任务列表 */
    suspend fun getActiveTasks(): Result<List<TaskInfo>>

    /** 获取等待中任务列表 */
    suspend fun getWaitingTasks(offset: Int, limit: Int): Result<List<TaskInfo>>

    /** 获取已完成/出错/已删除任务列表 */
    suspend fun getStoppedTasks(offset: Int, limit: Int): Result<List<TaskInfo>>

    /** 修改全局选项 */
    suspend fun changeGlobalOption(options: Map<String, String>): Result<Unit>

    /** 修改单任务选项 */
    suspend fun changeTaskOption(gid: String, options: Map<String, String>): Result<Unit>

    /** 监听任务事件流 */
    fun observeTaskEvents(): Flow<TaskEvent>
}
```

### 2.2 引擎配置

```kotlin
data class EngineConfig(
    val downloadDir: String,           // 下载目录
    val maxConcurrentDownloads: Int = 5, // 最大同时下载数
    val maxConnectionPerServer: Int = 16, // 单服务器最大连接数
    val split: Int = 16,               // 单任务分片数
    val maxOverallDownloadLimit: String = "0", // 全局限速，0=不限
    val maxDownloadLimit: String = "0",       // 单任务限速
    val continueDownload: Boolean = true,     // 断点续传
    val enableDht: Boolean = true,            // 启用 DHT
    val btEnableLpd: Boolean = true,          // 启用 LPD
    val btEnablePeerExchange: Boolean = true, // 启用 Peer Exchange
    val listenPort: Int = 6881,               // BT 监听端口
    val rpcSecret: String = "",               // RPC 密钥
    val userAgent: String = ""                // 自定义 UA
)
```

### 2.3 任务数据模型

```kotlin
/** 任务状态枚举 */
enum class TaskState {
    ACTIVE,      // 下载中
    WAITING,     // 等待中
    PAUSED,      // 已暂停
    COMPLETED,   // 已完成
    ERROR,       // 出错
    REMOVED      // 已删除
}

/** 任务信息（引擎返回的实时状态） */
data class TaskInfo(
    val gid: String,
    val name: String,
    val state: TaskState,
    val totalLength: Long,          // 总字节数
    val completedLength: Long,      // 已完成字节数
    val uploadLength: Long,          // 已上传字节数
    val downloadSpeed: Long,        // 下载速度 bytes/s
    val uploadSpeed: Long,          // 上传速度 bytes/s
    val connections: Int,           // 当前连接数
    val numSeeders: Int,            // 种子数（BT）
    val dir: String,                // 下载目录
    val files: List<TaskFileInfo>,  // 文件列表
    val bittorrent: BtInfo?,        // BT 信息
    val errorCode: String?,         // 错误码
    val errorMessage: String?,      // 错误信息
    val createdAt: Long,            // 创建时间戳
    val completedAt: Long?          // 完成时间戳
)

/** 文件信息 */
data class TaskFileInfo(
    val index: Int,
    val path: String,
    val length: Long,
    val completedLength: Long,
    val selected: Boolean = true    // BT 选择性下载
)

/** BT 信息 */
data class BtInfo(
    val announceList: List<String>,
    val comment: String?,
    val creationDate: Long?,
    val name: String?
)

/** 全局统计 */
data class GlobalStat(
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val numActive: Int,
    val numWaiting: Int,
    val numStopped: Int
)

/** 任务事件 */
sealed class TaskEvent {
    data class Started(val gid: String) : TaskEvent()
    data class Paused(val gid: String) : TaskEvent()
    data class Stopped(val gid: String) : TaskEvent()
    data class Completed(val gid: String) : TaskEvent()
    data class Error(val gid: String, val errorCode: String, val errorMessage: String) : TaskEvent()
    data class Progress(val gid: String, val completedLength: Long, val totalLength: Long, val speed: Long) : TaskEvent()
}
```

### 2.4 Repository 接口

```kotlin
interface TaskRepository {
    /** 获取任务列表（按状态过滤） */
    fun getTasks(state: TaskState? = null): Flow<List<TaskEntity>>

    /** 获取单个任务 */
    suspend fun getTask(gid: String): TaskEntity?

    /** 保存/更新任务到本地 DB */
    suspend fun upsertTask(task: TaskEntity)

    /** 删除任务 */
    suspend fun deleteTask(gid: String)

    /** 添加 HTTP 下载 */
    suspend fun addHttpDownload(url: String, options: DownloadOptions): Result<String>

    /** 添加磁力链接下载 */
    suspend fun addMagnetDownload(magnetUri: String, options: DownloadOptions): Result<String>

    /** 添加 Torrent 文件下载 */
    suspend fun addTorrentDownload(torrentPath: String, options: DownloadOptions): Result<String>

    /** 暂停任务 */
    suspend fun pauseTask(gid: String): Result<Unit>

    /** 恢复任务 */
    suspend fun resumeTask(gid: String): Result<Unit>

    /** 删除任务 */
    suspend fun removeTask(gid: String, withFile: Boolean): Result<Unit>

    /** 暂停全部 */
    suspend fun pauseAllTasks(): Result<Unit>

    /** 恢复全部 */
    suspend fun resumeAllTasks(): Result<Unit>

    /** 获取全局统计 */
    fun getGlobalStat(): Flow<GlobalStat>

    /** 同步引擎状态到 DB */
    suspend fun syncEngineState()
}

data class DownloadOptions(
    val dir: String? = null,
    val filename: String? = null,
    val split: Int? = null,
    val maxConnectionPerServer: Int? = null,
    val header: List<String>? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val selectFile: String? = null  // BT 选择性下载，文件索引
)
```

### 2.5 ViewModel 接口（以 TaskListViewModel 为例）

```kotlin
data class TaskListUiState(
    val activeTasks: List<TaskUiModel> = emptyList(),
    val waitingTasks: List<TaskUiModel> = emptyList(),
    val completedTasks: List<TaskUiModel> = emptyList(),
    val errorTasks: List<TaskUiModel> = emptyList(),
    val globalStat: GlobalStat = GlobalStat(0, 0, 0, 0, 0),
    val selectedTab: TaskTab = TaskTab.ACTIVE,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class TaskTab { ACTIVE, COMPLETED, ERROR }

data class TaskUiModel(
    val gid: String,
    val name: String,
    val state: TaskState,
    val progress: Float,              // 0.0 ~ 1.0
    val totalSize: String,            // 格式化后的大小
    val downloadedSize: String,
    val speed: String,                // 格式化后的速度
    val remainingTime: String?,       // 预计剩余时间
    val connections: Int,
    val seeders: Int,
    val isTorrent: Boolean
)

class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {
    val uiState: StateFlow<TaskListUiState>

    fun onTabSelected(tab: TaskTab)
    fun onPauseTask(gid: String)
    fun onResumeTask(gid: String)
    fun onRemoveTask(gid: String, withFile: Boolean)
    fun onPauseAll()
    fun onResumeAll()
    fun onRefresh()
}
```

### 2.6 Aria2 RPC 客户端

```kotlin
interface Aria2RpcClient {
    /** 发送 JSON-RPC 请求 */
    suspend fun <T> call(method: String, params: List<Any?>): Result<T>

    /** 连接 WebSocket 接收事件通知 */
    fun connectWebSocket(): Flow<Aria2Event>

    /** 关闭 WebSocket */
    suspend fun disconnectWebSocket()
}

/** Aria2 WebSocket 事件 */
sealed class Aria2Event {
    data class DownloadStart(val gid: String) : Aria2Event()
    data class DownloadPause(val gid: String) : Aria2Event()
    data class DownloadStop(val gid: String) : Aria2Event()
    data class DownloadComplete(val gid: String) : Aria2Event()
    data class DownloadError(val gid: String) : Aria2Event()
    data class BtDownloadComplete(val gid: String) : Aria2Event()
}
```

---

## 3. 数据库表结构设计

### 3.1 任务表 (download_tasks)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| gid | TEXT | PRIMARY KEY | Aria2 GID |
| name | TEXT | NOT NULL | 任务名称 |
| type | TEXT | NOT NULL | 任务类型：http / ftp / magnet / torrent |
| state | TEXT | NOT NULL | 状态枚举值 |
| url | TEXT | | 原始 URL 或磁力链接 |
| dir | TEXT | NOT NULL | 下载目录 |
| total_length | INTEGER | DEFAULT 0 | 总字节数 |
| completed_length | INTEGER | DEFAULT 0 | 已完成字节数 |
| download_speed | INTEGER | DEFAULT 0 | 当前下载速度 |
| upload_speed | INTEGER | DEFAULT 0 | 当前上传速度 |
| connections | INTEGER | DEFAULT 0 | 连接数 |
| num_seeders | INTEGER | DEFAULT 0 | 种子数 |
| error_code | TEXT | | 错误码 |
| error_message | TEXT | | 错误信息 |
| user_agent | TEXT | | 自定义 UA |
| referer | TEXT | | 来源页 |
| headers | TEXT | | 自定义请求头 JSON |
| created_at | INTEGER | NOT NULL | 创建时间戳 |
| updated_at | INTEGER | NOT NULL | 更新时间戳 |
| completed_at | INTEGER | | 完成时间戳 |
| is_selected | INTEGER | DEFAULT 1 | 是否选中（批量操作） |

### 3.2 文件表 (download_files)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | 自增主键 |
| task_gid | TEXT | FOREIGN KEY → download_tasks(gid) | 所属任务 |
| file_index | INTEGER | NOT NULL | 文件索引 |
| path | TEXT | NOT NULL | 文件路径 |
| length | INTEGER | DEFAULT 0 | 文件总大小 |
| completed_length | INTEGER | DEFAULT 0 | 已下载大小 |
| selected | INTEGER | DEFAULT 1 | BT 选择性下载标记 |

### 3.3 设置表 (settings)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| key | TEXT | PRIMARY KEY | 设置项键名 |
| value | TEXT | NOT NULL | 设置项值（JSON 序列化） |
| updated_at | INTEGER | NOT NULL | 更新时间戳 |

**预定义设置项**：

| key | 默认值 | 说明 |
|-----|--------|------|
| download_dir | /sdcard/Download/Motrix | 默认下载目录 |
| max_concurrent_downloads | 5 | 最大同时下载数 |
| max_connection_per_server | 16 | 单服务器最大连接数 |
| split | 16 | 单任务分片数 |
| max_overall_download_limit | 0 | 全局限速 (KB/s)，0=不限 |
| max_download_limit | 0 | 单任务限速 (KB/s) |
| continue_download | true | 断点续传 |
| enable_dht | true | 启用 DHT |
| bt_listen_port | 6881 | BT 监听端口 |
| enable_auto_update_tracker | true | 自动更新 Tracker |
| tracker_list_url | "" | Tracker 列表 URL |
| theme_mode | system | 主题模式：light / dark / system |
| notification_sound | true | 通知声音 |
| notification_vibrate | false | 通知振动 |
| delete_with_file | false | 删除任务时同时删除文件 |

### 3.4 Tracker 缓存表 (tracker_cache)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | 自增主键 |
| url | TEXT | NOT NULL UNIQUE | Tracker URL |
| updated_at | INTEGER | NOT NULL | 更新时间戳 |

---

## 4. UI/UX 设计规范

### 4.1 设计理念

综合参考 **Motrix 原版** 的清爽简约风格与 **Motrix Next** 的 Material Design 3 动效规范，打造一款界面现代、交互流畅自然的安卓下载管理器。

**核心原则**：
- **简约至上**：参考 Motrix 原版，界面元素精简，无冗余信息
- **动效驱动**：参考 Motrix Next，严格遵循 MD3 运动指南
- **状态感知**：参考 Motrix 托盘图标设计，图标与视觉元素随下载状态动态变化

### 4.2 页面布局

#### 4.2.1 主页面结构（竖屏手机适配）

```
┌──────────────────────────────┐
│  TopAppBar                   │
│  [Motrix Logo]  [主题切换] [⋮]│
├──────────────────────────────┤
│  [正在下载] [已完成] [出错]    │  ← TabRow (Primary)
├──────────────────────────────┤
│                              │
│  ┌────────────────────────┐  │
│  │ 📦 TaskCard            │  │
│  │ 文件名.pdf             │  │
│  │ ████████░░░░ 67%  2MB/s│  │  ← LinearProgressIndicator
│  │ 12.5MB / 18.6MB  3s    │  │
│  │ [⏸] [🗑️]              │  │
│  └────────────────────────┘  │
│                              │
│  ┌────────────────────────┐  │
│  │ 📦 TaskCard            │  │
│  │ ...                    │  │
│  └────────────────────────┘  │
│                              │
├──────────────────────────────┤
│  📊 Speed Chart (底部)       │  ← 折线图，最近60秒速度曲线
│  ▁▂▃▅▇▆▄▃▂▁▂▃▅▇▇▅▃▂       │
│  ↓ 2.1 MB/s  ↑ 0.0 KB/s    │
└──────────────────────────────┘
│  ＋                          │  ← FAB (新建下载)
```

#### 4.2.2 新建下载弹窗 (BottomSheet)

```
┌──────────────────────────────┐
│  新建下载                     │
├──────────────────────────────┤
│  URL / 磁力链接               │
│  ┌──────────────────────────┐│
│  │ https://...              ││
│  └──────────────────────────┘│
│                              │
│  保存到                       │
│  ┌──────────────────────────┐│
│  │ /sdcard/Download/Motrix ▾││
│  └──────────────────────────┘│
│                              │
│  文件名（可选）               │
│  ┌──────────────────────────┐│
│  │                          ││
│  └──────────────────────────┘│
│                              │
│  高级选项 ▾                   │
│  ┌──────────────────────────┐│
│  │ 分片数: [16]             ││
│  │ User-Agent: [默认]       ││
│  │ Referer: [可选]          ││
│  │ Headers: [可选]          ││
│  └──────────────────────────┘│
│                              │
│  [     提交下载     ]         │
└──────────────────────────────┘
```

#### 4.2.3 设置页面

参考 Motrix 的偏好设置面板，分组展示：

- **基础设置**：下载目录、最大任务数、分片数、限速
- **BT 设置**：DHT、LPD、Peer Exchange、监听端口、Tracker 自动更新
- **界面设置**：主题模式、通知设置
- **高级设置**：UA、代理、RPC 端口

#### 4.2.4 任务详情页面

点击任务卡片进入详情页，展示：
- 文件列表（BT 可选择性下载）
- 连接信息（种子数、连接数）
- 速度历史曲线
- 错误信息（如有）

### 4.3 交互与动效规范

全面参考 **Motrix Next 的 Material Design 3 动效规范**，定义统一的动画参数。

#### 4.3.1 缓动曲线

| 用途 | 缓动曲线 | Compose 实现 |
|------|---------|-------------|
| 标准进入 | `cubic-bezier(0.05, 0.7, 0.1, 1.0)` | `EaseOutCubic` 变体 |
| 标准退出 | `cubic-bezier(0.3, 0.0, 0.8, 0.15)` | `EaseInCubic` 变体 |
| 强调性缓动 | `cubic-bezier(0.2, 0.0, 0, 1.0)` | MD3 Emphasized |
| 标准缓动 | `cubic-bezier(0.2, 0.0, 0, 1.0)` 短版 | MD3 Standard |
| 减速缓动 | `cubic-bezier(0, 0, 0, 1)` | `EaseOut` |

**Compose 定义**：

```kotlin
// MD3 Emphasized Easing (参考 Motrix Next)
val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
val StandardAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
val StandardDecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
```

#### 4.3.2 非对称动画时长

参考 MD3 运动指南，进入动画稍长，退出动画稍短：

| 动画类型 | 进入时长 | 退出时长 | 缓动 |
|---------|---------|---------|------|
| 页面切换 | 500ms | 350ms | Emphasized |
| 容器变换 | 500ms | 350ms | Emphasized |
| 模态弹窗 | 350ms | 250ms | Standard |
| FAB 展开 | 350ms | 200ms | Emphasized |
| 卡片出现 | 300ms | 200ms | Standard |
| 列表项 | 250ms | 200ms | Standard |
| 微交互（按钮按压） | 100ms | 100ms | Standard |
| Tab 切换指示器 | 300ms | 300ms | Emphasized |

#### 4.3.3 弹簧动画参数

参考 MD3 弹簧动画规范和 Motrix Next 的弹簧动画实现：

| 场景 | dampingRatio | stiffness | 说明 |
|------|-------------|-----------|------|
| 标准空间（默认） | 0.9 | 700 | 平衡的空间动画 |
| 标准空间（快速） | 0.9 | 1400 | 快速空间变化 |
| 标准空间（慢速） | 0.9 | 300 | 温和的空间变化 |
| 表现性空间（默认） | 0.8 | 380 | 带弹性的空间动画 |
| 表现性空间（快速） | 0.6 | 800 | 明显弹性的快速动画 |
| 表现性空间（慢速） | 0.8 | 200 | 温和的弹性动画 |
| 效果动画（默认） | 0.7 | 400 | 通用效果弹簧 |
| 效果动画（快速） | 0.6 | 800 | 快速效果弹簧 |
| 效果动画（慢速） | 0.8 | 200 | 慢速效果弹簧 |

**Compose 弹簧定义**：

```kotlin
// 标准 MD3 弹簧
val StandardSpatial = SpringSpec<Float>(dampingRatio = 0.9f, stiffness = 700f)
val ExpressiveSpatial = SpringSpec<Float>(dampingRatio = 0.8f, stiffness = 380f)
val ExpressiveEffect = SpringSpec<Float>(dampingRatio = 0.7f, stiffness = 400f)
```

#### 4.3.4 具体动效场景

1. **任务卡片出现/消失**：使用 `AnimatedVisibility` + ExpressiveSpatial 弹簧，卡片从底部滑入并淡入
2. **进度条更新**：使用 `animateFloatAsState` + StandardDecelerate，进度变化平滑过渡
3. **Tab 切换**：指示器使用 `animateDpAsState` + EmphasizedEasing，300ms
4. **FAB 点击**：使用 `animateFloatAsState` + 弹簧（dampingRatio=0.6, stiffness=800），轻微缩放回弹
5. **BottomSheet 展开**：使用 EmphasizedDecelerate，350ms 进入
6. **手势删除**：使用 `animateOffset` + ExpressiveSpatial，卡片滑出后其余卡片弹簧式重排
7. **速度曲线图**：使用 `PathInterpolator`，平滑贝塞尔曲线连接数据点
8. **主题切换**：使用 `AnimatedContent` + EmphasizedEasing，500ms 渐变过渡

### 4.4 主题适配

参考 Motrix 的深色/浅色主题切换，基于 Material Design 3 动态取色。

#### 4.4.1 色彩方案

**浅色主题 (Light)**：

| 角色 | 颜色 | 色值 | 用途 |
|------|------|------|------|
| Primary | 蓝色 | #4A90D9 | 主色调，FAB、Tab 指示器 |
| OnPrimary | 白色 | #FFFFFF | Primary 上的文字 |
| PrimaryContainer | 浅蓝 | #D4E5F9 | 卡片背景、选中态 |
| Surface | 白色 | #FAFAFA | 页面背景 |
| SurfaceVariant | 浅灰 | #F0F0F0 | 卡片背景 |
| OnSurface | 深灰 | #1C1C1E | 主文字 |
| OnSurfaceVariant | 中灰 | #8E8E93 | 次要文字 |
| Outline | 浅灰 | #E5E5EA | 分隔线 |
| Error | 红色 | #FF3B30 | 错误状态 |
| Success | 绿色 | #34C759 | 完成状态 |

**深色主题 (Dark)**：

| 角色 | 颜色 | 色值 | 用途 |
|------|------|------|------|
| Primary | 亮蓝 | #6CB4EE | 主色调 |
| OnPrimary | 深色 | #003366 | Primary 上的文字 |
| PrimaryContainer | 深蓝 | #1A3A5C | 卡片背景、选中态 |
| Surface | 深色 | #1C1C1E | 页面背景 |
| SurfaceVariant | 深灰 | #2C2C2E | 卡片背景 |
| OnSurface | 白色 | #F2F2F7 | 主文字 |
| OnSurfaceVariant | 浅灰 | #8E8E93 | 次要文字 |
| Outline | 深灰 | #38383A | 分隔线 |
| Error | 亮红 | #FF453A | 错误状态 |
| Success | 亮绿 | #30D158 | 完成状态 |

#### 4.4.2 主题切换实现

- 支持 `system`（跟随系统）、`light`、`dark` 三种模式
- 使用 `isSystemInDarkTheme()` + 用户偏好组合判断
- 切换时使用 `AnimatedContent` 实现平滑过渡
- 通过 DataStore 持久化用户选择

### 4.5 图标系统

综合参考 **Motrix 原版的状态感知图标** 与 **Motrix Next 的 Naive UI 图标风格**。

#### 4.5.1 应用图标

- 主图标采用 Motrix 经典的「M」字母 + 下载箭头组合
- 圆角方形，符合 Android Adaptive Icon 规范
- 前景：白色「M」+ 箭头，背景：品牌蓝色渐变

#### 4.5.2 状态感知图标

参考 Motrix 托盘图标的状态切换设计，在安卓上实现通知栏图标的状态感知：

| 下载状态 | 图标描述 | 颜色 |
|---------|---------|------|
| 空闲（无任务） | 「M」字母 | 灰色 |
| 下载中 | 「M」+ 向下箭头动画 | 蓝色 |
| 全部完成 | 「M」+ 对勾 | 绿色 |
| 有错误 | 「M」+ 感叹号 | 红色 |
| 暂停 | 「M」+ 暂停符号 | 黄色 |

**实现方式**：
- 使用 `VectorDrawable` 定义各状态图标
- 通过 `NotificationCompat.Builder.setSmallIcon()` 动态切换
- 下载中状态可使用 `AnimatedVectorDrawable` 实现箭头脉冲动画

#### 4.5.3 界面内图标风格

参考 Motrix Next 使用的 Naive UI 图标风格：
- 采用 **Material Symbols (Rounded)** 作为基础图标集
- 线条粗细统一 1.5px，圆角风格
- 图标大小规范：24dp（标准）、20dp（紧凑）、40dp（列表图标）
- 颜色跟随主题，使用 `colorScheme.onSurfaceVariant` 等语义色

| 功能 | 图标 | Material Symbol 名称 |
|------|------|---------------------|
| 新建下载 | ＋ | add |
| 暂停 | ⏸ | pause |
| 继续 | ▶ | play_arrow |
| 删除 | 🗑️ | delete |
| 设置 | ⚙️ | settings |
| 下载 | ↓ | download |
| 上传 | ↑ | upload |
| 文件夹 | 📁 | folder |
| 链接 | 🔗 | link |
| Torrent | 🧲 | torrent (自定义) |
| 磁力 | 🧲 | magnet_on (自定义) |
| 速度 | 📊 | speed |
| 主题 | 🌓 | dark_mode / light_mode |
| 关闭 | ✕ | close |
| 更多 | ⋮ | more_vert |
| 复制 | 📋 | content_copy |
| 分享 | ↗ | share |

---

## 5. 第三方库选型清单

| 类别 | 库名 | 版本 | 选型理由 |
|------|------|------|---------|
| **UI 框架** | Jetpack Compose BOM | 2024.12+ | Android 现代声明式 UI，原生支持 MD3 |
| **导航** | Navigation Compose | 2.8+ | Compose 原生导航，支持 Deep Link |
| **状态管理** | ViewModel + StateFlow | 2.8+ | 官方推荐，生命周期感知 |
| **依赖注入** | Hilt | 2.51+ | Google 官方 DI，编译期检查 |
| **数据库** | Room | 2.6+ | Jetpack ORM，支持 Flow，类型安全 |
| **网络** | OkHttp | 4.12+ | 成熟稳定的 HTTP 客户端，支持 WebSocket |
| **JSON** | Kotlinx Serialization | 1.6+ | Kotlin 原生序列化，编译期生成，无反射 |
| **协程** | Kotlinx Coroutines | 1.8+ | 官方异步方案，与 Flow 深度集成 |
| **后台任务** | WorkManager | 2.9+ | Jetpack 后台任务调度，兼容 Doze 模式 |
| **数据存储** | DataStore Preferences | 1.1+ | 替代 SharedPreferences，协程友好 |
| **图表** | Vico | 2.0+ | Compose 原生图表库，性能优秀，支持折线图 |
| **权限** | Accompanist Permissions | 0.36+ | Compose 权限请求封装 |
| **日志** | Timber | 5.0+ | 轻量日志库，Release 自动裁剪 |
| **Aria2 进程** | aria2c (Native Binary) | 1.37+ | 预编译 ARM64/ARMv7 二进制，APK 内置 |
| **Torrent 解析** | libtorrent4j | 2.x | Java 封装的 libtorrent-rasterbar，用于解析 Torrent 文件元数据 |
| **磁力链接解析** | 自研 | - | 解析 magnet:? xt=urn:btih:... 格式 |
| **文件选择** | System File Picker | API 33+ | 系统原生文件选择器，适配分区存储 |
| **通知** | NotificationCompat | 1.3+ | 兼容库，支持前台服务通知 |
| **速度格式化** | 自研 | - | 基于 ICU 的文件大小/速度格式化 |

---

## 6. 集成 Aria2 vs 自研 HTTP 方案对比与推荐

### 6.1 方案对比

| 维度 | 集成 Aria2 | 自研 HTTP 下载引擎 |
|------|-----------|-----------------|
| **协议支持** | HTTP/HTTPS/FTP/BitTorrent/Magnet 全协议 | 仅 HTTP/HTTPS，BT/磁力需额外实现 |
| **开发成本** | 低（封装 RPC 调用） | 极高（需实现多线程、断点续传、BT 协议栈） |
| **成熟度** | 极高（20年历史，久经考验） | 低（需自行处理各种边界情况） |
| **APK 体积** | +5~8MB（aria2c 二进制） | +0MB（纯 Kotlin） |
| **内存占用** | aria2c 进程约 10~30MB | 纯 JVM，更可控 |
| **性能** | C 实现，极高 | Kotlin 实现，足够但不如 C |
| **维护成本** | 低（跟随上游更新） | 高（需持续修复协议兼容性） |
| **BT 支持** | 原生完整支持 | 需集成 libtorrent 或自研 |
| **磁力链接** | 原生支持 | 需额外实现 DHT/PEX |
| **可定制性** | 中（通过 RPC 配置） | 高（完全控制） |
| **进程管理** | 需管理 aria2c 子进程 | 无额外进程 |

### 6.2 推荐：集成 Aria2

**理由**：

1. **全协议支持**：Motrix 的核心价值在于支持 HTTP/FTP/BT/Magnet，自研引擎无法在合理开发周期内覆盖
2. **成熟稳定**：Aria2 是业界最成熟的开源下载引擎，Motrix 原版和 Motrix Next 均基于此
3. **开发效率**：封装 RPC 调用远比自研引擎高效，可集中精力在 UI/UX 上
4. **与参考项目一致**：Motrix 原版和 Motrix Next 均使用 Aria2（Next 使用 Aria2 Next 分支），保持架构一致性

**APK 体积增加的权衡**：+5~8MB 对于全协议下载器而言完全可接受。

**实现策略**：
- APK 内置 aria2c 二进制（arm64-v8a + armeabi-v7a）
- 首次启动时解压到应用私有目录并赋予执行权限
- 通过 JSON-RPC over WebSocket 与 aria2c 通信
- 应用退出时优雅关闭 aria2c 进程

---

## 7. 后台任务与恢复机制设计

### 7.1 架构设计

```
┌──────────────────────────────────────────────┐
│                  App Process                  │
│  ┌─────────┐  ┌──────────────┐  ┌────────┐ │
│  │ViewModel│←→│TaskRepository│←→│Room DB  │ │
│  └─────────┘  └──────┬───────┘  └────────┘ │
│                      │                       │
│               ┌──────┴───────┐               │
│               │ Aria2Engine  │               │
│               │ (RPC Client) │               │
│               └──────┬───────┘               │
│                      │ JSON-RPC/WS           │
├──────────────────────┼───────────────────────┤
│               aria2c Process (Native)         │
└──────────────────────────────────────────────┘
         │
    ┌────┴────┐
    │WorkManager│ ← 定期同步状态
    │  Worker  │ ← 重启 aria2c（如需）
    └─────────┘
```

### 7.2 前台服务 (Foreground Service)

- **用途**：确保下载过程中应用不被系统杀死
- **通知**：显示当前下载进度、速度，支持暂停/恢复操作
- **生命周期**：有活跃下载时启动，全部完成/暂停后停止
- **实现**：`DownloadService` 继承 `android.app.Service`，`startForeground()`

### 7.3 WorkManager Worker

- **用途**：应用被杀后恢复下载状态
- **约束**：`Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)`
- **策略**：
  1. 应用启动时检查是否有未完成的任务
  2. 如有，启动前台服务并恢复 aria2c 进程
  3. 通过 Room DB 中的任务状态判断哪些需要恢复
  4. aria2c 自身的 session 文件保证断点续传

### 7.4 aria2c 进程管理

```kotlin
class Aria2ProcessManager {
    /** 启动 aria2c 进程 */
    fun startAria2Process(config: EngineConfig): Result<Unit>

    /** 停止 aria2c 进程 */
    fun stopAria2Process(): Result<Unit>

    /** 检查 aria2c 是否存活 */
    fun isAria2Running(): Boolean

    /** 监听进程退出事件 */
    fun observeProcessState(): Flow<ProcessState>
}
```

**关键实现细节**：
- aria2c 二进制解压到 `context.applicationInfo.dataDir/aria2/`
- 使用 `Runtime.exec()` 启动进程
- 通过 `--save-session` 和 `--save-session-interval` 保存会话
- 通过 `--input-file` 恢复上次会话
- 通过 `--enable-rpc` 和 `--rpc-listen-port` 开启 RPC
- 通过 `--rpc-secret` 设置认证密钥

### 7.5 状态恢复流程

```
App 启动
  ↓
检查 Room DB 中是否有 state=ACTIVE 或 state=PAUSED 的任务
  ↓ (有)
启动 Aria2ProcessManager.startAria2Process()
  ↓
Aria2Engine.initialize() → 连接 RPC
  ↓
通过 RPC 查询 aria2c 内部状态，与 DB 同步
  ↓
启动前台服务 DownloadService
  ↓
恢复 WebSocket 监听
```

### 7.6 省电与 Doze 模式适配

- 使用 `WakeLock` 在下载期间保持 CPU 唤醒（仅在前台服务活跃时）
- 在 Doze 模式下，前台服务仍可运行，但网络可能受限
- 使用 `WorkManager` 的 `setRequiresDeviceIdle(false)` 确保不在 Doze 时调度
- 用户可在系统设置中将应用加入电池优化白名单

---

## 8. 磁力链接处理流程

### 8.1 完整流程

```
用户输入磁力链接 / 分享到 Motrix
  ↓
解析 magnet URI：
  - xt (exact topic): urn:btih:<info_hash>
  - dn (display name): 可选文件名
  - tr (tracker): 可选 Tracker 列表
  - xl (exact length): 可选文件大小
  ↓
验证 info_hash 格式（SHA-1 / Base32）
  ↓
调用 Aria2Engine.addMagnetTask()
  ↓ (内部)
Aria2 RPC: aria2.addUri(["magnet:?xt=..."], options)
  ↓
aria2c 开始获取种子元数据
  ↓ (通过 WebSocket 事件通知)
事件: DownloadStart → 获取元数据中...
  ↓
元数据获取完成 → aria2c 自动开始下载
  ↓ (如果启用了 Tracker 自动更新)
合并用户配置的 Tracker 列表
  ↓
下载进行中... (状态通过 WebSocket 实时推送)
  ↓
下载完成 → 事件: DownloadComplete
```

### 8.2 磁力链接解析

```kotlin
data class MagnetUri(
    val infoHash: String,           // 40位十六进制 SHA-1
    val displayName: String?,       // dn 参数
    val trackers: List<String>,     // tr 参数列表
    val exactLength: Long?,         // xl 参数
    val webSeeds: List<String>      // ws 参数
)

object MagnetUriParser {
    fun parse(uri: String): Result<MagnetUri>
    fun isValidMagnetUri(uri: String): Boolean
}
```

### 8.3 Intent Filter 配置

在 `AndroidManifest.xml` 中注册 Intent Filter，支持：
- `magnet:` scheme 的 Intent
- 浏览器分享的链接
- 剪贴板监听（可选，需用户授权）

### 8.4 Tracker 自动更新

- 默认从 `ngosang/trackerslist` 等公开列表获取最佳 Tracker
- 使用 WorkManager 定期更新（默认每日）
- 更新后通过 `aria2.changeOption()` 为活跃的 BT 任务追加 Tracker
- Tracker 列表缓存在 Room DB 中

---

## 9. 安卓版本兼容性要点

### 9.1 最低 API 与目标 API

| 项目 | 值 | 说明 |
|------|---|------|
| minSdk | 34 (Android 14) | 按需求最低 Android 14 |
| targetSdk | 35 (Android 15) | 最新目标 API |
| compileSdk | 35 | 最新编译 SDK |

### 9.2 关键兼容性要点

#### 9.2.1 前台服务 (Android 14+)

- Android 14 要求前台服务必须声明 `foregroundServiceType`
- 下载服务需声明 `foregroundServiceType="dataSync"`
- 需在 Manifest 中声明 `android.permission.FOREGROUND_SERVICE_DATA_SYNC`

#### 9.2.2 分区存储 (Scoped Storage)

- Android 11+ 强制分区存储
- 下载目录策略：
  - 默认使用 `MediaStore.Downloads` 外部存储
  - 用户可选择自定义目录（通过 `SAF` 框架获取持久 URI 权限）
  - aria2c 进程需要可写路径，使用 `context.getExternalFilesDir()` 或 SAF 授权路径
- 使用 `MediaStore` 扫描已下载文件，使其在系统文件管理器中可见

#### 9.2.3 通知权限 (Android 13+)

- Android 13+ 需要运行时请求 `POST_NOTIFICATIONS` 权限
- 前台服务通知不受此限制，但仍建议请求

#### 9.2.4 网络安全

- Android 9+ 默认禁止明文 HTTP
- aria2c RPC 使用本地 WebSocket，需配置 `network_security_config.xml` 允许 localhost
- 用户下载 HTTP 资源由 aria2c 处理，不受应用网络安全配置影响

#### 9.2.5 Battery Optimization

- 引导用户将应用加入电池优化白名单
- 使用 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Intent

#### 9.2.6 Native Binary 执行

- aria2c 作为 Native Binary 需要可执行权限
- 解压到 `context.applicationInfo.dataDir` 下的子目录（无需 `EXECUTE` 存储权限）
- 使用 `chmod 755` 赋予执行权限

#### 9.2.7 ABI 分割

- 支持 `arm64-v8a` 和 `armeabi-v7a` 两种架构
- 使用 APK Split 或 App Bundle 减小单 APK 体积
- GitHub Action 构建 App Bundle 以支持按架构分发

---

## 10. GitHub Action 自动构建 APK 方案设计

### 10.1 整体方案

使用 GitHub Actions 实现 CI/CD 自动构建，支持 Debug 和 Release 两种构建类型。

### 10.2 工作流设计

#### 10.2.1 触发条件

| 触发方式 | 条件 | 构建类型 |
|---------|------|---------|
| Push | `main` / `master` 分支 | Debug APK |
| Tag | `v*` 模式（如 v1.0.0） | Debug + Release APK |
| 手动 | `workflow_dispatch` | Debug + Release APK |
| PR | 任意分支 → main | Debug APK（仅检查编译） |

#### 10.2.2 运行环境

| 项目 | 值 |
|------|---|
| OS | `ubuntu-latest` |
| JDK | 17 |
| Node.js | 24（强制） |
| Gradle | 8.7+（通过 wrapper） |

#### 10.2.3 构建步骤

```
1. Checkout 代码
2. 设置 JDK 17 (temurin)
3. 设置 Node.js 24
4. 缓存 Gradle (~/.gradle/caches, ~/.gradle/wrapper)
5. 赋予 Gradle Wrapper 执行权限
6. 构建 Debug APK (./gradlew assembleDebug)
7. 构建 Release APK (./gradlew assembleRelease) [仅 tag / 手动触发]
8. 上传 Debug APK 作为 Artifact
9. 上传 Release APK 作为 Artifact [仅 tag / 手动触发]
10. [可选] 创建 GitHub Release 并上传 APK [仅 tag 触发]
```

#### 10.2.4 Release APK 签名

通过 GitHub Secrets 配置签名密钥：

| Secret 名称 | 说明 |
|-------------|------|
| `KEYSTORE_BASE64` | Base64 编码的 .jks/.keystore 文件 |
| `KEYSTORE_PASSWORD` | KeyStore 密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

**签名流程**：
1. 将 `KEYSTORE_BASE64` 解码为文件
2. 在 `signingConfigs` 中引用环境变量
3. Release 构建自动签名

#### 10.2.5 产物保存

| 产物 | 保留天数 | 路径 |
|------|---------|------|
| Debug APK | 30 天 | `app/build/outputs/apk/debug/*.apk` |
| Release APK | 90 天 | `app/build/outputs/apk/release/*.apk` |
| Mapping 文件 | 90 天 | `app/build/outputs/mapping/release/mapping.txt` |

### 10.3 工作流文件结构

```
.github/
└── workflows/
    └── android.yml      # 主构建工作流
```

### 10.4 关键配置说明

- **Node.js 24**：某些 Gradle 插件或构建脚本可能依赖 Node.js，强制指定 `node-version: '24'`
- **Gradle 缓存**：使用 `actions/cache` 缓存 Gradle 依赖，加速后续构建
- **APK 命名**：构建后重命名为 `Motrix-{version}-{variant}-{abi}.apk` 格式
- **多架构**：通过 `split` 产出不同 ABI 的 APK，或使用 App Bundle

---

## 11. 附录：手动编译环境搭建与打包 APK 教程

### 11.1 环境要求

| 软件 | 最低版本 | 推荐版本 |
|------|---------|---------|
| JDK | 17 | 17 (Temurin) |
| Android SDK | 34 | 35 |
| Node.js | 20+ | 24 |
| Git | 2.x | 最新 |
| Android Studio | 2023.1+ | 2024.1+ (Iguana) |

### 11.2 环境搭建步骤

#### 步骤 1：安装 JDK 17

```bash
# macOS (Homebrew)
brew install openjdk@17

# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# Windows
# 下载安装 Temurin JDK 17: https://adoptium.net/
```

设置 `JAVA_HOME` 环境变量指向 JDK 17 安装路径。

#### 步骤 2：安装 Android SDK

1. 安装 Android Studio（推荐）或仅安装 Command Line Tools
2. 通过 SDK Manager 安装：
   - Android SDK Platform 35
   - Android SDK Build-Tools 35.0.0
   - Android SDK Platform-Tools
   - NDK (Side by side) 27.0.12077973（编译 aria2c native 库时需要）
3. 设置 `ANDROID_HOME` 环境变量

#### 步骤 3：安装 Node.js 24

```bash
# 使用 nvm (推荐)
nvm install 24
nvm use 24

# macOS (Homebrew)
brew install node@24

# Ubuntu/Debian
curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -
sudo apt install nodejs
```

#### 步骤 4：克隆代码

```bash
git clone https://github.com/<your-org>/motrix-android.git
cd motrix-android
```

#### 步骤 5：配置签名密钥（Release 构建）

1. 生成签名密钥：

```bash
keytool -genkey -v -keystore motrix-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias motrix
```

2. 将 `motrix-release.jks` 放入项目根目录（或自定义路径）
3. 在项目根目录创建 `keystore.properties`：

```properties
storeFile=motrix-release.jks
storePassword=<your-password>
keyAlias=motrix
keyPassword=<your-password>
```

> **注意**：`keystore.properties` 和 `.jks` 文件已添加到 `.gitignore`，切勿提交到版本库

#### 步骤 6：构建 Debug APK

```bash
# 赋予 Gradle Wrapper 执行权限
chmod +x gradlew

# 构建 Debug APK
./gradlew assembleDebug

# 产物路径
# app/build/outputs/apk/debug/app-debug.apk
```

#### 步骤 7：构建 Release APK

```bash
# 构建 Release APK（需配置签名）
./gradlew assembleRelease

# 产物路径
# app/build/outputs/apk/release/app-release.apk
```

#### 步骤 8：安装到设备

```bash
# 通过 adb 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 或通过 Android Studio 直接运行
```

### 11.3 常见问题

| 问题 | 解决方案 |
|------|---------|
| `SDK location not found` | 创建 `local.properties`，写入 `sdk.dir=/path/to/android/sdk` |
| `Execution failed for task ':app:kaptDebugKotlin'` | 确保 JDK 版本为 17，检查 `JAVA_HOME` |
| `Aria2 binary not found` | 运行 `./gradlew extractAria2Binary` 或手动将 aria2c 放入 `app/src/main/assets/` |
| `Keystore file not found` | 检查 `keystore.properties` 中 `storeFile` 路径 |
| `Namespace not specified` | 确保 `build.gradle.kts` 中已设置 `namespace` |

### 11.4 清理构建

```bash
./gradlew clean
```

---

**阶段一文档输出完毕。请确认方案，确认后我将进入阶段二提供完整代码实现。**
