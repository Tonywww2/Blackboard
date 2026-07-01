# 外部参考：ApricityUI（晴雪UI / AUI）世界内 UI 渲染

> 来源：官方文档 <https://doc.sighs.cc/ApricityUI>（`intro` 与 `guide/*`：ui-types、support、resource、web-api、example、agent，均已逐页核实）；官方 Maven（Nexus）<https://maven.sighs.cc> 的版本索引（已核实）。
> GitHub <https://github.com/Tower-of-Sighs/AUI>（中国网络不可达，**未**直接核源码）。
> 作用：为 Blackboard 的 [`BlackboardRenderer`](../../src/main/kotlin/com/tonywww/blackboard/api/render/BlackboardRenderer.kt) 提供「把题面渲染到世界内黑板」的具体实现依据。设计背景见 [blackboard-design.md](../blackboard-design.md) §5.2/§8。

> ⚠️ 本文按官方文档归档；其中 **WorldWindow 参数精确语义、内容注入 API、LaTeX 渲染路径** 三处标注为「待核实」，落地前须对齐官方源码或实测（见 §7）。凡未核实处，实现代码用 `// TODO(ref): AUI …` 标注，不凭空实现（沿用 references/README 约定）。

目录：
1. 是什么 / 定位
2. 依赖坐标与仓库（Forge 1.20.1 / NeoForge 1.21.1）★
3. 三种 UI 类型（Blackboard 用「世界内影像」WorldWindow）★
4. 静态资源分发与热重载
5. 可用标签 / CSS / JS（能力子集与边界）
6. 映射到 Blackboard：`BlackboardRenderer` 实现方案 ★
7. 关键未决问题（落地前核实）
8. 相关链接

---

## 1. 是什么 / 定位

- **晴雪UI（ApricityUI / AUI）** 是一个用 **HTML + CSS + JS** 描述界面的 Minecraft UI 框架；**从零用 Java 构建**（不内嵌 Chrome 内核），本体 < 1MB。
- 语法尽量贴合 Web 标准，但**不是浏览器**：只实现「一套好用、越来越像 Web 的子集」，写超出实现范围的 CSS/JS 通常**静默失效**（不报错）。
- **JS 依赖 KubeJS（可选前置）**：不装 KubeJS 时 HTML/CSS 仍可用，但**页面内 JS 与 F12 调试台不可用**。对纯展示型 UI 无影响。
- **关键能力（对 Blackboard 决定性）**：支持把 UI **渲染成世界里的一块平面**——按世界坐标定位、可旋转/缩放、带**深度测试与遮挡**。这正是「世界内黑板」需要的。
- 其它能力：盒模型/Flex/Grid、圆角、边框九宫格（border-image）、毛玻璃（backdrop-filter）、裁剪遮罩（clip-path）、滤镜、自定义动画/过渡、自定义字体、GIF、自定义输入框、以及 MC 原生元素（物品槽/容器/配方/翻译）。

---

## 2. 依赖坐标与仓库 ★

**仓库**（官方 Nexus）：

```kotlin
repositories { maven("https://maven.sighs.cc/repository/maven-public/") }
```

**制品按「加载器 + MC 版本」区分**（已核实 Nexus 索引 `com/sighs/` 下的实际 artifactId）：

| 目标 | artifactId（group `com.sighs`）| 撰写时最新稳定版 | 说明 |
| --- | --- | --- | --- |
| **Forge 1.20.1** | `ApricityUI-forge-1.20.1` | **1.1.6.4** | 官方**主力**版本，功能最全 |
| **NeoForge 1.21.1** | `ApricityUI-neoforge-1.21.1` | **1.1.2** | 存在，但**落后于 Forge**（另有 1.1.3-dev / 1.1.5-dev 开发版）|
| Fabric 1.20.1 | `apricityui-fabric-1.20.1`（注意小写）| — | 本项目不用 |
| NeoForge 26.1.1 | `ApricityUI-neoforge-26.1.1` | — | 官方规划的未来主力，本项目不用 |

> ⚠️ 官方 `intro` 页示例写的是 `implementation 'com.sighs:ApricityUI:1.0.2'`——那是**简化占位**；真实制品是上表的 loader+MC 限定坐标。**构建时以 `maven-metadata.xml` 的最新稳定版为准**（dev/SNAPSHOT 勿用于发布）。
> ⚠️ **版本支持不对等**：官方明确「除 1.20.1 Forge 外，其它版本都会落后一部分」。故 NeoForge 1.21.1 上世界内渲染**必须实测**，不能假设与 Forge 等价（见 §7）。

**Loom 接入**（本项目是 Architectury Loom + Stonecutter，见 [multiloader-build.md](multiloader-build.md)）：AUI 是 Minecraft mod，按 loader 选坐标（用 `loom.platform` 条件，同 KLF/KubeJS 模式）。因 Blackboard 设计为「**无 AUI 时用 no-op 渲染器、功能不受影响**」，AUI 应作**客户端软依赖**：

```kotlin
// 编译期依赖其 API，运行期由玩家安装（同 KubeJS 的 modCompileOnly 思路）。isTransitive = false：
// AUI 的 POM 声明了可选软集成依赖（JitPack animated-gif-lib、Modrinth sodium/iris/jei/
// lan-server-properties），这些仓库本项目没有；我们只编译 AUI 自身 API，故丢弃传递依赖。
if (loader == ModPlatform.FORGE) {
    modCompileOnly("com.sighs:ApricityUI-forge-1.20.1:${property("deps.aui")}") { isTransitive = false }
} else {
    modCompileOnly("com.sighs:ApricityUI-neoforge-1.21.1:${property("deps.aui")}") { isTransitive = false }
}
```

> `deps.aui` 写进各节点 `versions/<id>/gradle.properties`（Forge 与 NeoForge 版本号不同：1.1.6.4 / 1.1.2）。✅ 上述接入已落地（两端 `build` 通过）。

---

## 3. 三种 UI 类型（Blackboard 用「世界内影像」WorldWindow）★

AUI 有三种玩法：**Overlay**（叠加层/HUD）、**Screen**（打开的界面，可绑定真实容器/背包）、**世界内影像**（把 UI 当成世界里的一块平面）。**Blackboard 用第三种。**

统一入口是主类 `ApricityUI`（Java 与 KJS 同名接口）：`createDocument` / `createInWorldDocument` / `removeDocument` / `openScreen` / `closeScreen` / `bind` / `createWorldWindow` / `createFollowFacingWorldWindow` / `removeWorldWindow` / `clearWorldWindows`。

### 3.1 WorldWindow（Blackboard 主用）

`createInWorldDocument(path)` 只创建「世界内 Document」，**要显示还得挂进 `WorldWindow`**：

```java
// Java — 确认签名（据 AUI 源码 ApricityUI.java / WorldWindow.java）：
// createWorldWindow(String documentPath, Vec3 position, float width, float height, int maxDistance)
WorldWindow window = ApricityUI.createWorldWindow("blackboard/board.html", position, 96f, 56f, 16);
window.setRotation(yRot, xRot);         // 朝向（度）；另有 window.setScale(scale)，默认 0.02f
ApricityUI.removeWorldWindow(window);    // 需要时移除
```

```javascript
// KJS 客户端脚本（坐标拆成 x, y, z）：(path, x, y, z, width, height, maxDistance)
let window = ApricityUI.createWorldWindow("blackboard/board.html", 0, 65, 0, 96, 56, 16)
```

- `position`：平面**中心**的世界坐标（`Vec3`）。
- `width` / `height`：文档画布**像素**尺寸（float）；配合 `scale` 决定世界里的实际大小。默认 `scale = 0.02f`（≈ 50px = 1 格），`window.setScale(...)` 可改。
- `maxDistance`：交互射线的最大命中距离（int），不影响是否绘制。
- **旋转**：`window.setRotation(yRot, xRot)`（度）。渲染时按 `rotationY(180 - yRot)` 应用——本项目按黑板 facing 映射 `yRot`：NORTH=0 / EAST=90 / SOUTH=180 / WEST=270（见 `client/AuiBlackboardRenderer`）。

> ✅ 之前「参数是 `(旋转角,宽,高)`」的推测**已否证**：三个数值是 **`(width, height, maxDistance)`**，旋转/缩放是 `WorldWindow` 的独立方法。设计世界内 UI 时别照搬浏览器大面板尺寸（MC 默认 GUI ≈ 427×240 px，观感偏大）。

### 3.2 FollowFacingWorldWindow（本项目大概率不用）

`WorldWindow` 的扩展：保留基准位置的同时，按 `followFactor`（0~1）跟随玩家视角并始终朝向摄像机，适合头顶说明牌/漂浮信息卡。

```java
FollowFacingWorldWindow w = ApricityUI.createFollowFacingWorldWindow("demo/follow.html", position, 180, 100, 16, 0.3f);
```

> Blackboard 的黑板**固定挂墙、朝向由 blockstate 决定**，用普通 `WorldWindow` 即可，不需要 follow（除非将来做「漂浮题卡」变体）。

### 3.3 Overlay / Screen（Blackboard 暂不用，备查）

- **Overlay**：`ApricityUI.createDocument(path)` / `removeDocument(path)`（或底层 `Document.create/remove`）。适合 HUD、状态提示、调试面板；可多开。
- **Screen**：`ApricityUI.openScreen(path)` / `closeScreen()`；接真实容器/背包时走服务端权威入口 `ApricityUI.bind()...build()` + `openScreen(player, path, plan)`，模板顶层 `<container id>` 要与 `OpenBindPlan` 名字对齐。**Blackboard 作答走聊天栏，不需要容器绑定**，故不用 Screen。

---

## 4. 静态资源分发与热重载

- **支持格式**：HTML、CSS、JS、图片（PNG/JPG/**GIF 真动**）、字体（TTF/OTF）。音视频暂未支持。内置 `lxgw` 字体（精简版，汉字仅 3500 常用字）。
- **三层资源路径（优先级 高 → 低）**：
  1. 开发环境：`src/main/resources/assets/apricityui/apricity/...`（**最高**）
  2. 游戏实例目录：`apricity/...`
  3. 模组资源包：`assets/apricityui/apricity/...`（兜底；AUI 默认全局样式 `global.css` 与内置字体在此）
- **Blackboard 资源归属**：黑板的 HTML/CSS/字体放
  `src/main/resources/assets/apricityui/apricity/blackboard/`（对应 design 的 `apricity/blackboard/`；随模组资源包分发）。用**相对路径**引用（`<img src="chalk.png">`、`@font-face { src: url("fonts/x.ttf") }`）；以 `/` 开头则从 Apricity 资源根算（非站点根）。
- **CSS/JS 加载**：内联或外链——`<style src="board.css"></style>` / `<script src="board.js"></script>`。外链 CSS 异步、支持 `@import`；外链 JS 走本地资源（非浏览器脚本分发）。
- **远程资源**：仅 `https://`、异步、有大小/类型限制，支持远程**图片/CSS/字体**（不适合远程 HTML/JS）。
- **热重载**：按 `END` 立即重载并刷新 Document；调试模式自动监听 `.html/.css/.js` 变更，日志打 `[DebugReload] change detected:` 与 `[DebugReload] reload completed`。
- **自动化/AI 调试**：调试模式下 `run/screenshots/aui` 每秒输出一张游戏截图（最多存 20 张）——配合官方 `agent` 引导可让 AI 迭代 UI（见 §8）。AUI 自带 **Resource Manager** 可查看合并后最终生效的资源来自哪一层。

---

## 5. 可用标签 / CSS / JS（能力子集与边界）

### 5.1 标签
- 通用：`body` `div` `span` `pre` `img` `a` `input` `textarea` `select` `option` `sprite`
- MC 专属：`container` `slot` `recipe` `translation`（Blackboard 展示题面基本用不到）
- 依赖 `global.css` 兜底样式：`p` `h1`~`h6` `small` `b` `strong` `i` `em` `mark` `sub` `sup` `code` `kbd` `hr` `blockquote`
- 默认只写 `<body>...</body>`，**不写** `<html>`/`<head>`；不要依赖浏览器 UA 默认样式。

### 5.2 CSS
- 选择器：标签、`.class`、`#id`、`[attr]`、`[attr=value]`、`:first-child/:last-child/:nth-child()/:hover/:active/:focus/:empty/:checked`、后代空格、`>`、`,`。
- 属性：布局 `display/flex-*/grid-template-*/grid-row/grid-column`；尺寸 `width/height/min|max-*`；盒模型 `margin*/padding*/border*/border-radius/border-image`；定位 `position/top/right/bottom/left/z-index`；背景 `background-*`；文本 `color/font-size/font-family/font-weight/font-style/line-height/text-stroke`；视觉 `opacity/box-shadow/transform/clip-path/filter/backdrop-filter`；交互 `cursor/pointer-events/visibility/user-select`；动效 `transition/animation*/@keyframes/@font-face`；变量 `--*`。
- **未实现的属性会静默失效**——调样式时先怀疑「这个属性到底支不支持」。

### 5.3 JS（需 KubeJS）
- `guide/web-api` 页给出较全的**浏览器兼容层**：`document`/`window`/`Element`/`Event`/`CustomEvent`/`MouseEvent`/`WheelEvent`/`PointerEvent`、`localStorage`/`sessionStorage`、`console`、`performance.now()`、`fetch(url)`（**仅单参 GET**）、`getComputedStyle`、`requestAnimationFrame`、`ResizeObserver`、`MutationObserver`、`URLSearchParams`、`FormData`、`location`（占位、不真导航）。集合返回 **JS 数组**（非原生 NodeList/HTMLCollection）。
- **不提供/仅部分**：真实页面导航、`fetch(url, init)`、完整 `Node/Text/DocumentFragment` 模型、`window.history`、`matchMedia`、`postMessage`、`WebSocket`、`XMLHttpRequest`、微任务级调度。
- ⚠️ **两页口径不同**：`guide/support` 与 `agent` 给的是**保守子集**（querySelector/setAttribute/classList/dataset/addEventListener 等）；`guide/web-api` 给的是**当前较全实现**。以 `web-api` 为「能力上限」参考、以 `agent` 为「稳妥写法」参考，**不确定就用保守方案**。

### 5.4 ⚠️ 无数学 / LaTeX 渲染（对 Blackboard 关键）
AUI **不含 MathML、也无内置 LaTeX 排版**。design 里「把含 LaTeX 的 `Component` 交给 AUI 的 HTML 渲染」**不能直接成立**，须在 §6 选定落地路径。

---

## 6. 映射到 Blackboard：`BlackboardRenderer` 实现方案 ★

**现状**（已在代码就位，见 `api/render/`）：`fun interface BlackboardRenderer { render(RenderContext) }`、`RenderContext{ level,pos,boardId,blockState,content: Component }`、`object BlackboardRendering{ var renderer }` 默认 no-op。AUI 集成 = 写一个 renderer 在**客户端初始化**时注入 `BlackboardRendering.renderer`，核心逻辑零改动。

> ✅ **已落地最小版（P8-A 首版）**：`client/AuiBlackboardRenderer`（把 `content` 用 `Component.getString()` 拍平成字符串、与 `boardId` 一起显示在黑板前的 `WorldWindow` 面板上；**暂无 LaTeX**，公式按原文显示）+ `client/BlackboardClient`（客户端 setup 时若装了 AUI 则注入，否则保持 no-op）+ 模板 `assets/apricityui/apricity/blackboard/board.html`。两端 `build` 已过（编译对齐真实 AUI API）。dev runClient 默认不含 AUI（`modCompileOnly`），故需加 `modLocalRuntime` 才能在游戏内看到（见 §7）。

**渲染骨架（拟）**：
1. 仅客户端、且检测到 AUI 已安装时注入实现（`ModList.get().isLoaded("apricityui")`；未装则保持 no-op）。
2. `render(context)`：为该黑板 `pos` 创建/更新一个 `WorldWindow`（HTML 模板 `apricity/blackboard/board.html`），把 `context.content` 的文本注入页面。
3. 维护一份**客户端 `pos → WorldWindow` 索引**：题目变化→更新对应窗口；方块移除/区块卸载/renderer 卸载→`removeWorldWindow`（或 `clearWorldWindows`）。
4. 世界内窗口的**位置/朝向**要与黑板 blockstate 的 facing 对齐（贴墙、朝外）。

**内容注入方式（✅ 已确认 Java DOM API，源码核实）**：`WorldWindow.document`（公有字段）→ `Document.getElementById(id)` / `querySelector(sel)` 拿 `Element`，再设置文本。文本 setter **两版不同**（NeoForge 1.1.2 API 较旧）：
- Forge 1.1.6.4：`element.setTextContent(value)`（清子节点 + 设 `innerText` + 清渲染缓存，最完整）。
- NeoForge 1.1.2：无 `setTextContent`；用公有字段 `element.innerText = value` + `element.invalidateStyle()` 触发重算。
- 两版都有：`Document.getElementById/querySelector`、`Element.innerText`（字段）、`Element.invalidateStyle()`、`WorldWindow.setRotation/setScale/setPosition`、`ApricityUI.createWorldWindow/removeWorldWindow`。
- `createInWorldDocument`/`createWorldWindow` 内部**同步** `document.refresh()`（从静态 HTML 文件建树），故创建后可立即 `getElementById`。用 Stonecutter 隔离 setter（见 `client/AuiBlackboardRenderer.setText`）。

**LaTeX 渲染缺口（🟥 需作者拍板，见 §5.4）**：
- (1) **预渲染成图片**：Blackboard 端把 LaTeX → **PNG**（客户端或服务端预渲染），黑板用 `<img>` 显示（AUI 支持 PNG/GIF；**SVG 未列入支持、大概率不可用**）。复杂公式（矩阵/积分/极限）走此路最稳。
- (2) **纯 HTML/CSS 近似**：简单式子用 flex + border 拼（分数、上下标可用 `sub`/`sup`），**能力有限**，复杂公式难。
- 建议：**简单题面用 HTML/CSS，复杂公式用预渲染 PNG**；据此细化 design §8，并明确 LaTeX→PNG 的渲染器归属（新 `platform`/`client` 组件）。

**KubeJS 关系**：黑板若**纯静态展示**（无交互 JS），AUI 世界内渲染**可不依赖 KubeJS**；一旦走 (c) 或页面交互，则需 KubeJS（Blackboard 已有 KubeJS 软依赖，见 [kubejs-integration.md](kubejs-integration.md)）。

**Loader 差异**：渲染是**纯客户端**；`RenderContext` 引用的 MC 类型（`Level`/`BlockPos`/`BlockState`/`Component`）两版同包，**无需 Stonecutter 拆分**（代码注释已确认）。但 **AUI 依赖坐标与可用性分 loader**（§2）：Forge 1.20.1 成熟、NeoForge 1.21.1 版本较旧须实测。

---

## 7. 关键未决问题（落地前核实）

1. ✅ **已解决** `createWorldWindow` 参数：`(String path, Vec3 position, float width, float height, int maxDistance)`；旋转/缩放走 `WorldWindow.setRotation/setScale`（默认 scale 0.02f）。（据源码）
2. ✅ **已解决** Java 侧内容注入：`WorldWindow.document.getElementById(id)` + Forge `setTextContent` / NeoForge `innerText`+`invalidateStyle`（见 §6）。
3. 🟥 **LaTeX 渲染路径**（预渲染 PNG vs HTML/CSS 近似）——需作者决策，再细化 design §8。首版按原文字符串显示，未做 LaTeX。
4. **世界内窗口朝向/对齐**：首版按 blockstate facing 映射 `yRot` 并把面板中心外移 0.51 格；板体尺寸（3 宽 2 高）与窗口宽高/缩放的精确匹配仍需实测微调。
5. **游戏内实测**：dev 默认 `modCompileOnly` 不含 AUI 运行时→renderer 不激活。要实测需把 AUI 加进运行期（`modLocalRuntime`）并补齐其运行时所需仓库/依赖；且 NeoForge 1.21.1 版本较旧（1.1.2）、AUI 是否在 Loom dev 干净加载**尚未实测**（勿贸然加，避免影响已修好的 runClient）。
6. **窗口生命周期**：首版保留 `pos→WorldWindow` 索引、题目变化就更新；**方块破坏/区块卸载时的 `removeWorldWindow` 尚未接线**（客户端无对应钩子）——留作后续。
7. `deps.aui` 具体版本：构建时取各节点 `maven-metadata.xml` 最新稳定版（Forge≈1.1.6.4 / NeoForge-1.21.1≈1.1.2）。

---

## 8. 相关链接

- 官方文档：<https://doc.sighs.cc/ApricityUI>（`intro` / `guide/ui-types` / `guide/support` / `guide/resource` / `guide/web-api` / `guide/example`）
- AI 生成引导（喂给 AI 生成 AUI 页面用，含标签/CSS/JS 约束）：<https://doc.sighs.cc/ApricityUI/agent>
- 官方 Maven（Nexus）：<https://maven.sighs.cc/repository/maven-public/>（浏览 `#browse/browse:maven-public`）
- CurseForge：<https://curseforge.com/minecraft/mc-mods/apricityui> ｜ Modrinth：<https://modrinth.com/mod/apricityui> ｜ GitHub：<https://github.com/Tower-of-Sighs/AUI>

---

## 修订记录
- 2026-07-01：据官方文档（`intro` 与 `guide/ui-types|support|resource|web-api|example|agent`）与官方 Nexus 版本索引首次归档。确认「世界内影像」`WorldWindow` 为 Blackboard 主用路径、依赖坐标按 loader+MC 限定（Forge `ApricityUI-forge-1.20.1`≈1.1.6.4 / NeoForge `ApricityUI-neoforge-1.21.1`≈1.1.2）、资源层 `apricity/blackboard/`、JS 需 KubeJS。标注三处待核实：WorldWindow 参数语义、Java 侧内容注入 API、LaTeX 渲染路径（AUI 无内置 LaTeX，倾向预渲染 PNG）。
- 2026-07-01（补·源码核实 + 首版落地）：下载官方 sources/binary jar 核实 API——`createWorldWindow(String, Vec3, float width, float height, int maxDistance)`（此前「三参=旋转/宽/高」推测已否证）、`WorldWindow.document`/`setRotation`/`setScale`、`Document.getElementById`、文本 setter 两版分歧（Forge `setTextContent` / NeoForge 1.1.2 `innerText`+`invalidateStyle`）。据此落地最小 `client/AuiBlackboardRenderer`+`BlackboardClient`+`board.html`，`build.gradle.kts` 以 `modCompileOnly {isTransitive=false}`（丢弃 AUI 的 gif/sodium/iris/jei 传递依赖）接入，两端 `build` 通过。§7-1/§7-2 已解决。
