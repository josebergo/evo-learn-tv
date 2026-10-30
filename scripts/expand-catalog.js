#!/usr/bin/env node
/**
 * 片单扩充到1000部 + 难度分级(v2)：
 * 1) 现有365部按系列映射 L1-L5 等级
 * 2) 策划新系列(儿歌/动画/科普/纪录片)调B站搜索取BV，再拉分P展开成集
 * 3) 重组 categories 为等级分类(L1启蒙~L5挑战 + 纪录片)，条目带 lv 字段
 * 输出: server/config.json (version 2) 并同步 assets/config.default.json
 * 用法: node scripts/expand-catalog.js
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const SRC = path.join(ROOT, 'server', 'config.json'); // v1 已解析BV的365部
const DST = path.join(ROOT, 'server', 'config.json');
const ASSETS = path.join(ROOT, 'app', 'src', 'main', 'assets', 'config.default.json');
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
let cookie = '';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function bootstrapCookie() {
  try {
    const res = await fetch('https://www.bilibili.com/', { headers: { 'User-Agent': UA } });
    const set = res.headers.getSetCookie ? res.headers.getSetCookie() : [];
    cookie = set.map((c) => c.split(';')[0]).join('; ');
  } catch (e) { console.error('bootstrap cookie failed:', e.message); }
  if (!cookie.includes('buvid3')) cookie += (cookie ? '; ' : '') + 'buvid3=infoc';
}

async function getJson(url, tries = 4) {
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(url, { headers: { 'User-Agent': UA, Cookie: cookie, Referer: 'https://www.bilibili.com/' } });
      const json = await res.json();
      if (json.code === 412 || json.code === -412) { await sleep((6 + i * 4) * 1000); continue; }
      return json;
    } catch (e) { await sleep(2500); }
  }
  return null;
}

function parseDuration(d) {
  if (!d) return 0;
  return String(d).split(':').map(Number).reduce((acc, p) => acc * 60 + p, 0);
}

async function searchVideo(kw) {
  const json = await getJson('https://api.bilibili.com/x/web-interface/search/type?search_type=video&page=1&keyword=' + encodeURIComponent(kw));
  const result = json && json.code === 0 && json.data && json.data.result;
  if (!result || !result.length) return null;
  // 合集优先：时长长、标题带"合集/全集/Season"，且与关键词相关
  const cands = result.filter((v) => parseDuration(v.duration) >= 300);
  const pool = cands.length ? cands : result.filter((v) => parseDuration(v.duration) >= 60);
  const clean = (s) => String(s).replace(/<[^>]+>/g, '').toLowerCase();
  const tokens = kw.toLowerCase().split(/\s+/).filter((t) => t.length >= 2);
  let best = pool[0], bestScore = -1;
  for (const v of pool) {
    const t = clean(v.title);
    let s = tokens.filter((tok) => t.includes(tok)).length;
    if (/合集|全集|collection|full/.test(t)) s += 1.5;
    if (parseDuration(v.duration) > 1800) s += 0.5;
    if (s > bestScore) { best = v; bestScore = s; }
  }
  return { bvid: best.bvid, title: String(best.title).replace(/<[^>]+>/g, ''), author: best.author, duration: best.duration };
}

async function pagelist(bvid) {
  const json = await getJson('https://api.bilibili.com/x/player/pagelist?bvid=' + bvid);
  if (json && json.code === 0 && Array.isArray(json.data)) return json.data;
  return null;
}

// ---------------- 等级定义与系列规划 ----------------

const LEVELS = [
  { lv: 1, title: 'L1 启蒙', en: 'Starter', theme: '零基础 · 语速慢 · 重复句型 · 儿歌启蒙' },
  { lv: 2, title: 'L2 基础', en: 'Basic', theme: '基础词汇 · 生活短句 · 慢速动画' },
  { lv: 3, title: 'L3 提高', en: 'Growing', theme: '完整对话 · 情节动画 · 学科英语' },
  { lv: 4, title: 'L4 进阶', en: 'Advanced', theme: '长对话 · 快语速 · 复杂情节' },
  { lv: 5, title: 'L5 挑战', en: 'Challenge', theme: '纪录片 · 快语速 · 专题术语' },
];

/** 旧365部等级映射（按周标题关键词，从上到下首个命中生效） */
const LEGACY_RULES = [
  [/佩奇|波波|Maisy|考拉|Penelope|Duggee|道奇|小猪|Timi|提米/i, 1],
  [/布鲁伊|Bluey|莎拉|乖乖鸭|朵拉|Dora|汪汪队|Paw|巴塔木|Hello/i, 2],
  [/查理|罗拉|Charlie|Lola|拼读|Phonics|单词|Word|阅读|Reading|数学|Number|校园|Big Muzzy|Muzzy/i, 3],
  [/科学|Science|工程|百科|物理|Physics|故事|Tales|综合|Blaze|STEM|语言|Word Party/i, 4],
  [/纪录片|BBC|星球|Planet|海洋|极地|非洲|草原|恐龙时代|宇宙|太阳系|天文|人体|微观|夜行|动物家族|安迪|Andy|Horrible|历史/i, 5],
];

/** 新增系列投放表：kw=搜索词 quota=目标集数 lv/tag/series */
const NEW_SERIES = [
  // L1
  { series: 'Super Simple Songs', kw: 'Super Simple Songs 英文儿歌 合集', quota: 55, lv: 1, tag: '英语儿歌' },
  { series: 'Numberblocks', kw: 'Numberblocks 数字积木 英文 第一季', quota: 30, lv: 1, tag: '数学启蒙' },
  { series: 'Alphablocks', kw: 'Alphablocks 字母积木 英文 自然拼读', quota: 26, lv: 1, tag: '自然拼读' },
  { series: 'Word Party 单词派对', kw: 'Word Party 英文版 单词派对', quota: 20, lv: 1, tag: '单词启蒙' },
  { series: '小猪佩奇 S2', kw: '小猪佩奇 第二季 英文版 合集', quota: 40, lv: 1, tag: '英语启蒙' },
  { series: '蓝色小考拉', kw: '蓝色小考拉 Penelope 英文 全集', quota: 25, lv: 1, tag: '英语启蒙' },
  { series: '小鼠波波', kw: 'Maisy 小鼠波波 英文版 全集', quota: 25, lv: 1, tag: '英语启蒙' },
  // L2
  { series: 'Hey Duggee', kw: 'Hey Duggee 道奇 英文版 第一季', quota: 30, lv: 2, tag: '生活英语' },
  { series: '布鲁伊 S1', kw: 'Bluey 布鲁伊 英文版 第一季 合集', quota: 30, lv: 2, tag: '生活英语' },
  { series: '爱探险的朵拉', kw: 'Dora the Explorer 英文版 合集', quota: 25, lv: 2, tag: '互动英语' },
  { series: '海底小纵队', kw: 'Octonauts 海底小纵队 英文版', quota: 22, lv: 2, tag: '海洋英语' },
  { series: '汪汪队立大功', kw: 'PAW Patrol 汪汪队 英文版', quota: 20, lv: 2, tag: '冒险英语' },
  { series: '莎拉与乖乖鸭', kw: 'Sarah and Duck 莎拉与乖乖鸭 英文', quota: 20, lv: 2, tag: '生活英语' },
  // L3
  { series: '好奇猴乔治', kw: 'Curious George 好奇猴乔治 英文', quota: 28, lv: 3, tag: '情节动画' },
  { series: ' Wild Kratts', kw: 'Wild Kratts 克拉特兄弟 英文版', quota: 25, lv: 3, tag: '动物科普' },
  { series: '丹尼尔老虎', kw: 'Daniel Tiger 英文版', quota: 20, lv: 3, tag: '情绪英语' },
  { series: '小公主苏菲亚', kw: 'Sofia the First 英文版', quota: 20, lv: 3, tag: '公主故事' },
  { series: '神奇校车', kw: 'The Magic School Bus 神奇校车 英文版', quota: 22, lv: 3, tag: '科学英语' },
  { series: '亚瑟', kw: 'Arthur 亚瑟 英文版 动画', quota: 18, lv: 3, tag: '校园英语' },
  // L4
  { series: '烈焰与怪物卡车', kw: 'Blaze and the Monster Machines 英文版', quota: 22, lv: 4, tag: 'STEM英语' },
  { series: '查理和罗拉', kw: 'Charlie and Lola 英文版 合集', quota: 20, lv: 4, tag: '兄妹日常' },
  { series: '小猪佩奇 S5', kw: '小猪佩奇 第五季 英文版 合集', quota: 30, lv: 4, tag: '生活英语' },
  { series: '布鲁伊 S2', kw: 'Bluey 布鲁伊 第二季 英文版', quota: 22, lv: 4, tag: '生活英语' },
  // L5
  { series: 'TED-Ed', kw: 'TED-Ed 英语 字幕合集', quota: 18, lv: 5, tag: '知识短片' },
  { series: 'SciShow Kids', kw: 'SciShow Kids 英文 科普', quota: 18, lv: 5, tag: '科学短片' },
  { series: '可怕的历史', kw: 'Horrible Histories 英文版', quota: 18, lv: 5, tag: '历史趣味' },
  { series: '致命60', kw: 'Deadly 60 BBC 英文', quota: 15, lv: 5, tag: '动物纪录片' },
  { series: '语言星球', kw: 'Planet Word BBC 纪录片', quota: 5, lv: 5, tag: '语言纪录片' },
];

/** 备用补差系列（总数不足1000时按序补） */
const EXTRA_SERIES = [
  { series: '小猪佩奇 S3', kw: '小猪佩奇 第三季 英文版 合集', quota: 40, lv: 2, tag: '英语启蒙' },
  { series: 'Numberblocks S2', kw: 'Numberblocks 第二季 英文版', quota: 25, lv: 2, tag: '数学启蒙' },
  { series: '粉红猪小妹 S4', kw: '小猪佩奇 第四季 英文版 合集', quota: 40, lv: 3, tag: '英语启蒙' },
  { series: 'Blaze S2', kw: 'Blaze Monster Machines Season 2 英文', quota: 20, lv: 4, tag: 'STEM英语' },
  { series: 'BBC 地球脉动', kw: 'Planet Earth BBC 英文版 纪录片', quota: 12, lv: 5, tag: '自然纪录片' },
];

// ---------------- 主流程 ----------------

(async () => {
  await bootstrapCookie();
  const legacy = JSON.parse(fs.readFileSync(SRC, 'utf8'));

  // 1) 旧条目标等级 + 纪录片判定
  const legacyItems = [];
  const seen = new Set();
  for (const cat of legacy.categories || []) {
    const isDocu = /纪录片|BBC/.test((cat.theme || '') + cat.title);
    for (const it of cat.items) {
      const text = `${cat.title} ${cat.en || ''} ${it.t} ${it.tag || ''}`;
      let lv = 3;
      for (const [re, l] of LEGACY_RULES) { if (re.test(text)) { lv = l; break; } }
      it.lv = isDocu ? Math.max(4, lv) : lv;
      it.catTitle = cat.title;
      it.docu = isDocu;
      if (it.bvid) seen.add(`${it.bvid}#${it.ep || 1}`);
      legacyItems.push(it);
    }
  }
  console.log('legacy items:', legacyItems.length);

  // 2) 新系列搜索 + 分P展开
  const fresh = [];
  let seq = 0;
  const seenBv = new Set();
  async function harvest(def) {
    console.log(`\n>> [${def.series}] 搜索: ${def.kw}`);
    const r = await searchVideo(def.kw);
    if (!r) { console.log('   搜索无结果，跳过'); return; }
    console.log(`   命中 ${r.bvid} ${r.title} (${r.duration}) by ${r.author}`);
    if (seenBv.has(r.bvid)) { console.log('   该BV已用过，跳过'); return; }
    seenBv.add(r.bvid);
    const pages = await pagelist(r.bvid);
    if (!pages || pages.length === 0) { console.log('   无分P，跳过'); return; }
    console.log(`   分P数: ${pages.length}, 取前 ${def.quota}`);
    let made = 0;
    for (let i = 0; i < pages.length && made < def.quota; i++) {
      const p = pages[i];
      const key = `${r.bvid}#${p.page}`;
      if (seen.has(key)) continue;
      seen.add(key);
      const partName = String(p.part || '').replace(/<[^>]+>/g, '').trim();
      const enish = /[a-zA-Z]{3}/.test(partName) ? partName : `${def.series} EP${p.page}`;
      seq++; made++;
      fresh.push({
        id: `n${String(seq).padStart(3, '0')}`,
        t: enish.length > 42 ? enish.slice(0, 42) : enish,
        kw: `${def.kw} ${p.page}`,
        bvid: r.bvid,
        ep: p.page,
        min: Math.max(1, Math.round((p.duration || 300) / 60)),
        tag: def.tag,
        lv: def.lv,
        series: def.series,
        resolved_title: r.title,
        resolved_author: r.author,
      });
    }
    console.log(`   生成 ${made} 条`);
    await sleep(700 + Math.floor(Math.random() * 300));
  }

  for (const def of NEW_SERIES) {
    if (legacyItems.length + fresh.length >= 1035) break;
    await harvest(def);
  }
  console.log(`\n新增条目: ${fresh.length}, 合计: ${legacyItems.length + fresh.length}`);

  // 3) 补差到 >=1000
  for (const def of EXTRA_SERIES) {
    if (legacyItems.length + fresh.length >= 1000) break;
    console.log(`\n!! 总数不足1000，补: ${def.series}`);
    await harvest(def);
  }

  // 4) 组装 v2 等级分类
  const all = [...legacyItems, ...fresh];
  const docuItems = all.filter((i) => i.docu);
  const englishItems = all.filter((i) => !i.docu);
  const categories = LEVELS.map((L) => ({
    id: `l${L.lv}`,
    title: L.title,
    en: L.en,
    theme: L.theme,
    level: L.lv,
    items: englishItems.filter((i) => i.lv === L.lv),
  })).concat([{
    id: 'docu',
    title: '纪录片',
    en: 'Documentaries',
    theme: '自然与科学 · 真实世界英语',
    level: 4,
    items: docuItems,
  }]).filter((c) => c.items.length > 0);

  const config = {
    version: 2,
    updated_at: new Date().toISOString().slice(0, 10),
    server: legacy.server,
    play: legacy.play || { default_qn: 64, autoplay_next: true },
    levels: LEVELS,
    categories,
    expanded: { at: new Date().toISOString(), total: all.length },
  };
  for (const c of categories) for (const i of c.items) { delete i.catTitle; delete i.docu; }
  fs.writeFileSync(DST, JSON.stringify(config, null, 1), 'utf8');
  fs.writeFileSync(ASSETS, JSON.stringify(config, null, 1), 'utf8');
  console.log('\n==== 分类统计 ====');
  for (const c of categories) console.log(`${c.title}: ${c.items.length}部`);
  console.log(`total: ${all.length} -> ${DST} & ${ASSETS}`);
})();
