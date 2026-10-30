# -*- coding: utf-8 -*-
"""生成测试计划所需的异常/边界配置文件（plan/TEST-PLAN.md §4.3）。"""
import json, os, shutil

HERE = os.path.dirname(os.path.abspath(__file__))
SERVER = os.path.dirname(HERE)

real = json.load(open(os.path.join(SERVER, "config.json"), encoding="utf-8"))
d001 = next(i for c in real["categories"] for i in c["items"] if i["id"] == "d001")
COVER = d001["cover"]  # 有效封面URL（来自真实片单d001）

def item(id_, t, **kw):
    d = {"id": id_, "t": t}
    d.update(kw)
    return d

# ---- 1. 标准测试配置 v100：含好BV/仅kw/坏BV/ep越界/短片连播 ----
normal = {
    "version": 100,
    "updated_at": "2026-10-30",
    "server": {"name": "骁骁TV测试服", "notice": "【测试公告】normal v100 生效"},
    "play": {"autoplay_next": True},
    "categories": [
        {"id": "t1", "title": "L1 测试A", "level": 1, "items": [
            item("ts01", "Muddy Puddles 踩泥坑", bvid="BV1vfdrBJEpP", ep=1, min=5,
                 tag="英语启蒙", lv=1, cover=COVER),
            item("ts02", "无BV号条目(仅搜索词)", min=5, tag="英语启蒙", lv=1,
                 kw="小猪佩奇 英文版 第1季 Muddy Puddles"),
            item("ts03", "已失效BV条目", bvid="BV1ZZtestZZ99", min=5, tag="英语启蒙", lv=1,
                 kw="Peppa Pig Muddy Puddles"),
            item("ts06", "搜索词无结果条目", min=1, tag="测试", lv=1,
                 kw="zzzqqqxx不存在测试2026"),
        ]},
        {"id": "t3", "title": "L1 短片连播", "level": 1, "items": [
            item("ts07", "51秒儿歌一号", bvid="BV1Zg411t7ZQ", ep=1, min=1, tag="儿歌", lv=1),
            item("ts08", "Go Away 儿歌", bvid="BV1XA4y1D7h8", ep=1, min=2, tag="儿歌", lv=1),
        ]},
        {"id": "t2", "title": "L2 测试B", "level": 2, "items": [
            item("ts04", "选集越界ep99", bvid="BV1vfdrBJEpP", ep=99, min=5, tag="英语启蒙", lv=2),
            item("ts05", "51秒儿歌(连播用)", bvid="BV1Zg411t7ZQ", ep=1, min=1, tag="儿歌", lv=2),
        ]},
    ],
}

# ---- 2. 版本回退 v99（低于缓存，应被拒） ----
rollover = {
    "version": 99, "updated_at": "2026-10-30",
    "server": {"name": "骁骁TV测试服", "notice": "【测试公告】rollback v99 不应生效"},
    "categories": [
        {"id": "r1", "title": "回退分类", "level": 1, "items": [
            item("rs01", "回退条目", bvid="BV1Zg411t7ZQ", ep=1, lv=1),
        ]},
    ],
}

# ---- 3. 同版本 v100 内容更新（version相等，应被接受） ----
equal = json.loads(json.dumps(normal))
equal["server"]["notice"] = "【测试公告】equal v100 同版本更新生效"

# ---- 4. 非法JSON ----
with open(os.path.join(HERE, "config.badjson.json"), "w", encoding="utf-8") as f:
    f.write('{"version": 101, "server": {"notice": "半截JSON"')

# ---- 5. 空片单 ----
empty = {"version": 102, "categories": [{"id": "e1", "title": "空分类", "items": []}]}

# ---- 6. 关闭连播 v110：只有51秒短片 ----
noautoplay = {
    "version": 110, "updated_at": "2026-10-30",
    "server": {"name": "骁骁TV测试服", "notice": "【测试公告】noautoplay v110"},
    "play": {"autoplay_next": False},
    "categories": [
        {"id": "n1", "title": "连播关闭", "level": 1, "items": [
            item("ns01", "51秒儿歌(连播关闭)", bvid="BV1Zg411t7ZQ", ep=1, min=1, lv=1),
        ]},
    ],
}

for name, obj in [("config.normal.json", normal), ("config.rollover.json", rollover),
                  ("config.equal.json", equal), ("config.empty.json", empty),
                  ("config.noautoplay.json", noautoplay)]:
    with open(os.path.join(HERE, name), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)

# ---- 7. v1旧格式（直接复制备份） ----
shutil.copy(os.path.join(SERVER, "config.v1.backup.json"),
            os.path.join(HERE, "config.v1compat.json"))

print("generated:", sorted(os.listdir(HERE)))
