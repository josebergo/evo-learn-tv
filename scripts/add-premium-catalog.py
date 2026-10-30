#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 docs/premium-bilingual-catalog.md 精选资源并入片单(server/config.json 与 assets 离线兜底)。
条目数据来自 scripts/premium-catalog-data.json(B站API验证)。用法: python scripts/add-premium-catalog.py"""
import json, re, datetime

ROOT = __file__.rsplit('\\', 2)[0] if '\\' in __file__ else __file__.rsplit('/', 2)[0]
DATA = json.load(open(ROOT + '/scripts/premium-catalog-data.json', encoding='utf-8'))

# bvid → (目标分类, tag, 系列名, 搜索词kw, 单集分钟min)
META = {
 'BV1tf4y1U7gS': ('l1','教学动画','Big Muzzy 新版3D','Big Muzzy 英文版 新版',13),
 'BV1bWYizEEz6': ('l1','教学动画','Big Muzzy 经典版','big muzzy gondoland 英文版',13),
 'BV1PJ8FzvEnf': ('l1','真人互动','Wow English','Wow English 史蒂夫 英文',13),
 'BV1qbC6YtE91': ('l1','词汇句型','English Sing Sing','English Sing Sing 儿童英语',8),
 'BV1rS411A7eW': ('l1','单词启蒙','Yakka Dee 开口说','Yakka Dee 英文',5),
 'BV1BycUecEqy': ('l1','英语启蒙','动物王国大冒险','动物王国大冒险 英语',3),
 'BV1AgouY7EFE': ('l1','英语儿歌','Cocomelon 儿歌','Cocomelon 英文儿歌',4),
 'BV18T3G6jEVM': ('l1','英语启蒙','布鲁伊·真双语','Bluey 布鲁伊 双语 中英 第一季',7),
 'BV1HhHA6NEZu': ('l1','英语启蒙','布鲁伊·双音频','Bluey 布鲁伊 双音频 第一季',10),
 'BV1QAKp6wEEi': ('l1','英语启蒙','Pocoyo 小P优优','Pocoyo 小P优优 英文',7),
 'BV1RT42117Gd': ('l1','生活英语','Tee and Mo','Tee and Mo 英文',7),
 'BV1KC411a7Dz': ('l1','情绪英语','Love Monster','Love Monster 英文',7),
 'BV16cudzaEeQ': ('l1','英语儿歌','Super Simple Songs','Super Simple Songs 高清 合集',19),
 'BV1Te4y1c7sA': ('l1','英语启蒙','嗨道奇 Hey Duggee','Hey Duggee 嗨道奇 英文 合集',7),
 'BV1vT411h7Lw': ('l1','双语精选','英语绘本动画','英语启蒙绘本动画 中英双语',3),
 'BV1iBBFYhEKZ': ('l2','分级阅读','牛津树 Biff and Chip','Biff and Chip 牛津树 真人剧',11),
 'BV1Y1421y72F': ('l2','科学英语','Do You Know','BBC Do You Know Maddie',14),
 'BV1dkCCBRExW': ('l2','地理启蒙','Go Jetters 探险冲冲冲','Go Jetters 英文',11),
 'BV169rrBXExP': ('l2','地理启蒙','Go Jetters 第3季','Go Jetters 第三季 英文',11),
 'BV1nt421G7wv': ('l2','单词启蒙','Colourblocks 颜色积木','Colourblocks 颜色积木 英文',5),
 'BV1nA4m1N7n9': ('l2','单词启蒙','Colourblocks 第2季','Colourblocks 第二季 英文',5),
 'BV1fs3cz5EL3': ('l2','科学英语','Messy Goes to Okido','Messy Goes to Okido 英文',11),
 'BV1V94y1e7jG': ('l2','生活英语','JoJo & Gran Gran','JoJo and Gran Gran 英文',11),
 'BV1QEnd6FEuj': ('l2','数学启蒙','数字积木·双语版','Numberblocks 数字积木 中英字幕',20),
 'BV1sJ4m1w79t': ('l3','科学英语','小好奇埃莉诺','Elinor Wonders Why 英文',26),
 'BV1v98MzVEdT': ('l3','推理趣味','少年侦探所·双语','The Inbestigators 双语',5,'超棒少年侦探所'),
 'BV1q2bazGERu': ('l3','科学英语','百诺博士秀','Dr Binocs 百诺博士 中英字幕',6),
 'BV1j9qdB5E1j': ('l3','历史趣味','Xavier 秘密博物馆','Xavier Riddle 秘密博物馆 英文',24),
 'BV1M9pnz7EZF': ('l3','动画电影','Puffin Rock 大电影','Puffin Rock 海鹦鹉岛 电影',70),
 'BV1pB4kzrELU': ('l4','冒险幽默','怪诞小镇','Gravity Falls 怪诞小镇 全两季',155),
 'BV1zK4y1h7jg': ('l4','历史趣味','Who Was 名人秀·双语','The Who Was Show 双语字幕',24),
 'BV1aE411L7GU': ('l4','科学英语','妙想科学秀·双语','Brainchild 妙想科学秀 双语',22),
 'BV1qE41157bM': ('l4','动物趣味','72危险动物·拉美','72 Dangerous Animals 拉丁美洲',44),
 'BV1B7411F78V': ('l4','动物趣味','72危险动物·亚洲','72 Dangerous Animals 亚洲',46),
 'BV1sV411d7kW': ('l4','动物趣味','怪诞星球·双语','Absurd Planet 怪诞星球 中英双字',20),
 'BV1Qwb5zJE6K': ('l4','趣味科学','AumSum 时间',"It's AumSum Time 英文",6),
 'BV1Gf4y1y7wc': ('l5','知识短片','TED-Ed 百科','TED-Ed 中英CC',5),
 'BV1TK421i72D': ('l5','知识短片','TED-Ed 高清精选','TED-Ed 高清',5),
 'BV1B7411v73M': ('l5','学科英语','统计学速成课·双语','Crash Course 统计学 中英',12),
 'BV1oJ41137oE': ('docu','自然纪录片','我们的星球','Our Planet 我们的星球',50),
 'BV1Tm4y1b7Hq': ('docu','自然纪录片','夜色中的地球·双语','Earth At Night 夜色中的地球',28,'Earth at Night in Color 夜色中的地球'),
 'BV1oDS1BUEUR': ('docu','极地纪录片','冰冻星球2·4K','Frozen Planet 2 冰冻星球2 4K',53),
 'BV1V64y1H7LZ': ('docu','自然纪录片','七个世界精讲','Seven Worlds One Planet 七个世界 一个星球',70),
}
LV = {'l1':1,'l2':2,'l3':3,'l4':4,'l5':5,'docu':5}

def clean_part(part, ep, strip=None):
    """把分P名清洗成适合直接展示的标题片段"""
    p = part.replace('_logo', '').replace('.', ' ')
    p = re.sub(r'\[[^\]]*\]', ' ', p)
    if strip:
        p = p.replace(strip, ' ')
    p = re.sub(r'[_]?part[_ ]?\d+$', '', p)
    p = re.sub(r'^[\s\-_=·]+', '', p)
    p = re.sub(r'\s+', ' ', p).strip()
    p = re.sub(r'\bS(\d)\s*E?(\d\d)\b', r'S\1E\2', p)          # S1 01 → S1E01
    p = re.sub(r'^\d{4}-\d\d-\d\d\s*', '', p)                   # TED-Ed 去日期
    p = p.split(' - ')[0]                                       # 去作者尾缀
    if not p or p.isdigit() or len(p) < 3:
        return None
    p = p[:46]
    return p.rsplit(' ', 1)[0] if ' ' in p and len(p) == 46 else p

def build_items():
    items, n = [], 0
    for bv, meta in META.items():
        cat, tag, name, kw, mins = meta[:5]
        strip = meta[5] if len(meta) > 5 else None
        d = DATA[bv]
        for e in d['eps']:
            n += 1
            frag = clean_part(e['part'], e['ep'], strip)
            multi = len(d['eps']) > 1
            title = f"{name} · {frag}" if frag and multi else (name if not multi else f"{name} 第{e['ep']}集")
            items.append({
                "id": f"p{n:03d}", "t": title, "kw": kw, "ep": e['ep'], "min": mins, "tag": tag,
                "bvid": bv, "resolved_title": d['title'], "resolved_author": d['owner'],
                "resolved_duration": f"{e['dur']//60}:{e['dur']%60:02d}", "lv": LV[cat], "cover": d['pic'],
                "_cat": cat,
            })
    return items

def main():
    items = build_items()
    today = datetime.date.today().isoformat()
    for path in ['server/config.json', 'app/src/main/assets/config.default.json']:
        full = ROOT + '/' + path
        cfg = json.load(open(full, encoding='utf-8'))
        # 幂等:去掉旧的精选增补条目再重灌
        for c in cfg['categories']:
            c['items'] = [it for it in c['items'] if it.get('bvid') not in META]
        bycat = {}
        for it in items:
            bycat.setdefault(it['_cat'], []).append(it)
        for c in cfg['categories']:
            if c['id'] in bycat:
                for it in bycat[c['id']]:
                    c['items'].append({k: v for k, v in it.items() if k != '_cat'})
        cfg['version'] = 4
        cfg['updated_at'] = today
        cfg['server']['notice'] = "本片单为二~三年级小学生定制，仅包含英语动画与纪录片；已并入2026-10精选增补（43部优质双语/高清系列）。家长可通过服务器远程更新此配置。"
        json.dump(cfg, open(full, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        total = sum(len(c['items']) for c in cfg['categories'])
        print(f"{path}: version=4 items={total} (+{len(items)})")
    print('new item ids:', items[0]['id'], '...', items[-1]['id'])

if __name__ == '__main__':
    main()
