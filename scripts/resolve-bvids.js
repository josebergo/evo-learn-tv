#!/usr/bin/env node
/**
 * 调用B站搜索接口，为 config.default.json 中的每个条目解析 BV 号，
 * 输出 server/config.json（含 bvid 字段），供App直接播放与MD文档生成使用。
 * 用法: node scripts/resolve-bvids.js
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const SRC = path.join(ROOT, 'server', 'config.default.json');
const DST = path.join(ROOT, 'server', 'config.json');
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
let cookie = '';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function bootstrapCookie() {
  try {
    const res = await fetch('https://www.bilibili.com/', { headers: { 'User-Agent': UA } });
    const set = res.headers.getSetCookie ? res.headers.getSetCookie() : [];
    cookie = set.map((c) => c.split(';')[0]).join('; ');
  } catch (e) {
    console.error('bootstrap cookie failed:', e.message);
  }
  if (!cookie.includes('buvid3')) {
    cookie += (cookie ? '; ' : '') + 'buvid3=infoc';
  }
  console.log('cookie:', cookie.slice(0, 80));
}

async function searchFirstVideo(kw, tries = 5) {
  const url =
    'https://api.bilibili.com/x/web-interface/search/type?search_type=video&page=1&keyword=' +
    encodeURIComponent(kw);
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': UA, Cookie: cookie, Referer: 'https://www.bilibili.com/' },
      });
      const json = await res.json();
      if (json.code === 0 && json.data && json.data.result && json.data.result.length > 0) {
        const pick = pickBest(json.data.result, kw);
        return {
          bvid: pick.bvid,
          title: String(pick.title).replace(/<[^>]+>/g, ''),
          author: pick.author,
          duration: pick.duration,
        };
      }
      if (json.code === 412 || json.code === -412) {
        console.log(`  rate-limited, wait ${6 + i * 4}s...`);
        await sleep((6 + i * 4) * 1000);
        continue;
      }
      return null; // 无结果
    } catch (e) {
      await sleep(3000);
    }
  }
  return null;
}

function parseDuration(d) {
  if (!d) return 0;
  const parts = String(d).split(':').map(Number);
  return parts.reduce((acc, p) => acc * 60 + p, 0);
}

/** 标题与关键词的相关度打分：命中的关键词分词越多越可信 */
function relevance(title, kw) {
  const t = String(title).toLowerCase();
  let s = 0;
  for (const tok of String(kw).toLowerCase().split(/\s+/)) {
    if (tok.length >= 2 && t.includes(tok)) s++;
  }
  return s;
}

function pickBest(result, kw) {
  const candidates = result.slice(0, 15);
  const valid = candidates.filter((v) => parseDuration(v.duration) >= 60);
  const pool = valid.length ? valid : candidates;
  let best = pool[0], bestScore = -1;
  for (const v of pool) {
    const sc = relevance(v.title, kw);
    if (sc > bestScore) { best = v; bestScore = sc; }
  }
  return best;
}

(async () => {
  await bootstrapCookie();
  // 增量模式：已解析过的（config.json里带bvid）跳过，只补缺失项
  const srcFile = fs.existsSync(DST) ? DST : SRC;
  console.log('source:', srcFile);
  const config = JSON.parse(fs.readFileSync(srcFile, 'utf8'));
  let ok = 0, fail = 0, skip = 0;
  const failures = [];
  const cats = config.categories || [];
  const total = cats.reduce((n, c) => n + c.items.filter((i) => !i.bvid).length, 0);
  let n = 0;
  for (const cat of cats) {
    for (const item of cat.items) {
      if (item.bvid) { skip++; continue; }
      n++;
      const r = await searchFirstVideo(item.kw);
      if (r) {
        item.bvid = r.bvid;
        item.resolved_title = r.title;
        item.resolved_author = r.author;
        item.resolved_duration = r.duration;
        ok++;
      } else {
        fail++;
        failures.push(`${item.id} ${item.kw}`);
      }
      process.stdout.write(`[${n}/${total}] ${item.id} ${r ? 'OK ' + r.bvid : 'FAIL'}   \r`);
      await sleep(600 + Math.floor(Math.random() * 400));
    }
  }
  console.log(`\ndone: ok=${ok} fail=${fail} skip(already)=${skip}`);
  if (failures.length) console.log('failures:\n' + failures.join('\n'));
  config.resolved = { at: new Date().toISOString(), ok, fail };
  fs.writeFileSync(DST, JSON.stringify(config, null, 1), 'utf8');
  console.log('written:', DST);
})();
