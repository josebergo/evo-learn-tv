# 片单配置服务器

应用通过 HTTP(S) 拉取一份 JSON 配置决定"孩子能看什么"。修改片单 = 改 JSON + 部署，电视端下次启动自动更新。

## 文件说明

| 文件 | 说明 |
|---|---|
| `config.default.json` | 母本：365部片单（含搜索词，不含BV号） |
| `config.json` | 由 `scripts/resolve-bvids.js` 生成：365部 + B站BV号（可直连播放） |

**部署到服务器的是 `config.json`（带BV号，加载快、更稳定）。**

## 部署方式一：GitHub Pages（推荐，免费）

```bash
git init && git add server/config.json && git commit -m "playlist"
git branch -M main
git remote add origin https://github.com/<你的用户名>/evo-learn-tv.git
git push -u origin main
# 仓库 Settings → Pages → Source 选 main 分支 /root
```

得到的地址（即应用里要填的"配置地址"）：

```
https://raw.githubusercontent.com/<你的用户名>/evo-learn-tv/main/server/config.json
```

## 部署方式二：家里局域网（NAS/树莓派/nginx）

```bash
# 任意静态服务器即可，例如：
cd server && python3 -m http.server 8090
# 配置地址填： http://<内网IP>:8090/config.json
```

> 应用已在 AndroidManifest 中允许 http 明文流量（`usesCleartextTraffic=true`），内网 http 地址可直接用。

## 电视端生效方式（二选一）

1. **改APK内置地址**：`app/build.gradle.kts` 里 `DEFAULT_CONFIG_URL` 改成你的地址后重新打包；
2. **不改APK**：电视上 `家长专区 → ⚙ 家长设置 → 修改配置地址`，输入上面的JSON地址 → 立即刷新。（应用内配置地址优先级最高）

## 更新片单

1. 编辑 `config.default.json`（加/删条目，把 `version` +1）；
2. 重新解析BV号并重新生成MD文档：
   ```bash
   node scripts/resolve-bvids.js && node scripts/gen-md.js
   ```
3. 提交推送 `server/config.json`。电视端下次启动（或手动刷新）自动生效。
