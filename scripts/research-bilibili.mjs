#!/usr/bin/env node
/**
 * 优质英语学习视频调研脚本：按候选系列搜索B站，抓取搜索结果并调 view 接口
 * 验证(分辨率/分P/播放量)，输出 JSON 供人工筛选编入精选文档。
 * 用法: node scripts/research-bilibili.mjs [输出文件]
 */
import fs from 'fs';

const OUT = process.argv[2] || 'bili-research.json';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
let COOKIE = '';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 候选系列清单：现有片单缺失或薄弱、教育口碑佳
const SERIES = [
  // ===== L1 启蒙(零基础) =====
  { id: 'muzzy', en: 'Big Muzzy', alias: ['muzzy', '玛泽'], kws: ['Big Muzzy 英文版', '玛泽的故事 Big Muzzy'], lv: 1, tag: '教学动画' },
  { id: 'wowenglish', en: 'Wow English', alias: ['steve', 'maggie', 'wow english'], kws: ['Wow English Steve and Maggie 英文'], lv: 1, tag: '真人互动' },
  { id: 'englishsingsing', en: 'English Sing Sing', alias: ['english sing', 'english singsing'], kws: ['English Sing Sing 儿童英语'], lv: 1, tag: '词汇句型' },
  { id: 'yakkadee', en: 'Yakka Dee', alias: ['yakka dee'], kws: ['Yakka Dee BBC 英文'], lv: 1, tag: '单词启蒙' },
  { id: 'blippi', en: 'Blippi', alias: ['blippi'], kws: ['Blippi 英文'], lv: 1, tag: '真人探索' },
  { id: 'puffinrock', en: 'Puffin Rock', alias: ['puffin rock', '海鹦'], kws: ['Puffin Rock 海鹦鹉岛 英文'], lv: 1, tag: '温馨动画' },
  { id: 'teemo', en: 'Tee and Mo', alias: ['tee and mo'], kws: ['Tee and Mo 英文'], lv: 1, tag: '生活英语' },
  { id: 'lovemonster', en: 'Love Monster', alias: ['love monster'], kws: ['Love Monster CBeebies 英文'], lv: 1, tag: '情绪英语' },
  { id: 'nellinora', en: 'Nelli and Nora', alias: ['nelli'], kws: ['Nelli and Nora 英文'], lv: 1, tag: '生活观察' },
  { id: 'cocomelon', en: 'Cocomelon', alias: ['cocomelon'], kws: ['Cocomelon 英文儿歌 高清'], lv: 1, tag: '英语儿歌' },
  { id: 'bumblenums', en: 'The Bumble Nums', alias: ['bumble nums'], kws: ['The Bumble Nums 英文'], lv: 1, tag: '趣味儿歌' },
  { id: 'peppa4k', en: 'Peppa Pig 官方高清', alias: ['小猪佩奇', 'peppa'], kws: ['小猪佩奇 英文版 1080P 合集'], lv: 1, tag: '英语启蒙' },
  { id: 'sss4k', en: 'Super Simple Songs 高清', alias: ['super simple'], kws: ['Super Simple Songs 1080P 合集'], lv: 1, tag: '英语儿歌' },
  { id: 'duggee', en: 'Hey Duggee 合集', alias: ['duggee', '道奇'], kws: ['Hey Duggee 嗨道奇 英文 合集'], lv: 1, tag: '英语启蒙' },

  // ===== L2 基础 =====
  { id: 'dora', en: 'Dora the Explorer', alias: ['dora', '朵拉'], kws: ['爱探险的朵拉 英文版'], lv: 2, tag: '互动教学' },
  { id: 'biffchip', en: 'Biff and Chip / The Magic Key', alias: ['biff', 'chip', 'magic key', '牛津'], kws: ['Biff and Chip 牛津树 动画 英文', 'The Magic Key 牛津树 英文'], lv: 2, tag: '分级阅读' },
  { id: 'gojetters', en: 'Go Jetters', alias: ['go jetters'], kws: ['Go Jetters 全球探险 英文'], lv: 2, tag: '地理启蒙' },
  { id: 'wallykazam', en: 'Wallykazam', alias: ['wallykazam', '沃利'], kws: ['Wallykazam 英文'], lv: 2, tag: '自然拼读' },
  { id: 'colourblocks', en: 'Colourblocks', alias: ['colourblocks', '颜色积木'], kws: ['Colourblocks 颜色积木 英文'], lv: 2, tag: '单词启蒙' },
  { id: 'okido', en: 'Messy Goes to Okido', alias: ['okido', 'messy'], kws: ['Messy Goes to Okido 英文'], lv: 2, tag: '科学英语' },
  { id: 'bluescluesyou', en: "Blue's Clues & You", alias: ['blue', 'clues'], kws: ["Blue's Clues & You 英文"], lv: 2, tag: '互动英语' },
  { id: 'jojo', en: 'JoJo and Gran Gran', alias: ['jojo', 'gran gran'], kws: ['JoJo and Gran Gran 英文'], lv: 2, tag: '生活英语' },
  { id: 'wombats', en: 'Work It Out Wombats!', alias: ['wombats'], kws: ['Work It Out Wombats 英文'], lv: 2, tag: '思维启蒙' },

  // ===== L3 提高 =====
  { id: 'elinor', en: 'Elinor Wonders Why', alias: ['elinor'], kws: ['Elinor Wonders Why 英文'], lv: 3, tag: '科学英语' },
  { id: 'xavier', en: 'Xavier Riddle', alias: ['xavier'], kws: ['Xavier Riddle 英文'], lv: 3, tag: '历史人物' },
  { id: 'inbestigators', en: 'The Inbestigators', alias: ['inbestigators'], kws: ['The Inbestigators 英文'], lv: 3, tag: '推理趣味' },
  { id: 'gravityfalls', en: 'Gravity Falls', alias: ['gravity falls', '怪诞小镇'], kws: ['怪诞小镇 Gravity Falls 英文'], lv: 4, tag: '冒险幽默' },

  // ===== L4/L5 科普与纪录片 =====
  { id: 'binocs', en: 'Dr. Binocs Show', alias: ['binocs'], kws: ['Dr Binocs 英文 科普'], lv: 3, tag: '科学英语' },
  { id: 'aumsum', en: "It's AumSum Time", alias: ['aumsum'], kws: ['AumSum 英文'], lv: 4, tag: '趣味科学' },
  { id: 'crashkids', en: 'Crash Course Kids', alias: ['crash course'], kws: ['Crash Course Kids 英文'], lv: 5, tag: '学科英语' },
  { id: 'teded', en: 'TED-Ed', alias: ['ted-ed', 'ted ed'], kws: ['TED-Ed 中英双语'], lv: 5, tag: '知识短片' },
  { id: 'ourplanet', en: 'Our Planet', alias: ['our planet', '我们的星球'], kws: ['Our Planet 我们的星球 纪录片'], lv: 5, tag: '自然纪录片' },
  { id: 'sevenworlds', en: 'Seven Worlds, One Planet', alias: ['seven worlds', '七个世界'], kws: ['七个世界 一个星球 Seven Worlds'], lv: 5, tag: '自然纪录片' },
  { id: 'frozenplanet', en: 'Frozen Planet', alias: ['frozen planet', '冰冻星球'], kws: ['冰冻星球 Frozen Planet BBC'], lv: 5, tag: '极地纪录片' },
  { id: 'd72', en: '72 Dangerous Animals', alias: ['72', 'dangerous animals', '危险动物'], kws: ['72 Dangerous Animals 危险动物'], lv: 4, tag: '动物趣味' },
  { id: 'absurd', en: 'Absurd Planet', alias: ['absurd planet', '荒诞'], kws: ['Absurd Planet 荒诞星球'], lv: 4, tag: '动物趣味' },
  { id: 'dogscats101', en: 'Dogs/Cats 101', alias: ['dogs 101', 'cats 101'], kws: ['Dogs 101 Animal Planet 萌犬'], lv: 4, tag: '动物百科' },
  { id: 'nightearth', en: 'Night on Earth', alias: ['night on earth'], kws: ['Night on Earth 地球之夜'], lv: 5, tag: '自然纪录片' },
  { id: 'hostile', en: 'Hostile Planet', alias: ['hostile planet'], kws: ['Hostile Planet 国家地理'], lv: 5, tag: '自然纪录片' },
  { id: 'whowas', en: 'The Who Was? Show', alias: ['who was'], kws: ['The Who Was Show 英文'], lv: 4, tag: '历史趣味' },
  { id: 'brainchild', en: 'Brainchild', alias: ['brainchild'], kws: ['Brainchild Netflix 英文'], lv: 4, tag: '科学真人秀' },

  // ===== 双语字幕专项 =====
  { id: 'blueybi', en: 'Bluey 双语增强', alias: ['布鲁伊', 'bluey'], kws: ['布鲁伊 双语 中英'], lv: 1, tag: '英语启蒙' },
  { id: 'bilingual', en: '双语字幕动画精选', alias: ['双语', '中英'], kws: ['英语启蒙动画 中英双语字幕 合集'], lv: 1, tag: '双语精选' },
];

async function boot() {
  const spi = await (await fetch('https://api.bilibili.com/x/frontend/finger/spi', { headers: { 'User-Agent': UA } })).json();
  COOKIE = `buvid3=${spi.data.b_3}; buvid4=${spi.data.b_4}`;
  console.log('cookie ok');
}

async function getJson(url, tries = 4) {
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(url, { headers: { 'User-Agent': UA, Cookie: COOKIE, Referer: 'https://www.bilibili.com/', Accept: 'application/json' } });
      if (res.status === 412) { console.log('  412, backoff...'); await sleep(5000 + i * 3000); continue; }
      return await res.json();
    } catch (e) { await sleep(2000); }
  }
  return null;
}

async function searchKw(kw) {
  const j = await getJson('https://api.bilibili.com/x/web-interface/search/type?search_type=video&page=1&keyword=' + encodeURIComponent(kw));
  if (!j || j.code !== 0) return [];
  return (j.data.result || []).map((r) => ({
    bvid: r.bvid,
    title: String(r.title).replace(/<[^>]+>/g, ''),
    author: r.author,
    play: r.play,
    duration: r.duration,
  }));
}

async function enrich(bvid) {
  const j = await getJson(`https://api.bilibili.com/x/web-interface/view?bvid=${bvid}`);
  if (!j || j.code !== 0) return null;
  const d = j.data;
  let maxq = 0;
  const cid = d.pages?.[0]?.cid;
  if (cid) {
    const p = await getJson(`https://api.bilibili.com/x/player/playurl?bvid=${bvid}&cid=${cid}&qn=127&fnval=16&fourk=1`);
    if (p && p.code === 0) maxq = Math.max(...(p.data.accept_quality || [0]));
  }
  return {
    bvid, maxq,
    dim: `${d.dimension.width}x${d.dimension.height}`,
    pages: d.pages.length,
    dur: d.duration,
    view: d.stat.view,
    like: d.stat.like,
    title: d.title,
    owner: d.owner.name,
  };
}

function relevant(s, item) {
  const t = (item.title + ' ' + item.author).toLowerCase();
  return s.alias.some((a) => t.includes(a.toLowerCase())) || s.kws.some((k) => t.includes(k.split(' ')[0].toLowerCase()));
}

async function main() {
  await boot();
  const out = [];
  for (const s of SERIES) {
    console.log(`== ${s.id} (${s.en})`);
    const pool = [];
    for (const kw of s.kws) {
      const rs = await searchKw(kw);
      console.log(`  [${kw}] hits=${rs.length}`);
      pool.push(...rs);
      await sleep(1300);
    }
    // 相关度过滤 + 按播放量取 top
    const seen = new Set();
    const cands = pool
      .filter((r) => relevant(s, r) && !seen.has(r.bvid) && seen.add(r.bvid))
      .sort((a, b) => b.play - a.play)
      .slice(0, 5);
    for (const c of cands) {
      const e = await enrich(c.bvid);
      if (e) out.push({ series: s.id, en: s.en, lv: s.lv, tag: s.tag, ...e, searchPlay: c.play });
      await sleep(400);
    }
    console.log(`  enriched ${cands.length}`);
  }
  fs.writeFileSync(OUT, JSON.stringify(out, null, 1), 'utf-8');
  console.log('DONE', out.length, '->', OUT);
}
main().catch((e) => { console.error(e); process.exit(1); });
