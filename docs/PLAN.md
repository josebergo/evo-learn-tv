# EvoKids 英语小小影院 — 执行方案

按阶段执行，每阶段含验收标准。对应设计文档：`docs/DESIGN.md`。

## 阶段一：内容生产（片单）✅

| 任务 | 产出 | 验收 |
|---|---|---|
| 1.1 编写52周×7+1部片单母本 | `server/config.default.json` | 365条，字段齐全(id/t/kw/ep/min/tag) |
| 1.2 脚本真实调用B站搜索接口解析BV号 | `scripts/resolve-bvids.js` → `server/config.json` | 解析率>80%，失败条目保留搜索词兜底 |
| 1.3 生成人读版MD片单 | `scripts/gen-md.js` → `docs/365-english-kids-videos.md` | 表格含片名/搜索词/B站链接 |

## 阶段二：安卓TV应用（Kotlin）

| 任务 | 产出 | 验收 |
|---|---|---|
| 2.1 工程骨架 | Gradle(KTS)+Leanback+media3+OkHttp+zxing+Gson，minSdk 21 / target 35 | `assembleDebug` 出APK |
| 2.2 配置加载 ConfigRepo | 三级来源：应用内自定义地址 > 内置默认URL > assets离线 | 断网启动可用；version提升才覆盖 |
| 2.3 主界面 MainFragment | BrowseSupportFragment，左=周次，右=7张大卡片，焦点缩放 | 65寸4K布局正常，D-pad流畅 |
| 2.4 详情+播放 | DetailActivity → PlayerActivity(media3)，自动选ep、自动连播开关 | 点卡片→详情→播放，全程不触系统UI |
| 2.5 B站数据链路 BiliApi | search→bvid缓存、pagelist→cid、playurl→MP4(带Referer/Cookie) | 拉流成功进入ExoPlayer；失败提示+PIN后跳B站TV版降级 |
| 2.6 扫码登录 QrLoginFragment | generate→大屏QR→poll→存Cookie→nav验证昵称 | 手机扫码后TV显示"已登录：xxx" |
| 2.7 家长锁 SetupActivity+SettingsActivity | 首启设PIN；设置/退出需PIN(SHA-256加盐) | 输错PIN无法进入设置或退出 |
| 2.8 配置地址管理 ConfigUrlFragment | 设置页输入/清除自定义URL、立即刷新、显示当前来源与版本 | 修改URL后立即生效并缓存 |
| 2.9 打包发布 | GitHub Actions 自动构建APK + README安装说明 | CI产物可下载 |

## 阶段三：服务器侧

| 任务 | 产出 | 验收 |
|---|---|---|
| 3.1 示例配置托管说明 | `server/README.md`（GitHub Pages/局域网nginx两种） | 按文档可访问 config.json |
| 3.2 默认远程地址约定 | `BuildConfig.DEFAULT_CONFIG_URL` 指向占位地址+注释 | 家长改一处常量/或用应用内自定义地址即可 |

## 阶段四：验证

| 任务 | 验收 |
|---|---|
| 4.1 本地编译 | `gradle assembleDebug` 成功（JDK17+SDK35） |
| 4.2 JSON校验 | node脚本校验365条、id唯一、必填字段 |
| 4.3 文档齐套 | DESIGN/PLAN/365片单/README 四份+代码注释自解释 |

## 技术选型定版

- Kotlin 2.0.x，AGP 8.7.x，Gradle 8.11，compileSdk 35，minSdk 21，targetSdk 35
- UI：androidx.leanback 1.0.0（TV标准组件）；播放：androidx.media3 1.3.1（ExoPlayer+PlayerView）
- 网络：OkHttp 4.12；JSON：Gson 2.10；二维码：zxing-core 3.5（本地生成位图）
- 依赖仓库：阿里云镜像优先（国内网络），google()/mavenCentral()兜底
