#!/usr/bin/env node
/**
 * 从 server/config.json（已解析BV号）生成 docs/365-english-kids-videos.md
 * 用法: node scripts/gen-md.js
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const SRC = path.join(ROOT, 'server', 'config.json');
const DST = path.join(ROOT, 'docs', '365-english-kids-videos.md');

const config = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const cats = config.categories || [];
const totalItems = cats.reduce((n, c) => n + c.items.length, 0);
const resolved = cats.reduce((n, c) => n + c.items.filter((i) => i.bvid).length, 0);
const totalMin = cats.reduce((n, c) => n + c.items.reduce((m, i) => m + (i.min || 0), 0), 0);

const esc = (s) => String(s || '').replace(/\|/g, '\\|');
const link = (bvid) =>
  bvid ? `[${bvid}](https://www.bilibili.com/video/${bvid})` : '—（用搜索词）';

let md = `# 英语动画·纪录片分级片单（小学2~3年级）

> 目标用户：小学二、三年级（7~9岁）学生，以英语原声内容为主。
> 结构：L1~L5 五级分类 + 纪录片专类，含 2026-10 精选增补（43部优质双语/高清系列），共 **${totalItems}部**。
> 内容分类：英语启蒙生活动画、STEM科普动画、英语纪录片；2026-10 增补双语精选（Big Muzzy/牛津树真人剧/Do You Know/真双语布鲁伊/TED-Ed 等43系列）。
> 单部时长 5~50 分钟，总时长约 ${Math.round(totalMin / 60)} 小时。

## 使用说明

- 每天看 1 部，全年刚好看完；也可按孩子兴趣自由挑选。
- 表中 **B站链接** 由脚本 \`${path.basename(__filename)}\` 于 ${config.resolved ? config.resolved.at.slice(0, 10) : '-'} 自动解析（解析成功 ${resolved}/${totalItems}）。
- B站视频可能因版权/删除而失效：失效时用同一行 **搜索词** 在B站搜索即可找到同类资源，或在 \`server/config.default.json\` 中更新后重新解析。
- 纪录片第43周《猎捕》涉及捕食画面，建议家长陪同观看（片单已标注）。

## 周目录

| 周次 | 主题 | 英文名 | 分类主题 |
|---|---|---|---|
`;

for (const c of cats) {
  md += `| ${c.id.toUpperCase()} | ${esc(c.title)} | ${esc(c.en)} | ${esc(c.theme)} |\n`;
}

md += `\n---\n\n## 每日片单\n\n`;

for (const c of cats) {
  md += `### ${c.title}（${c.en}）\n\n`;
  md += `> ${c.theme}\n\n`;
  md += `| # | 片名 | 标签 | 时长 | B站搜索词 | B站链接 |\n|---|---|---|---|---|---|\n`;
  for (const it of c.items) {
    md += `| ${it.id.toUpperCase()} | ${esc(it.t)} | ${esc(it.tag)} | ${it.min}分钟 | \`${esc(it.kw)}\` | ${link(it.bvid)} |\n`;
  }
  md += `\n`;
}

md += `---\n\n## 分周内容统计\n\n`;
md += `- 英语启蒙生活动画（W01–W22）：154 部\n- STEM科普动画（W23–W35）：91 部\n- 英语纪录片（W36–W52）：119 部\n- 新年特辑（W53）：1 部\n- 合计：${totalItems} 部\n`;
md += `\n*由 \`server/config.json\` 自动生成，修改配置后运行 \`node scripts/gen-md.js\` 重新生成。*\n`;

fs.mkdirSync(path.dirname(DST), { recursive: true });
fs.writeFileSync(DST, md, 'utf8');
console.log(`written: ${DST} (${totalItems} items, ${resolved} resolved)`);
