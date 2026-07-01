# Blackboard 参考资料索引（docs/references）

本文件夹存放**实装阶段需要随时查阅的详细参考资料**。分两类：

1. **内部参考（已归档，可直接依据实现）**：完全由本模组设计、不依赖外部框架细节的内容，已写成详尽文档。
2. **外部参考（待补充）**：依赖第三方框架/版本特定 API 的内容。**为避免臆测，这些必须由作者提供权威文档/示例后才归档。** 在拿到资料前，实现代码中相关处用 `// TODO(ref): <条目>` 标注，不凭空实现。

> 维护约定：实现过程中若发现某条参考与真实 API/行为不符，**立即修正对应文档**并在文末「修订记录」追加一行（日期 + 变更摘要）。

---

## 一、内部参考（已归档）

| 文件 | 覆盖内容 | 状态 |
| --- | --- | --- |
| [internal-core-api.md](internal-core-api.md) | 注册表 `SimpleRegistry`、事件总线 `EventHook`、`Question`/`QuestionGenerator`/`BlackboardType` 数据模型与参考实现、上下文对象、选题算法、奖励流程、渲染接口、NBT 序列化模式、注册生命周期 | ✅ 已归档 |
| [answer-format-and-validation.md](answer-format-and-validation.md) | 聊天作答解析 `AnswerFormat`/`ParsedAnswer` 默认语法、内置校验器 `Validators`（文字/数值/矩阵/表达式）的归一化与文本语法、`Component` 取纯文本 | ✅ 已归档 |
| [calculus-module.md](calculus-module.md) | 微积分题库模块：表达式 `Expr` AST 与求导/化简/求值、LaTeX 生成 `toLatex` 与答案解析（LaTeX 归一化 + infix 文法 → `ExpressionEvaluator`）、各题型出题逻辑（求导/不定积分/定积分/极限，逆向积分法）、数值抽样判题（含 +C 两点差）、三类拆分（`CalculusExpr`/`CalculusProblems`/`CalculusGenerators`）、难度分级与两阶段实装 | 📝 设计（待实装） |

> 内部参考中标注 `【平台边界】` 的位置依赖加载器/版本特定 API（如 `Component` 的 NBT/JSON 编解码），其**具体实现**归入下方「外部参考」，在拿到资料前不实现。

---

## 二、外部参考（已归档：构建/Kotlin/渲染）

基于作者提供的资料（Stonecutter 官方 wiki、官方/rotgruengelb 模板、KotlinLangForge、ApricityUI 官方文档）已核实并归档：

| 文件 | 覆盖内容 | 状态 |
| --- | --- | --- |
| [stonecutter.md](stonecutter.md) | Stonecutter 插件/版本节点、settings/controller/build 机制、版本化注释语法、常量/swap/替换、多加载器（split buildscript） | ✅ 已归档 |
| [multiloader-build.md](multiloader-build.md) | **Architectury Loom（flat）+ Stonecutter** 的真实构建（据 KLF testmod）：Forge 1.20.1 + NeoForge 1.21.1 单一共享 `build.gradle.kts`、`loom.platform` 节点属性、`modImplementation` 接入 KLF、属性布局、`mods.toml`/`neoforge.mods.toml`、Java 版本 | ✅ 已归档（已由 MDG 更正为 Loom）|
| [kotlinlangforge.md](kotlinlangforge.md) | KLF：`modLoader="klf"`、`@Mod` 入口、事件总线/注解、版本对照表与 Maven 坐标、内置库 | ✅ 已归档 |
| [loader-platform-api.md](loader-platform-api.md) | **C**：Forge 1.20.1 ↔ NeoForge 1.21.1 的方块/物品/方块实体注册、`SyncedBlockEntity` 客户端同步、`HolderLookup.Provider` 变化、网络通道（SimpleChannel vs PayloadRegistrar）（据 FarmersDelight 两分支）| ✅ 已归档（C1/C2/C3）|
| [kubejs-integration.md](kubejs-integration.md) | **D**：KubeJS 插件模式（`kubejs.plugins.txt` + `KubeJSPlugin` + `EventGroup` + `EventJS`）与生命周期桥接（据 SlashBlade-SenDims, KubeJS 6/Forge）| ✅ 已归档（含 1.21 待验）|
| [apricity-ui.md](apricity-ui.md) | **E**：ApricityUI（晴雪UI）世界内渲染——依赖坐标（loader+MC 限定，Forge/NeoForge 均有制品）、`WorldWindow`/`FollowFacingWorldWindow`、静态资源层（`apricity/blackboard/`）、标签/CSS/JS 子集、`BlackboardRenderer` 实现方案与 **LaTeX 缺口**（据 doc.sighs.cc + 官方 Nexus 版本索引）| ✅ 已归档（3 处待核实）|

> 这六份中仍需落地时确认的点：
> - **具体版本号**：KLF / NeoForge 21.1.x / KubeJS（multiloader-build.md）、AUI（`deps.aui`：Forge≈1.1.6.4 / NeoForge-1.21.1≈1.1.2）；
> - C4 聊天事件 / C5 发奖 / C6 `Component` 编解码——按作者指示**导入后查反编译源码**确认（loader-platform-api.md §6）；
> - KubeJS 7 的 `EventGroup`/`EventJS` 精确签名（kubejs-integration.md §6，仅 1.21 侧需时）；
> - **AUI**：`WorldWindow` 参数语义、Java 侧内容注入 API、LaTeX 渲染路径（apricity-ui.md §7）。
> 已解决：KLF 依赖配置名 = Loom `modImplementation`；KubeJS 7 发现机制 = `kubejs.plugins.txt`（同 KJS6）。

---

## 三、已关闭 / 不需要

- ~~**E. ApricityUI 渲染**~~ ✅ **已调查并归档** → [apricity-ui.md](apricity-ui.md)。渲染接口（`BlackboardRenderer` + `RenderContext.content: Component`）保持不变（design §5.2/§8）；AUI 侧的依赖坐标、`WorldWindow` 世界内渲染、资源层与 **LaTeX 缺口**已按官方文档归档，3 处待核实见该文 §7。
- **F. 数学求值/校验库**：作者明确「完全由 `QuestionGenerator.validate` 内的逻辑处理」。因此模组核心**不内置**任何数值/CAS 库；answer-format-and-validation.md 中的 `Validators` 仅为可选辅助，生成器作者可自行在 `validate` lambda 里实现任意判题逻辑。

---

## 四、仍需验证（非阻塞，落地时补）

- **C4/C5/C6**（`ServerChatEvent` / 战利品表发物 / `Component`↔JSON・NBT）：按作者指示，**项目导入后通过反编译源码查询**确认两版精确签名（loader-platform-api.md §6 已标注候选 API）。
- **具体版本号**：NeoForge `21.1.x` / `deps.kubejs`——用构建时最新（`deps.klf` 已定 **2.12.1**，两变体 POM 已发布）。
- **KubeJS 7 的 `EventGroup`/`EventJS` 精确签名**：仅在实现 1.21 侧 KubeJS 兼容时查 KJS7 源码（发现机制/基类/钩子已确认）。

> 已解决（本轮）：KLF 构建改用 **Architectury Loom**（`modImplementation`，与 KLF testmod 一致）；KubeJS 7 与 KJS6 共用 `kubejs.plugins.txt` 发现机制。

---

## 修订记录
- 2026-06-30：建立索引；归档 internal-core-api、answer-format-and-validation 两份内部参考；列出 A–F 待补充外部参考。
- 2026-06-30：收到作者提供的 P0 资料（Stonecutter、官方/rotgruengelb 模板、KotlinLangForge），归档 stonecutter.md、multiloader-build.md、kotlinlangforge.md；关闭待补清单中的 A、B。依然待补：C（加载器内 API）、D（KubeJS）、E（ApricityUI）、F（数学库选型）。
- 2026-06-30：收到 C（FarmersDelight 1.20/1.21 分支）、D（SlashBlade-SenDims kubejs）资料，归档 loader-platform-api.md（C1/C2/C3 + 注册/同步/网络差异）、kubejs-integration.md（KubeJS 6 插件模式）；作者明确 E 仅传 `Component`、F 完全由 `validate` 处理，二者关闭。剩余仅「仍需验证」项（C4/C5/C6、KLF 配置名/版本、KubeJS 7）。
- 2026-06-30：作者指示 C4/C5/C6 「导入后查反编译源码」；其余两项自行搜索后：**重大更正** multiloader-build.md 由 ModDevGradle → **Architectury Loom（flat）**（依据 KLF testmod：loom 1.11-SNAPSHOT、`loom.platform` 节点属性、forge 47.4.4/1.20.1、`modImplementation`）；KubeJS 7 经官方 README 确认仍用 `kubejs.plugins.txt` + `KubeJSPlugin`（包名变为 `….plugin.KubeJSPlugin`）+ `EventGroup`，Gradle 经 `maven.latvian.dev`。
- 2026-07-01：新增内部参考 [calculus-module.md](calculus-module.md)：微积分题库模块开发文档（三类拆分 `CalculusExpr`/`CalculusProblems`/`CalculusGenerators`、表达式 AST 与 LaTeX 生成/解析、各题型出题逻辑与逆向积分法、数值抽样判题含 +C 两点差、难度分级与两阶段实装）。`BlackboardTags.CALCULUS` 已入代码；生成器与黑板类型待实装。
