#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从验证数据生成《优质双语英语学习视频精选》文档。用法: python scripts/gen-premium-md.py"""
import json, sys, datetime

ROOT = __file__.rsplit('\\', 2)[0] if '\\' in __file__ else __file__.rsplit('/', 2)[0]
data = json.load(open(ROOT + '/scripts/premium-catalog-data.json', encoding='utf-8'))

QN = {120: ('4K', '🔥'), 116: ('1080P60', ''), 112: ('1080P高码', ''), 80: ('1080P', ''), 74: ('720P60', ''), 64: ('720P', '')}

# (bv, 星, 字幕, 标注名, 推荐理由)
CATS = [
("L1 启蒙 · 零基础起步", "语速慢、句型重复、画面直观;适合刚接触英语的孩子建立『听懂英语』的信心", [
 ("BV1tf4y1U7gS", "★★★", "内嵌教学字幕", "Big Muzzy 新版3D", "BBC经典教学动画3D重制:语速是所有动画里最慢的,句型重复率极高,剧情完整,专为零基础设计,公认英语启蒙第一课"),
 ("BV1bWYizEEz6", "★★", "英文原声", "Big Muzzy 经典版", "1986年原版手绘(BBC),英语教学动画鼻祖,画质重制的Gondoland完整故事"),
 ("BV1PJ8FzvEnf", "★★★", "内嵌字幕", "Wow English 全9季", "油管超人气的真人外教Steve+乌鸦玩偶Maggie:夸张肢体+高频重复,把『不敢开口』变成『跟着喊』,零基础开口神器"),
 ("BV1qbC6YtE91", "★★★", "英文原声+AI双语", "English Sing Sing", "按主题分类的ESL『动画词典』:单词→句型→对话三级递进,体系性最强的启蒙资源,从颜色数字一直覆盖到日常对话"),
 ("BV1rS411A7eW", "★★", "英文原声+AI双语", "Yakka Dee", "BBC CBeebies出品,每集只教1个单词、变着花样重复,专治『不肯开口』,已入选BBC官方启蒙名单"),
 ("BV1BycUecEqy", "★★★", "英文原声+AI双语", "动物王国大冒险", "句型极简+情节爆笑的动物定格动画,B站英语启蒙现象级作品(455万播放),孩子会主动追着看"),
 ("BV1AgouY7EFE", "★★", "英文原声+AI双语", "Cocomelon 儿歌", "全球播放量第一的3D儿歌频道,画面精美、旋律洗脑,磨耳朵首选"),
 ("BV18T3G6jEVM", "★★★", "★真双语音轨(先中后英)", "布鲁伊 Bluey S1 双语版", "艾美奖家庭动画的『先中文后英文』真双语版:零基础也能看懂剧情再听英文,是目前最好的亲子英语动画"),
 ("BV1HhHA6NEZu", "★★", "一句中文+一句英文交替", "布鲁伊 Bluey S1 双音频", "同季另一版本:每句先汉语后英语,『专治听不懂』,适合刚过渡到纯英文前的桥梁期"),
 ("BV1QAKp6wEEi", "★★", "★中英双音轨+内挂软字幕", "Pocoyo 小P优优 S1", "旁白极简、语速慢、色彩明快的西班牙国宝动画,可切换中/英音轨,5分钟一集"),
 ("BV1RT42117Gd", "★", "英文原声+AI双语", "Tee and Mo", "BBC出品的猿猴母子日常,生活场景词汇丰富,适合亲子共看"),
 ("BV1KC411a7Dz", "★", "英文原声+AI双语", "Love Monster", "CBeebies情绪主题动画:一只害羞毛绒怪的爱情课,句子简单温暖,顺带情绪管理"),
 ("BV16cudzaEeQ", "★★", "英文原声", "Super Simple Songs 高清合集", "现有片单SSS儿歌的1080P大合集整合版(426首),磨耳朵系统性更强"),
 ("BV1Te4y1c7sA", "★★", "英文原声+AI双语", "Hey Duggee 嗨道奇 1080P", "现有片单道奇(480P)的1080P升级:BBC幼儿园+史努比徽章,7分钟一集"),
 ("BV1vT411h7Lw", "★", "内嵌中英双语字幕", "经典绘本动画故事", "英语启蒙绘本动画化(28集),双语字幕,睡前故事好搭档"),
]),
("L2 基础 · 词汇与句型扩展", "有完整情节的生活/科普动画,在场景中学词汇与句型", [
 ("BV1iBBFYhEKZ", "★★★", "英文原声+AI双语", "Biff and Chip 牛津树真人剧", "牛津阅读树官方真人剧(全64集):与牛津树读物人物100%联动,『看完动画读同级绘本』形成闭环,英语分级阅读最强搭档"),
 ("BV1Y1421y72F", "★★★", "高清英字+AI双语", "Do You Know 全5季", "BBC科普神片:Maddie带你看懂牙刷怎么造、爆米花为什么爆,句型清晰、语速适中,B站73万播放的口碑之作"),
 ("BV1dkCCBRExW", "★★", "英文原声+AI双语", "Go Jetters 探险冲冲冲 S1-2", "BBC地理探险动画:每集打卡一个世界地标+完成纠错任务,地理词汇+冒险剧情"),
 ("BV169rrBXExP", "★", "英文原声+内嵌英字", "Go Jetters S3 完整版", "第三季英文原声+英文字幕版,前两季看完的续杯资源"),
 ("BV1nt421G7wv", "★★", "英文原声+AI双语", "Colourblocks 颜色积木 S1", "Numberblocks原班团队:颜色认知+颜色词汇+混色原理,和数字积木组成『Blocks宇宙』"),
 ("BV1nA4m1N7n9", "★", "英文原声+AI双语", "Colourblocks S2", "颜色积木第二季(同UP主出品,画质一致)"),
 ("BV1fs3cz5EL3", "★★", "英文原声+AI双语", "Messy Goes to Okido", "STEM科普动画:小怪兽梅西每集追一个『为什么』,60帧1080P画面丝滑"),
 ("BV1V94y1e7jG", "★★", "中英字幕", "JoJo & Gran Gran", "CBeebies真人+动画:祖孙的四季日常,慢速生活英语,教孩子『期待与耐心』"),
 ("BV1QEnd6FEuj", "★★", "中英双语字幕", "Numberblocks 数字积木 1-9季", "现有片单数字积木(720P)的升级:180集全+中英双语字幕,数学英语双修"),
]),
("L3 提高 · 完整对话与学科英语", "情节更复杂、对白更密集,开始接触学科词汇", [
 ("BV1sJ4m1w79t", "★★★", "内嵌英字+AI双语", "Elinor Wonders Why", "PBS艾美奖提名STEM动画:好奇心女孩的科学观察日记,科学词汇与日常句型平衡得最好"),
 ("BV1v98MzVEdT", "★★★", "内嵌双语字幕", "The Inbestigators 少年侦探所", "Netflix澳洲儿童侦探剧:4个小学生开侦探所,口语句型密集、表达地道,是『口语教科书』"),
 ("BV1q2bazGERu", "★★★", "内嵌中英字幕", "Dr. Binocs 百诺博士秀", "趣味科普顶流:百诺博士讲科学,单集5分钟、中英字幕,天文地理人体全覆盖"),
 ("BV1j9qdB5E1j", "★★", "英文原声+AI双语", "Xavier Riddle 秘密博物馆", "PBS历史名人穿越剧:与达芬奇、爱因斯坦做朋友,历史+人物品质启蒙"),
 ("BV1M9pnz7EZF", "★", "英文原声+AI双语", "Puffin Rock 大电影", "海鹦兄妹温馨大电影:爱尔兰动画,慢速旁白,适合安静时刻"),
]),
("L4 进阶 · 快语速与复杂情节", "对话密度高、梗多,适合已能看懂L3的孩子挑战真听力", [
 ("BV1pB4kzrELU", "★★★", "英文原声(配AI双语)", "Gravity Falls 怪诞小镇", "迪士尼口碑神作:双胞胎兄妹的暑假谜案,对话密度高+美式幽默,进阶听力磨耳朵的天花板(建议开AI双语轨)"),
 ("BV1zK4y1h7jg", "★★★", "★官方双语字幕", "The Who Was? Show", "Netflix名人历史喜剧秀:官方中英双语字幕,把名人传记做成喜剧小品,历史+娱乐性拉满"),
 ("BV1aE411L7GU", "★★★", "★官方双语字幕", "Brainchild 妙想科学秀", "Netflix科学真人秀:主持人口齿清晰、节奏明快,官方双语字幕,覆盖社交媒体/记忆/太空等话题"),
 ("BV1qE41157bM", "★★", "★官方双语字幕", "72 Dangerous Animals 拉美篇", "Netflix危险动物排行榜:趣味解说+官方双语字幕,排行榜形式让孩子欲罢不能"),
 ("BV1B7411F78V", "★★", "★官方双语字幕", "72 Dangerous Animals 亚洲篇", "同系列亚洲篇(官方双语字幕),与中国孩子的生活半径更近"),
 ("BV1sV411d7kW", "★★", "内嵌中英双字", "Absurd Planet 怪诞星球", "Netflix搞笑动物纪录片:动物视角段子手解说,笑声中学动物词汇"),
 ("BV1Qwb5zJE6K", "★★", "英文原声+AI双语", "It's AumSum Time", "脑洞科学问答:『如果地球停止转会怎样』,快语速+专业词汇,L4科普担当"),
]),
("L5 挑战 · 纪录片与知识短片", "真实语速的纪录片与知识动画,术语密集", [
 ("BV1Gf4y1y7wc", "★★★", "★B站中英CC字幕", "TED-Ed 百科合集", "5分钟一个知识主题的动画百科(约1000集),B站自带中英CC字幕,App可直接双语显示,1228万播放"),
 ("BV1TK421i72D", "★★", "英文原声+AI双语", "TED-Ed 高清精选", "1080P高清版TED-Ed精选(85集),对画质有要求时选这个"),
 ("BV1oJ41137oE", "★★★", "内嵌双语/英文原声", "Our Planet 我们的星球", "Netflix顶配自然纪录片:大卫·爱登堡解说、摄影顶级,每帧都是壁纸"),
 ("BV1Tm4y1b7Hq", "★★", "内嵌中英双语字幕", "Earth At Night 夜色中的地球", "夜间红外摄影的技术革命之作,依荷社『中英双语学习』压制版,解说词优美"),
 ("BV1oDS1BUEUR", "★★", "4K原声", "冰冻星球2 第一集", "BBC 4K原盘画质(本清单画质天花板),现有『极地世界』主题的升级素材"),
 ("BV1V64y1H7LZ", "★", "英文原声", "七个世界,一个星球 精讲", "BBC神作的70分钟精讲合集,可作正片引导课(正片另有版权资源)"),
 ("BV1B7411v73M", "★", "中英双语字幕", "Crash Course 统计学", "大学级速成课(家长与大童向):Crash Course系列精校双语,家庭共学"),
]),
]

def fmt_dur(sec):
    h, m = sec // 3600, sec % 3600 // 60
    return f"{h}小时{m:02d}分" if h else f"{m}分钟"

def fmt_view(v):
    return f"{v/10000:.0f}万" if v >= 10000 else str(v)

lines = []
lines.append("# 优质双语英语学习视频精选(2026-10 增补版)\n")
lines.append("> **定位**:对现有 1041 部片单(`docs/365-english-kids-videos.md`)的**提质增优**补充清单。\n")
lines.append("> 针对痛点:①画质参差 → 全部核验最高画质档;②缺双语字幕 → 优先内嵌双语/官方双语/CC双语;③内容一般 → 只收教育口碑与趣味性兼具的系列。\n")
lines.append("> **数据可信度**:46 部候选全部于 2026-10-30 经 B站官方接口逐一验证(BV 有效性、最高画质、分P数、时长、播放量),精选 43 部收录、9 项替换建议,脚本见 `scripts/research-bilibili.mjs`。\n")
lines.append("\n## 一、字幕方案(三档,App 均已支持)\n")
lines.append("| 形态 | 说明 | 适用 |\n|---|---|---|")
lines.append("| ★内嵌双语硬字幕 | 画面自带中英字幕(官方双语/UP压制),任何账号都显示 | 最省心,优先推荐 |")
lines.append("| ★双音轨/真双语 | 可切中英音轨,或『先中后英』真双语轨 | 零基础过渡期 |")
lines.append("| AI 双语(CC/Vosk) | App 登录后自动拉取 B站 `ai-en`/`ai-zh` CC 字幕;无CC时 Vosk 实时识别英文 | 大部分未标注视频 |")
lines.append("\n## 二、宝藏频道(持续产出双语学习资源)\n")
lines.append("| UP主 | 特色 | 代表作品 |\n|---|---|---|")
lines.append("| 小猫咪字幕组 | Netflix 官方双语字幕剧集 | Who Was 秀 / 妙想科学秀 / 72 危险动物 |")
lines.append("| 纸鸢卡通 | 全季 1080P 中英双字幕 | 字母积木六季全 / SSS 20周跟练 |")
lines.append("| 依荷社 | 纪录片中英双语学习版 | 夜色中的地球 / Pocoyo |")
lines.append("| 海上小甜橙 | BBC/PBS 科普合集 | Do You Know / JoJo & Gran Gran / Xavier |")
lines.append("| YouTube知识宝藏区 | 大而全的科普合集 | AumSum / Biff and Chip |")
lines.append("| 外语学习资源分享 | 双语字幕儿童剧 | 少年侦探所 |")
lines.append("| 小初高学堂 | 全季合集型 | Wow English / 数字积木 |")
lines.append("| 英语鼠鼠 | 现象级启蒙动画 | 动物王国大冒险 |")

for title, desc, items in CATS:
    lines.append(f"\n## 三-{title}\n")
    lines.append(f"> {desc}\n")
    lines.append("| 资源 | 星级 | 画质 | 集数/时长 | 字幕 | 播放量 | 推荐理由 |")
    lines.append("|---|---|---|---|---|---|---|")
    for bv, star, sub, name, why in items:
        d = data[bv]
        q = QN.get(d['maxq'], (str(d['maxq']), ''))[0] + QN.get(d['maxq'], ('', ''))[1]
        link = f"[{name}]({'https://www.bilibili.com/video/' + bv})"
        lines.append(f"| {link} `{bv}` | {star} | {q} | P{d['pages']} · {fmt_dur(d['dur'])} | {sub} | {fmt_view(d['view'])} | {why} |")

lines.append("\n## 四、现有片单画质增强替换建议\n")
lines.append("| 现有资源 | 现状 | 建议替换为 | 收益 |\n|---|---|---|---|")
lines.append("| Alphablocks 字母积木(BV1QPhi6AESB 等) | 720P 英字 | [BV1Jtn46aEPK](https://www.bilibili.com/video/BV1Jtn46aEPK) 六季全119P·1080P·中英双字幕 | 画质↑ 音轨全 双语字幕 |")
lines.append("| 同上(纯英文环境) | — | [BV1hAn46mEeD](https://www.bilibili.com/video/BV1hAn46mEeD) 六季全·1080P·英文字幕 | 考听力时用 |")
lines.append("| Numberblocks 数字积木(720P) | 720P | [BV1QEnd6FEuj](https://www.bilibili.com/video/BV1QEnd6FEuj) 1-9季180P·1080P·中英字幕 | 集全+高清+双语 |")
lines.append("| SSS 儿歌(分散) | 分散单集 | [BV16cudzaEeQ](https://www.bilibili.com/video/BV16cudzaEeQ) 426首·1080P | 播放连贯性↑ |")
lines.append("| SSS 儿歌(跟练版) | — | [BV1ztn46hEAH](https://www.bilibili.com/video/BV1ztn46hEAH) 20周跟练·中英双字幕 | 边唱边学 |")
lines.append("| Hey Duggee 道奇(480P) | 480P | [BV1Te4y1c7sA](https://www.bilibili.com/video/BV1Te4y1c7sA) 1080P | 画质大升级 |")
lines.append("| Bluey 布鲁伊(单集分散) | 单集 | [BV18T3G6jEVM](https://www.bilibili.com/video/BV18T3G6jEVM) S1全52集·1080P·真双语 | 集全+真双语 |")
lines.append("| Yakka Dee(仅1条) | 单条 | [BV1rS411A7eW](https://www.bilibili.com/video/BV1rS411A7eW) 12段合集 | 内容量↑ |")
lines.append("| TED-Ed(单集分散) | 单集 | [BV1Gf4y1y7wc](https://www.bilibili.com/video/BV1Gf4y1y7wc) 约1000集·中英CC | 集全+CC双语 |")

lines.append("\n## 五、落地步骤\n")
lines.append("1. 将选中的条目按现有格式(`id/t/kw/ep/min/tag/bvid`)追加到 `server/config.default.json` 对应级别分类;合集类建议以分P为单元拆条(`ep` 对应 P 序号)。")
lines.append("2. 运行 `node scripts/gen-md.js` 重新生成文档;新增条目无需再解析 BV(已验证)。")
lines.append("3. 按 `plan/TEST-PLAN.md` 流程在模拟器实测:双语字幕显示(内嵌/CC 两种形态)、清晰度档位(起播 toast)、断播降级。")
lines.append("4. 上传 `server/config.json`,App 端自动热更片单。\n")
lines.append("\n---\n")
lines.append(f"*生成于 {datetime.date.today().isoformat()} · 数据源:B站开放接口实抓 · 验证脚本 `scripts/research-bilibili.mjs` / `scripts/gen-premium-md.py`*")

out = '\n'.join(lines) + '\n'
path = ROOT + '/docs/premium-bilingual-catalog.md'
open(path, 'w', encoding='utf-8').write(out)
print('WROTE', path, len(out), 'chars,', sum(len(x[2]) for x in CATS), 'entries')
