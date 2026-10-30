#!/usr/bin/env node
/**
 * 片单补差：expand-catalog.js 不足1000时继续扩充。
 * 改进：每个系列取搜索结果前3个候选BV逐个展开，直到配额满；系列清单更偏合集类。
 * 用法: node scripts/top-up-catalog.js
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const CFG = path.join(ROOT, 'server', 'config.json');
const ASSETS = path.join(ROOT, 'app', 'src', 'main', 'assets', 'config.default.json');
const TARGET = 1010;
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
let cookie = '';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function bootstrapCookie() {
  try {
    const res = await fetch('https://www.bilibili.com/', { headers: { 'User-Agent': UA } });
    const set = res.headers.getSetCookie ? res.headers.getSetCookie() : [];
    cookie = set.map((c) => c.split(';')[0]).join('; ');
  } catch (e) {}
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

async function searchVideos(kw, want) {
  const json = await getJson('https://api.bilibili.com/x/web-interface/search/type?search_type=video&page=1&keyword=' + encodeURIComponent(kw));
  const result = json && json.code === 0 && json.data && json.data.result;
  if (!result) return [];
  const clean = (s) => String(s).replace(/<[^>]+>/g, '');
  const tokens = kw.toLowerCase().split(/\s+/).filter((t) => t.length >= 2);
  return result
    .filter((v) => parseDuration(v.duration) >= 240)
    .map((v) => {
      const t = clean(v.title).toLowerCase();
      let s = tokens.filter((tok) => t.includes(tok)).length;
      if (/合集|全集|collection|full|season|第.{1,3}季/.test(t)) s += 1.5;
      return { bvid: v.bvid, title: clean(v.title), author: v.author, duration: v.duration, score: s };
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, want);
}

async function pagelist(bvid) {
  const json = await getJson('https://api.bilibili.com/x/player/pagelist?bvid=' + bvid);
  if (json && json.code === 0 && Array.isArray(json.data)) return json.data;
  return null;
}

const PLAN = [
  { series: 'Super Simple Songs', kw: 'Super Simple Songs 儿歌 英文 合集', quota: 60, lv: 1, tag: '英语儿歌' },
  { series: '鹅妈妈童谣', kw: 'Mother Goose Club 英文 童谣', quota: 20, lv: 1, tag: '英语儿歌' },
  { series: '小猪佩奇 S6', kw: '小猪佩奇 第六季 英文版', quota: 40, lv: 1, tag: '英语启蒙' },
  { series: '小猪佩奇 S7', kw: '小猪佩奇 第七季 英文版 中英字幕', quota: 40, lv: 2, tag: '英语启蒙' },
  { series: 'Numberblocks S3', kw: '数字积木 第三季 Numberblocks', quota: 25, lv: 2, tag: '数学启蒙' },
  { series: '彩虹宝宝', kw: 'True and the Rainbow Kingdom 英文', quota: 20, lv: 2, tag: '奇幻英语' },
  { series: '本和霍利', kw: 'Ben and Hollys Little Kingdom 英文版', quota: 25, lv: 2, tag: '生活英语' },
  { series: '字母积木 S2', kw: 'Alphablocks 第二季 英文', quota: 26, lv: 2, tag: '自然拼读' },
  { series: '彼得兔', kw: 'Peter Rabbit 英文版 动画', quota: 25, lv: 3, tag: '童话英语' },
  { series: '数学城小兄妹', kw: 'Team Umizoomi 英文版', quota: 20, lv: 3, tag: '数学英语' },
  { series: '泡泡孔雀鱼', kw: 'Bubble Guppies 英文版', quota: 20, lv: 3, tag: '校园英语' },
  { series: '神奇校车 Rides Again', kw: 'Magic School Bus Rides Again 英文', quota: 15, lv: 4, tag: '科学英语' },
  { series: '小猪佩奇 S8', kw: '小猪佩奇 第八季 英文版', quota: 30, lv: 4, tag: '生活英语' },
  { series: 'BBC 蓝色星球', kw: 'Blue Planet BBC 英文 纪录片', quota: 12, lv: 5, tag: '海洋纪录片' },
  { series: 'BBC 人类星球', kw: 'Human Planet BBC 英文版', quota: 10, lv: 5, tag: '人文纪录片' },
  { series: '安迪的恐龙冒险', kw: 'Andys Dinosaur Adventures 英文', quota: 15, lv: 5, tag: '恐龙纪录片' },
  { series: '安迪的野外冒险', kw: 'Andys Wild Adventures 英文', quota: 15, lv: 5, tag: '动物纪录片' },
  { series: '如果我是一只动物', kw: 'If I Were an Animal 英文 纪录片', quota: 15, lv: 5, tag: '动物纪录片' },
];

(async () => {
  await bootstrapCookie();
  const config = JSON.parse(fs.readFileSync(CFG, 'utf8'));
  const seen = new Set();
  let maxSeq = 0;
  for (const c of config.categories) {
    for (const i of c.items) {
      seen.add(`${i.bvid}#${i.ep || 1}`);
      const m = /^n(\d+)$/.exec(i.id || '');
      if (m) maxSeq = Math.max(maxSeq, Number(m[1]));
    }
  }
  const count = () => config.categories.reduce((n, c) => n + c.items.length, 0);
  console.log('当前总数:', count());
  let seq = maxSeq;
  const freshByLv = {};

  for (const def of PLAN) {
    if (count() >= TARGET) break;
    console.log(`\n>> [${def.series}] ${def.kw}`);
    const cands = await searchVideos(def.kw, 3);
    if (!cands.length) { console.log('   无候选'); continue; }
    let made = 0;
    for (const r of cands) {
      if (made >= def.quota) break;
      if (seen.has(`${r.bvid}#1`)) continue;
      const pages = await pagelist(r.bvid);
      if (!pages || pages.length === 0) continue;
      console.log(`   ${r.bvid} "${r.title}" 分P=${pages.length} 取${Math.min(pages.length, def.quota - made)}`);
      for (const p of pages) {
        if (made >= def.quota) break;
        const key = `${r.bvid}#${p.page}`;
        if (seen.has(key)) continue;
        seen.add(key);
        const partName = String(p.part || '').replace(/<[^>]+>/g, '').trim();
        const enish = /[a-zA-Z]{3}/.test(partName) ? partName : `${def.series} EP${p.page}`;
        seq++; made++;
        (freshByLv[def.lv] = freshByLv[def.lv] || []).push({
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
      await sleep(600);
    }
    console.log(`   生成 ${made}`);
    await sleep(700 + Math.floor(Math.random() * 300));
  }

  // 汇入对应等级分类
  for (const c of config.categories) {
    if (c.id === 'docu') continue;
    const lv = c.level;
    if (freshByLv[lv]) { c.items.push(...freshByLv[lv]); delete freshByLv[lv]; }
  }
  // 剩余(如纪录片关键词命中的lv条目)塞进等级最接近的类
  for (const [lv, items] of Object.entries(freshByLv)) {
    const cat = config.categories.find((c) => c.level === Number(lv)) || config.categories[0];
    cat.items.push(...items);
  }

  config.version = 2;
  config.updated_at = new Date().toISOString().slice(0, 10);
  config.expanded = { at: new Date().toISOString(), total: count() };
  fs.writeFileSync(CFG, JSON.stringify(config, null, 1), 'utf8');
  fs.writeFileSync(ASSETS, JSON.stringify(config, null, 1), 'utf8');
  console.log('\n==== 最终统计 ====');
  for (const c of config.categories) console.log(`${c.title}: ${c.items.length}部`);
  console.log('total:', count());
})();
