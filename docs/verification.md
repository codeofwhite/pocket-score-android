# 构建与验证记录

以下为 2026-10-30 首次交付记录，并非本次仓库整理时重新运行的测试。

## 本次交付验证（2026-10-30）

- `build.ps1`：`testDebugUnitTest assembleDebug` 成功，9 项测试，0 失败，0 错误，0 跳过。
- `apksigner verify --verbose`：签名核验成功，APK Signature Scheme v2。
- `aapt dump badging`：包名 cn.pocketscore，版本 1.0.0，最低 API 26，目标 API 35。
- APK 大小 8,806,582 字节；SHA-256：4AE68DD6FDBB0A2E2E0242E0CFB36EB0C519344074EDB5670C1A0CCD1CA0F763。
- 清单及两个 XML 资源解析通过，setup.ps1/build.ps1 初始语法检查通过，最终 build.ps1 实际执行成功。
- 尚未通过模拟器或真机验证触控、旋转、系统文件选择器、分享接收和实际 PDF 外观；分页测试只验证小节覆盖及页数规划。
- 构建提示 libandroidx.graphics.path.so 无法剥离调试符号，已原样打包，不影响本次构建成功。

## 新增文件及数据流

本项目从空目录创建，没有删除或替换现有项目的代码。安卓原生界面调用 ScoreEditor 更新不可变乐谱；验证后 ScoreStorage 原子保存。编辑预览与 PDF 导出都调用 ScoreRenderer。

| 新增文件 | 作用与必要性 |
| --- | --- |
| settings.gradle.kts | 注册 app 模块及官方依赖仓库，让 Gradle 定位应用和插件。 |
| build.gradle.kts | 固定 AGP 与 Kotlin/Compose 插件版本。 |
| gradle.properties | 配置 JVM 内存、UTF-8 与 AndroidX。 |
| .gitignore | 排除项目本地工具、缓存、SDK 路径及构建产物。 |
| app/build.gradle.kts | 声明安卓版本、应用标识及 Compose/JUnit/JSON 依赖。 |
| app/src/main/AndroidManifest.xml | 注册启动界面和受限缓存文件分享 Provider，不申请网络或广泛存储权限。 |
| app/src/main/res/xml/file_paths.xml | 只允许分享 exports 缓存目录中的文件。 |
| app/src/main/res/drawable/ic_score.xml | 新增应用图标。 |
| app/src/main/java/cn/pocketscore/MainActivity.kt | 新增乐谱库、触屏编辑、设置、导入导出、后台 PDF 处理及错误提示；导入建立新副本，避免覆盖已有乐谱。 |
| app/src/main/java/cn/pocketscore/Score.kt | 定义乐器、音符、和弦、小节及工程，统一验证时值、范围和重叠。 |
| app/src/main/java/cn/pocketscore/ScoreEditor.kt | 处理增删、同拍和弦、声部、撤销及 PDF 分页规划；拒绝重叠输入，防止静默丢音。 |
| app/src/main/java/cn/pocketscore/ScoreStorage.kt | 版本化 JSON 工程编码、验证与原子保存，写入失败时保留原文件。 |
| app/src/main/java/cn/pocketscore/ScoreRenderer.kt | 绘制 TAB/双行五线谱、谱号、休止符和升降还原号；共用于屏幕与 PDF，背景限于当前小节。 |
| app/src/main/java/cn/pocketscore/PdfExporter.kt | 将完整乐谱分页绘制为 A4 PDF，写入用户选择的文件或分享缓存。 |
| app/src/test/java/cn/pocketscore/ScoreTest.kt | 新增 9 项行为测试，覆盖关键编辑、验证、备份恢复与分页逻辑。 |
| setup.ps1 | 项目内下载 JDK/Gradle/SDK；连接截断时有界续传，连续无进展停止。SDK 工具可使用已有系统代理。 |
| build.ps1 | 设置进程内环境并运行测试和打包；使用较短临时路径解决 Windows AF_UNIX 套接字路径问题，Maven 官方地址直连。 |
| local.properties | 记录仅限本机的 SDK 路径，排除版本控制。 |
| README.md | 使用方法、构建步骤、实现范围和本次验证记录。 |

所有 JAVA_HOME、TEMP、TMP、GRADLE_USER_HOME、ANDROID_USER_HOME 及代理参数只作用于构建进程，不修改系统持久环境变量。Gradle 自动补齐了 SDK Platform-Tools v37.0.1，位于项目 .tools/android-sdk 内。

## 仓库整理检查（2026-11-01）

仅调整 README、忽略规则、换行配置并新增本文档；未修改应用和构建脚本功能，未重新执行安卓构建。

`git diff --cached --check` 指出原有 build.ps1 的文件末尾空行，以及 setup.ps1 的第 27 行尾部空格和文件末尾空行。此次保留原脚本，检查并非全绿；这些提示属于空白格式，不是本次编译结果。
