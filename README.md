# 口袋乐谱 · Pocket Score Android

用于手机离线创建、编辑乐谱并导出 PDF 的原生 Android 应用，使用 Kotlin 和 Jetpack Compose 开发。最低支持 Android 8.0（API 26）。

## 功能

- 吉他六线谱、尤克里里四线谱、钢琴双行五线谱。
- 音符、同拍和弦、休止符、左右手声部、增删小节及最多 100 步撤销。
- 全音至十六分音符时值，检查同声部时值重叠和小节容量。
- 曲名、作者、拍号与速度设置。
- 本地原子自动保存，JSON 工程备份和恢复；恢复时创建副本。
- A4 PDF 导出，通过安卓系统文件选择器保存或通过分享面板发送。

## 安装

本仓库包含源码，不跟踪 APK。按下方步骤构建后，将 `app/build/outputs/apk/debug/app-debug.apk` 传到手机安装。它是带调试签名的测试包，不是应用商店发行包。

## 使用

1. 新建乐谱并选择乐器。
2. 左右滑动查看谱面，点击选择输入位置；每格代表十六分音符。
3. 选择时值。弦乐选弦和品数；钢琴选左右手、音高、八度和升降号，再点击添加音符。
4. 同一位置添加不同弦或不同音高组成和弦；同弦再次添加会替换品数。默认光标停留原拍，可切换输入后自动前进。
5. 用箭头移动光标、添加休止符或增删小节。已有时值重叠时先删除原音符。
6. 导出 PDF 用于打印或分享；备份工程用于之后继续编辑。卸载前务必备份，PDF 不能恢复为工程。

## 构建

### 环境

- Windows 和 **PowerShell 7**（命令为 `pwsh`；Windows 自带的 PowerShell 5.1 不支持安装脚本使用的断点续传参数）。
- 网络可访问 Adoptium、Google、Gradle 和 Maven 官方仓库。
- 磁盘空间用于项目本地 JDK、SDK、Gradle 和依赖缓存。

在仓库根目录运行：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\setup.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

`setup.ps1` 在 `.tools/` 中下载 JDK 17、Gradle 8.13、Android 命令行工具，接受 SDK 许可并安装 SDK 35 与 Build Tools 35.0.0。Gradle 可能自动补齐所需 Platform-Tools。

`build.ps1` 执行 `testDebugUnitTest assembleDebug`。进程内配置工具路径、现有系统代理和较短的临时目录，不修改系统持久环境变量。SDK 路径保存在不跟踪的 `local.properties`。

项目当前提供 Windows 构建脚本，未提供 Gradle Wrapper 或跨平台构建脚本。

### 输出

- 安装包：`app/build/outputs/apk/debug/app-debug.apk`
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`

## 目录结构与数据流

```text
app/
  src/main/java/cn/pocketscore/
    MainActivity.kt      # 乐谱库、编辑界面、导入导出和分享
    Score.kt             # 乐谱模型与校验
    ScoreEditor.kt       # 编辑、撤销及分页规划
    ScoreStorage.kt      # JSON 编码与原子保存
    ScoreRenderer.kt     # 屏幕与 PDF 共用绘制器
    PdfExporter.kt       # A4 PDF 导出
  src/main/res/          # 图标及分享文件路径配置
  src/test/              # 编辑、校验、恢复和分页测试
build.ps1                # 测试与 APK 构建
setup.ps1                # 项目本地工具准备
docs/verification.md     # 首次交付的验证记录与文件说明
```

界面调用 `ScoreEditor` 更新不可变乐谱，校验通过后由 `ScoreStorage` 原子保存；预览和 PDF 导出共用 `ScoreRenderer`。

## 当前范围

支持标准定弦吉他 E-A-D-G-B-e、尤克里里 G-C-E-A；钢琴 C 大调 / A 小调及逐音升降号。单谱最多 256 小节，钢琴音高 C1–C7，品数 0–24。未填写区域不会自动补休止符。

暂不支持调号、附点、连音组、延音线、反复、自动连梁、播放、MusicXML 或 PDF 反向导入。

首次交付时 9 项单元测试通过、APK 签名核验通过；尚未通过模拟器或真机验证触控、旋转、文件选择器、分享接收及实际 PDF 外观。详细证据见[构建与验证记录](docs/verification.md)。
