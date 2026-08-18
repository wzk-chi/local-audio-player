# 余音（LocalAudio）

本项目由ChatGPT开发。

当前版本：`1.1.0`

余音是一款面向 Android 的本地音乐播放器。它通过系统文件选择器读取用户授权的音乐文件夹，不依赖媒体库即可浏览目录并播放本地音频。

起因是睡觉时听书总是忘记开启定时关闭，需要自动定时功能，但找了一圈没找到，故借助AI开发了该软件，希望帮到大家。项目不完善，欢迎PR。

## 功能

- 使用 Android Storage Access Framework 添加一个或多个音乐文件夹
- 递归扫描文件夹并按目录浏览音频
- 支持 MP3、M4A、AAC、FLAC、OGG、Opus、WAV、MKA、AMR、MP4 等常见音频文件
- 播放队列、上一曲、下一曲、拖动进度、快进和快退
- 首页、播放和设置三个主页面支持左右滑动切换，页面切换跟手并带动画
- 首页播放小 bar 支持播放控制、上一曲、下一曲和拖动进度
- 顺序播放、列表循环、单曲循环和随机播放
- 定时暂停，以及“当前音频播放完后暂停”
- 浅色、深色和跟随系统主题
- 可选的锁屏显示

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX Lifecycle
- Kotlin Coroutines
- Android `MediaPlayer` 平台能力

## 环境要求

- Android Studio
- Android Studio 内置 JDK 17 或更高版本（当前使用 JDK 21 验证）
- Android 8.1（API 27）或更高版本


## 使用方式

1. 启动应用，在首页或设置中添加音乐文件夹。
2. 授予应用访问所选文件夹的权限。
3. 在首页进入目录并点击音频开始播放。
4. 在播放页管理进度、队列、播放模式和定时暂停。
5. 在设置中调整主题、首页显示、播放选项和音乐库扫描。

首页、播放页和设置页可以通过底部导航或左右滑动切换；媒体库管理是设置中的独立页面。

## 构建

在项目根目录执行。构建需要使用 Android Studio 内置 JDK 17 或更高版本；当前命令使用 Android Studio 内置 JDK 21：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleRelease
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Debug 版可直接安装到已连接的 Android 设备：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:installDebug
```

## 项目结构

```text
app/src/main/java/com/localaudio/player/
├── app/          应用状态、首页数据和事件
├── data/         音频模型、文件夹扫描、音乐库和设置持久化
├── playback/     平台播放器、播放队列、定时器和前台服务
└── ui/           Compose 页面、组件、主题和显示工具
```

`AppViewModel` 负责界面状态和应用事件，`PlaybackService` 负责 Android 服务边界，`PlaybackCoordinator` 负责播放状态和播放控制。

## 权限说明

- 音乐文件夹访问通过系统文件选择器授予，不需要申请传统存储权限。
- Android 13 及以上版本需要通知权限，以显示播放通知。
- 后台播放使用媒体播放类型的前台服务。

## 许可证

本项目采用 [MIT License](LICENSE) 开源。版权所有 © 2026 LocalAudio contributors。
