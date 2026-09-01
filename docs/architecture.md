# 余音（LocalAudio）架构说明

本文描述当前代码的实际架构和运行边界，适用于当前 `1.2.0` 版本。项目是单模块 Android 应用，核心目标是通过系统文件选择器管理本地音频，并提供可持续的后台播放能力。

## 1. 总体结构

应用采用“Compose 单向状态流 + ViewModel 编排 + Repository 数据层 + 独立播放服务”的结构：

```text
MainActivity
    └── LocalAudioRoute
          ├── LocalAudioApp / Compose Screens / Dialogs
          │       └── AppEvent
          └── AppViewModel
                  ├── LibraryRepository ── MediaScanner ── SAF / MediaMetadataRetriever
                  ├── SettingsRepository ── SharedPreferences
                  ├── AutoSkipRepository
                  ├── DirectorySkipRepository
                  ├── RecycleBinRepository
                  └── PlaybackConnection
                          └── Binder
                              └── PlaybackService
                                    ├── PlaybackCoordinator
                                    ├── PlatformPlayer ── MediaPlayer / Audio Effects
                                    └── MediaSession / Playback Notification
```

界面层不直接操作文件、数据库或 `MediaPlayer`。普通业务事件由 `AppViewModel` 分发；播放命令通过 `PlaybackConnection` 发送给 `PlaybackService`，由服务内的 `PlaybackCoordinator` 串行处理。

## 2. 代码分层

源码根目录为 `app/src/main/java/com/localaudio/player/`：

| 包 | 主要职责 |
| --- | --- |
| `app` | `AppViewModel`、应用状态、事件/副作用、首页列表派生 |
| `data/model` | 音频、目录、扫描、自动跳过和回收站等不可变模型 |
| `data/library` | 音乐库、扫描结果、文件操作和回收站协调 |
| `data/scan` | 基于 SAF 的递归目录扫描、媒体元数据补充 |
| `data/database` | `SQLiteOpenHelper` 数据库及版本迁移 |
| `data/settings` | 播放和界面设置的读取、校验、持久化 |
| `data/skip` | 单曲自动跳过标记和目录级跳过规则 |
| `data/loudness` | 响度分析、响度缓存和归一化增益计算 |
| `playback` | 播放器服务、播放队列、播放状态、定时器和音频效果 |
| `ui` | Compose 页面、组件、对话框、主题和导航 |
| `di` | `AppContainer`，负责在应用进程内组装依赖 |

当前只有 `app` 一个 Gradle 模块。依赖主要是 Kotlin、Jetpack Compose、Material 3、AndroidX Lifecycle、Kotlin Coroutines、LZ4 和 Android 平台媒体能力；播放器没有引入 Media3，底层使用 Android `MediaPlayer`。

## 3. 应用启动与依赖组装

1. `LocalAudioApplication.onCreate()` 创建单例 `AppContainer`。
2. `AppContainer` 创建数据库、设置仓库、音乐库仓库、自动跳过仓库、响度仓库和播放快照存储。
3. `MainActivity` 通过 `AppViewModelFactory` 创建 `AppViewModel`，并注册系统文件夹选择器与 Android 13+ 通知权限请求。
4. `AppViewModel` 初始化时连接 `PlaybackService`。连接尚未建立时，`PlaybackConnection` 会暂存播放命令，服务绑定成功后按顺序发送。
5. `LocalAudioRoute` 使用生命周期感知的 `collectAsStateWithLifecycle()` 收集内容状态和播放状态，计算主题后交给 `LocalAudioApp` 渲染。

`MainActivity` 只处理 Activity 级系统能力：SAF 文件夹选择、通知权限、返回键、锁屏显示和系统栏颜色。业务状态由 `AppViewModel` 持有或转发。

## 4. UI 与状态流

### 4.1 状态分离

`AppViewModel` 对外暴露两条主要状态流：

- `contentState: StateFlow<AppUiState>`：导航、对话框、首页目录位置、首页列表、音乐库、设置、自动跳过和回收站。
- `playbackState: StateFlow<PlaybackState>`：播放队列、当前索引、播放位置、时长、播放/暂停状态、循环/随机模式和定时器状态。

播放进度由服务独立发布，不与整个应用内容状态绑定。这样播放进度更新不会强制音乐库、设置页和导航状态一起重组。

### 4.2 事件流

```text
Compose UI
    └── AppEvent
          └── AppViewModel.onEvent()
                ├── 更新导航或设置
                ├── 调用 Library/Skip/Recycle Repository
                ├── 发送 PlaybackCommand
                └── 发送 AppEffect（文件选择器、权限请求、Toast）
```

主页面由 `LocalAudioApp` 管理：首页、播放页和设置页使用 `HorizontalPager`；音乐库、自动跳过、回收站和均衡器属于设置下的二级页面，不放入主分页。页面状态通过 `SaveableStateHolder` 保留。

### 4.3 主要界面

- 首页：根文件夹/子目录导航、音频列表、当前播放定位、重命名和删除。
- 播放页：封面占位、进度控制、队列、播放模式、定时器、均衡器和自动跳过操作。
- 设置页：外观、播放、睡眠定时、自动跳过和音乐库入口。
- 二级页面：音乐库管理、自动跳过管理、回收站和均衡器。

## 5. 音乐库与 SAF 扫描

### 5.1 文件访问边界

用户通过 `ActivityResultContracts.OpenDocumentTree` 选择目录。`LibraryRepository.addFolder()` 使用 `takePersistableUriPermission()` 保存该目录的持久化读写授权；应用不依赖传统共享存储权限，也不主动扫描系统媒体库。

音乐库中的目录使用 tree URI 标识，音频使用基于 document URI 的规范化 `AudioItem.key` 标识。`AudioItem` 同时保存根目录 URI、目录相对路径、显示标题、来源文件夹、文件大小、最后修改时间和内容哈希。

### 5.2 扫描流程

```text
添加/重新扫描根目录
    └── LibraryRepository（扫描线程）
          └── MediaScanner.scanFolder()
                ├── SAF 按目录查询子文档
                ├── 递归识别目录和音频文件
                ├── MediaMetadataRetriever 读取时长
                ├── AudioHashCalculator 流式计算 xxHash64
                └── 回调扫描进度和批量结果
```

扫描器同时识别 `audio/*` MIME 类型和常见扩展名：MP3、M4A、AAC、FLAC、OGG、Opus、WAV、MKA、AMR、MP4。递归深度上限为 20；扫描中的目录会发布 `Idle`、`Scanning`、`Done` 或 `Failed` 状态。

扫描结果补充阶段使用两个详情线程，并按批次处理。若文件大小和修改时间未变且已有内容哈希，则复用缓存的时长和哈希；否则重新读取文件信息。内容哈希采用流式读取，不把整个音频文件载入内存。

同一个根目录再次扫描前会取消旧任务，并通过扫描 token 丢弃过期回调。扫描完成后，仓库会过滤回收站屏蔽项、更新自动跳过的显示快照、清理失效标记并替换该根目录的缓存结果。

## 6. 播放架构

### 6.1 服务边界

`PlaybackService` 是前台媒体播放服务，`exported=false`，声明 `mediaPlayback` 前台服务类型并返回 `START_STICKY`。它负责：

- 创建并持有 `PlaybackCoordinator` 和 `PlatformPlayer`。
- 通过本地 Binder 向 Activity 暴露 `StateFlow<PlaybackState>` 和播放命令入口。
- 创建 Android `MediaSession`，接收系统播放、暂停、上一曲、下一曲和拖动进度操作。
- 创建媒体播放通知，并在开始播放后进入前台服务。
- 在 Android 13+ 配合 `POST_NOTIFICATIONS` 权限显示播放通知。

### 6.2 播放协调器

`PlaybackCoordinator` 是播放业务的唯一协调者，所有命令和播放器回调都切换到主线程处理。它维护：

- 播放队列和当前索引。
- 当前播放意图、准备中的 seek 位置和播放器 generation。
- 上一曲/下一曲、顺序、单曲循环、列表循环和随机播放逻辑。
- 睡眠定时器、自动跳过、淡入、响度增益和播放位置持久化。

每次加载音频都会生成新的 generation，旧 `MediaPlayer` 的异步回调会被忽略，避免切歌后的过期回调改变当前状态。队列中的文件重命名或扫描更新后，`AppViewModel` 会发送 `ReplaceItem(s)`，当前 URI 变化时由协调器重新加载播放器。

### 6.3 平台播放器

`PlatformPlayer` 对 `MediaPlayer` 做最小封装：

- 使用媒体用途的 `AudioAttributes` 并管理音频焦点。
- 处理异步准备、seek 完成、播放完成和播放失败回调。
- 应用音量、响度归一化增益和均衡器设置。
- Android 9+ 优先使用 `DynamicsProcessing`；不可用时回退到传统 `Equalizer`。
- 负责释放播放器、音频焦点和音频效果资源。

播放协调器使用 Handler 定时检查播放进度、定时器和自动跳过。普通状态约每 500 ms 更新一次；淡入或响度平滑过渡期间提高到约 50 ms。播放快照和位置由 `PlaybackStore` 保存，以便下次启动恢复队列、当前曲目和位置。

## 7. 特殊播放功能

### 7.1 睡眠定时

`SleepTimer` 使用 `SystemClock.elapsedRealtime()` 计算剩余时间，避免系统墙上时间调整影响计时。定时器有两种来源：

- 手动定时：用户从播放页设置一次播放时长。
- 自动定时：开始播放或用户手动切换曲目时，根据设置启动或重启。

到期后可立即暂停，也可进入“播放完当前音频后暂停”状态。自动播放推进到下一曲时不会重复重置计时。

### 7.2 自动跳过

单曲标记保存为 `AutoSkipSegment`，以内容哈希关联音频，而不是只依赖 URI、标题或文件夹名，因此文件重命名或 URI 变化时仍有机会匹配原音频。仓库按内容哈希建立索引。

目录级规则保存为 `DirectorySkipRule`，以根文件夹 URI 和相对目录路径定位，分别表示跳过每首音频的开头和结尾。播放时协调器将单曲标记与目录规则合并、排序和合并区间，再使用二分查找判断当前进度是否落在需要跳过的区间内。

### 7.3 响度均衡与均衡器

- `LoudnessRepository` 在真正播放音频后按内容哈希懒加载分析任务。
- `LoudnessAnalyzer` 使用 `MediaExtractor` 和 `MediaCodec` 解码 PCM，计算响度和峰值，并将结果缓存到数据库。
- 播放时根据目标响度和峰值上限计算增益；切换曲目或分析结果变化时进行平滑过渡。
- 均衡器提供五个频段、多种预设和自定义增益，设置保存于 `SharedPreferences`，实际效果由 `PlatformPlayer` 应用。

## 8. 文件操作与回收站

首页的重命名和删除操作由 `LibraryRepository` 执行，并通过 `DocumentsContract` 操作 SAF 文档。

删除默认是软删除：回收站保存音频/目录记录，并通过 URI、document identity 和目录键阻止扫描重新加入。回收站支持按层级选择、还原和彻底清理：

- 还原：移除回收站记录，恢复目录可见性并触发重新扫描。
- 彻底清理：调用 `DocumentsContract.deleteDocument()` 删除源文件或源目录，成功后移除回收站记录。

重命名后，音乐库、当前播放队列、自动跳过快照、目录规则和回收站路径会同步更新相关显示和匹配信息。

## 9. 持久化设计

应用当前使用 SQLite、SharedPreferences 和一个 JSON 文件三种持久化方式：

| 存储 | 内容 |
| --- | --- |
| `library.db` | 音频缓存、自动跳过标记、响度分析结果、回收站音频和目录 |
| `SharedPreferences("local_audio")` | 已选文件夹、主题、播放设置、均衡器设置、首页位置、播放队列快照和播放位置 |
| `filesDir/directory_skip_rules.json` | 目录级开头/结尾跳过规则 |

`AudioDatabase` 当前版本为 8，新增表和字段通过 `onUpgrade()` 逐版本迁移。各 Repository 对外以 `StateFlow` 提供内存状态；音乐库、自动跳过、目录规则和回收站等涉及列表或文件的写入会在各自后台执行器中串行处理，并尽量合并过时快照，避免界面线程执行大规模 I/O。

## 10. 并发与生命周期约束

- Compose 和 `AppViewModel` 只负责状态收集、事件分发和界面派生计算。
- `PlaybackCoordinator` 的状态变化、命令和播放器回调统一在主线程串行处理。
- `LibraryRepository` 使用独立扫描、文件操作和持久化执行器；扫描任务可取消，进度回调需校验 token。
- `MediaScanner` 的媒体详情补充使用有界线程池。
- 自动跳过、目录规则和回收站仓库使用各自的串行执行器加载和持久化。
- 响度分析使用低优先级串行后台线程，并只保留有价值的最新分析请求。
- Activity 销毁不会直接停止播放；播放服务独立于界面连接存活。`AppViewModel` 清理时只关闭连接，服务在自身生命周期结束时释放播放器和协调器。

## 11. 修改代码时的边界

1. 新增界面交互时，优先增加 `AppEvent` / `SettingChange`，由 `AppViewModel` 编排，不在 Composable 中直接写仓库。
2. 新增播放行为时，通过 `PlaybackCommand` 进入 `PlaybackCoordinator`，不要从 Activity 或 Composable 直接操作 `MediaPlayer`。
3. 新增音乐库数据时，同时考虑扫描结果、缓存替换、重命名、回收站过滤和当前播放队列更新。
4. 新增持久化字段或表时，更新对应 Store、模型和 `AudioDatabase.onUpgrade()` 迁移逻辑。
5. 依赖 Android 平台能力时，应保持 `PlatformPlayer` 和服务边界，避免把平台资源泄漏到 UI 层。
