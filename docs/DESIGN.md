# EvoKids 英语小小影院 — 应用设计分析

> 目标：一台65寸安卓电视 + 一份服务器可远程更新的365部英语动画/纪录片白名单片单 + 家长扫码登录B站，让孩子**只能**看到家长挑选的内容。

---

## 1. 需求分析

### 1.1 原始需求拆解

| # | 需求 | 设计要点 |
|---|---|---|
| R1 | 365部适合小学2~3年级的英语动画片/纪录片片单 | 52周主题×7部+1部新年特辑；见 `docs/365-english-kids-videos.md` 与 `server/config.json` |
| R2 | 安卓TV应用，能直接播放这些动画片 | 应用内播放器（media3/ExoPlayer），不跳转第三方App |
| R3 | 拉取服务器配置的播放列表 | 远程config.json + 本地缓存 + 失败回退 |
| R4 | 支持读取应用内配置的地址 | 设置页可输入自定义配置URL（优先级最高），另有内置默认URL与离线内置配置 |
| R5 | 扫码登录B站 | TV端展示二维码，手机B站App扫码确认，TV端轮询拿Cookie |
| R6 | 不让儿童随便看别的视频 | 白名单模式：应用内**只呈现**config中的内容；家长PIN锁保护设置与退出 |
| R7 | 自适应65寸电视 | Leanback TV UI + 大卡片/大字号 + 焦点缩放动画，按dp与sw限定符自适应 |

### 1.2 隐含需求（家长视角）

- **可靠**：网络差、配置服务器挂了、B站接口变动时仍能离线/降级工作。
- **可控**：家长能远程换片单（改服务器上的JSON即可），孩子端无感知更新。
- **防绕过**：孩子拿到遥控器无法进入系统桌面、无法打开设置、无法搜索任意内容。
- **护眼/时间**（本期可选开关）：连播开关、看完当日推荐即止。

---

## 2. 关键设计决策

### 2.1 播放方案对比（决策：B站 playurl + ExoPlayer，失败降级跳转B站TV版）

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| A. 拉流直放：`playurl(platform=html5)` 取MP4 → ExoPlayer | 体验最好：全屏、无广告位、可控UI、白名单闭环 | 依赖B站web接口；清晰度上限720p(html5) | ✅ 主方案 |
| B. WebView 嵌入 player.bilibili.com | 接口稳 | TV遥控器D-pad操作网页困难、有页面导航逃逸风险（可点击"去看推荐"） | ❌ |
| C. 深链跳转B站TV版App（`bilibili://video/BV...`） | 最省事 | 跳出去后孩子可自由浏览B站——违背R6 | ❌（跳转入口已移除） |

播放链路（App内自动执行，失败自动降级）：

```
片单项(item)
  ├─ item.bvid（配置预解析或本地缓存）
  ├─ 无bvid → 用 item.kw 调B站搜索接口取第一条 → 缓存bvid
  ↓
pagelist?bvid=xxx → 取第 item.ep 页的 cid
  ↓
playurl?bvid=..&cid=..&platform=html5&qn=64 → durl[0].url（MP4直链）
  ↓
ExoPlayer + 请求头(Referer: bilibili.com / Cookie: SESSDATA…)
  ↓ 播放失败(403/下架)
提示家长 + 重试（应用内闭环，不跳B站）
```

### 2.2 登录方案（决策：B站官方网页版扫码登录）

TV端没有键盘，账号密码输入不可行。采用B站**官方**二维码登录协议（与网页登录同一套）：

1. `GET passport.bilibili.com/x/passport-login/web/qrcode/generate` → `{url, qrcode_key}`
2. TV大屏展示二维码（zxing本地生成，无第三方依赖）
3. 手机B站App扫码确认
4. TV端每2秒轮询 `.../web/qrcode/poll?qrcode_key=...`，`code=0` 时响应头携带 `SESSDATA/bilijct/DedeUserID` 等Cookie
5. Cookie存本地（仅家长PIN可见的私有存储），后续所有API与拉流请求自动携带
   - 登录态可提升 `playurl` 清晰度（480p→720p）并避免风控

### 2.3 配置来源三级优先级（覆盖R3+R4）

```
1. 应用内自定义地址（设置页输入，存SharedPreferences）     ← 用户中途新增需求
2. 内置默认远程地址（BuildConfig.DEFAULT_CONFIG_URL，可随APK发版更新）
3. assets/config.default.json（离线兜底，与服务器版同源）
```

任一优先级拉取成功即缓存到本地；启动时后台静默刷新（版本号 `version` 变大才覆盖）。**孩子永远只看到"最后一份成功加载的配置"**，不存在无片单状态。

### 2.4 白名单与家长锁（R6）

- 应用内没有搜索框、没有推荐位、没有相关视频——内容**唯一来源是config**。
- 设置入口藏在右侧（孩子不易到达），进入前强制 **4位家长PIN**（首次启动向导设置，SHA-256加盐存储；忘记PIN=卸载重装）。
- 退出应用同样需要PIN，防止孩子退回系统桌面打开其他视频App。
- 可选：`android:launchMode="singleTask"` + 全屏沉浸，屏蔽状态栏。

### 2.5 65寸自适应（R7）

- Leanback `BrowseSupportFragment`：左侧周次栏 + 右侧大卡片行，天然TV范式。
- 卡片基准 220×124dp（65寸约4K，dpi≈420；按 `sw600dp/land` 限定符放大到 260×146dp），焦点缩放1.05+描边。
- 字号：标题28sp/正文20sp，3米观看距离可读。
- 全部布局用 `match_parent` + 权重，不写死像素；横屏锁定 `android:screenOrientation="landscape"`。

---

## 3. 系统架构

```
┌───────────────────────────── Android TV App (Kotlin) ─────────────────────────────┐
│                                                                                    │
│  ui/                                    core/                                      │
│  ├─ MainActivity (BrowseSupportFragment)  ├─ ConfigRepo   三级配置源+缓存+版本比较   │
│  ├─ DetailActivity (详情+播放入口)         ├─ BiliApi     QR登录/nav/search/pagelist │
│  ├─ PlayerActivity (media3 ExoPlayer)     │              /playurl(html5 mp4)       │
│  ├─ SettingsActivity (PIN门禁)            ├─ CookieStore SESSDATA等持久化          │
│  │   ├─ QrLoginFragment (扫码登录)         ├─ PinLock     PIN哈希/校验              │
│  │   └─ ConfigUrlFragment(配置地址管理)     └─ ResolveCache kw→bvid 本地缓存         │
│  └─ SetupActivity (首次启动向导)          player/                                      │
│                                          └─ BiliMediaSource 带Referer/Cookie的MP4源 │
└────────────────────────────────────────────────────────────────────────────────────┘
         ▲ config.json                          ▲ 搜索/拉流API
         │                                       │
   [远程服务器 GitHub Pages/静态HTTP]        [bilibili.com]
```

## 4. 远程配置格式（server/config.json）

```jsonc
{
  "version": 1,                      // 递增；App仅接受更大版本
  "updated_at": "2026-10-29",
  "server": { "name": "EvoKids 英语小小影院" },
  "play": { "default_qn": 64, "autoplay_next": true },
  "categories": [
    {
      "id": "w01", "title": "第01周 · 小猪佩奇", "en": "Peppa Pig",
      "theme": "生活英语 · 家庭日常",
      "items": [
        {
          "id": "d001",             // 全局唯一，进度/缓存key
          "t": "Muddy Pudders 踩泥坑",
          "kw": "小猪佩奇 英文版 第1季 Muddy Puddles",  // 兜底搜索词
          "bvid": "BV1xx411c7mD",   // 可选，预解析；失效自动回退kw搜索
          "ep": 1,                  // 选集序号(1起)，null=第1P
          "min": 5,                 // 参考时长(分钟)
          "tag": "英语启蒙"
        }
      ]
    }
  ]
}
```

家长改片单 = 改这份JSON并部署，所有电视下次启动自动更新。

## 5. B站接口清单（全部为公开web接口）

| 用途 | 接口 | 说明 |
|---|---|---|
| 二维码生成 | `GET /x/passport-login/web/qrcode/generate` | 无需登录 |
| 扫码轮询 | `GET /x/passport-login/web/qrcode/poll?qrcode_key=` | 成功时Set-Cookie含SESSDATA |
| 登录态检查 | `GET api.bilibili.com/x/web-interface/nav` | 显示昵称/头像 |
| 视频搜索 | `GET /x/web-interface/search/type?search_type=video&keyword=` | kw→bvid兜底 |
| 分P列表 | `GET api.bilibili.com/x/player/pagelist?bvid=` | 取cid |
| 播放地址 | `GET api.bilibili.com/x/player/playurl?platform=html5&qn=64&bvid=&cid=` | 返回MP4直链(durl) |

播放直链必须带 `Referer: https://www.bilibili.com` 与登录Cookie，否则403。

## 6. 安全与合规说明

- 本应用为**家庭自用**家长管控工具，播放源与登录协议均为B站官方公开接口；请遵守B站用户协议，不得用于商业分发或破解付费内容。
- Cookie存于应用私有目录，不传出设备；配置服务器只下发"看什么"，不收集任何用户数据。
- 部分纪录片含捕食等画面（片单已标注"家长陪同"），家长可自行在config中删减。
