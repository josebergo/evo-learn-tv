#!/usr/bin/env node
/**
 * 调用B站视频详情接口，为 server/config.json 中每个条目按 bvid 抓取封面图 URL（cover 字段），
 * 供App片源卡片做图片背景。1041个条目共享约170个BV号，按BV去重后增量抓取。
 * 用法: node scripts/fetch-covers.js
 */
const fs = require('fs');
const path = require('path');

const DST = path.join(__dirname, '..', 'server', 'config.json');
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
}

/** view接口返回 data.pic；412限流时指数退避 */
async function fetchCover(bvid, tries = 5) {
  const url = 'https://api.bilibili.com/x/web-interface/view?bvid=' + bvid;
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': UA, Cookie: cookie, Referer: 'https://www.bilibili.com/' },
      });
      const json = await res.json();
      if (json.code === 0 && json.data && json.data.pic) {
        let pic = json.data.pic;
        if (pic.startsWith('//')) pic = 'https:' + pic; // 协议相对地址补https
        else if (pic.startsWith('http://')) pic = pic.replace('http://', 'https://');
        return pic;
      }
      if (json.code === 412 || json.code === -412) {
        console.log(`  rate-limited, wait ${6 + i * 4}s...`);
        await sleep((6 + i * 4) * 1000);
        continue;
      }
      return null; // 视频失效/删除
    } catch (e) {
      await sleep(3000);
    }
  }
  return null;
}

(async () => {
  await bootstrapCookie();
  const config = JSON.parse(fs.readFileSync(DST, 'utf8'));
  const items = (config.categories || []).flatMap((c) => c.items);
  const pending = [...new Set(items.filter((i) => i.bvid && !i.cover).map((i) => i.bvid))];
  console.log(`items=${items.length} unique bvids without cover=${pending.length}`);

  const coverOf = {}; // bvid -> cover
  let ok = 0, fail = 0;
  const failures = [];
  for (let n = 0; n < pending.length; n++) {
    const bvid = pending[n];
    const pic = await fetchCover(bvid);
    coverOf[bvid] = pic;
    if (pic) ok++; else { fail++; failures.push(bvid); }
    process.stdout.write(`[${n + 1}/${pending.length}] ${bvid} ${pic ? 'OK' : 'FAIL'}   \r`);
    await sleep(700 + Math.floor(Math.random() * 300));
  }

  let filled = 0;
  for (const item of items) {
    if (item.bvid && coverOf[item.bvid]) {
      item.cover = coverOf[item.bvid];
      filled++;
    }
  }
  console.log(`\ndone: fetched=${ok} fail=${fail} items-filled=${filled}`);
  if (failures.length) console.log('failures:\n' + failures.join('\n'));
  config.cover_fetch = { at: new Date().toISOString(), ok, fail };
  fs.writeFileSync(DST, JSON.stringify(config, null, 1), 'utf8');
  console.log('written:', DST);
})();
