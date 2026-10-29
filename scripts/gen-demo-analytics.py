#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
生成演示用的历史交易数据（仅用于本地演示与答辩展示）。

生成内容：
  1. 给 seller(u_seller) 补充 7 个商品，使卖家端图表有足够样本
  2. 近 90 天约 300 笔订单（含跨品类、多状态、周末与大促峰值）
  3. 配套 browse_history（浏览量约为订单 15 倍）与 cart_item，供转化漏斗计算

安全设计：
  - 只影响 u_demo（买家）与 u_seller（卖家），其他账号数据不动
  - 幂等：脚本开头会清理上次生成的模拟数据（按 remark 标记识别）
  - 明确标注 remark = '[DEMO]' 便于识别与清理

用法：python scripts/gen-demo-analytics.py
"""
import random
import subprocess
import uuid
from datetime import datetime, timedelta
from decimal import Decimal

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "smart_mall"
DEMO_TAG = "[DEMO]"

random.seed(20261029)

BUYER_ID = "u_demo"
SELLER_ID = "u_seller"


SQL_FILE = ".cache/tmp/_seed.sql"


def run_sql(sql):
    """把 SQL 写入临时文件后执行，避免 Windows 命令行长度限制（WinError 206）。"""
    import os
    os.makedirs(os.path.dirname(SQL_FILE), exist_ok=True)
    # 二进制写入：Windows 文本模式会把换行转成 CRLF，
    # 导致 VARCHAR 末尾混入 0x0D，后续 WHERE 精确匹配全部失效。
    data = sql.replace(chr(13), "")
    with open(SQL_FILE, "wb") as f:
        f.write(data.encode("utf-8"))
    p = subprocess.run(
        [MYSQL, "-uroot", "-p123456", "-N", "-B", "--default-character-set=utf8mb4", DB],
        stdin=open(SQL_FILE, "rb"), capture_output=True,
    )
    if p.returncode != 0:
        err = p.stderr.decode("utf-8", "replace")
        if "Warning" not in err:
            raise RuntimeError(err[:400])
    return p.stdout.decode("utf-8", "replace")


def esc(s):
    return s.replace("\\", "\\\\").replace("'", "''")


def nid():
    return uuid.uuid4().hex[:32]


# ---------------------------------------------------------------- 清理旧模拟数据
def cleanup():
    run_sql(f"""
        DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE remark = '{DEMO_TAG}');
        DELETE FROM orders WHERE remark = '{DEMO_TAG}';
        DELETE FROM browse_history WHERE user_id IN ('{BUYER_ID}');
        DELETE FROM cart_item WHERE user_id = '{BUYER_ID}';
        DELETE FROM product_review WHERE product_id LIKE 'p_seller_%' AND username LIKE '体验用户%';
        DELETE FROM product WHERE seller_id = '{SELLER_ID}' AND id LIKE 'p_seller_%';
    """)
    print("  · 已清理旧的模拟数据")


# ---------------------------------------------------------------- 补卖家商品
SELLER_NEW_PRODUCTS = [
    ("p_seller_02", "星野 轻量通勤耳机 Lite", "半入耳 · 28h 续航 · 蓝牙 5.3",
     "cat_headphone", 199, 259, 120, "/images/catalog/headphone/3.jpg"),
    ("p_seller_03", "星野 骨传导运动耳机 S1", "不入耳 · IP67 · 10h 续航",
     "cat_headphone", 459, 559, 60, "/images/catalog/headphone/5.jpg"),
    ("p_seller_04", "星野 桌面收纳托盘", "胡桃木 · 防滑垫 · 32cm",
     "cat_backpack", 89, 119, 200, "/images/catalog/backpack/2.jpg"),
    ("p_seller_05", "星野 便携咖啡随行杯", "316 不锈钢 · 350ml · 保温 12h",
     "cat_coffee", 129, 169, 150, "/images/catalog/coffee/4.jpg"),
    ("p_seller_06", "星野 瑜伽伸展带", "天然棉麻 · 2.5m · 附带收纳袋",
     "cat_yoga", 39, 59, 300, "/images/catalog/yoga/6.jpg"),
    ("p_seller_07", "星野 降噪耳塞套装", "三层隔音 · 可水洗 · 3 组尺寸",
     "cat_headphone", 69, 89, 400, "/images/catalog/headphone/7.jpg"),
    ("p_seller_08", "星野 极简机械键盘 68 键", "青轴 · 白光 · 三模连接",
     "cat_keyboard", 329, 399, 80, "/images/catalog/keyboard/2.jpg"),
]


def seed_seller_products():
    rows = []
    for i, (pid, name, subtitle, cat, price, orig, stock, img) in enumerate(SELLER_NEW_PRODUCTS):
        rows.append(
            f"('{pid}', '{esc(name)}', '{esc(subtitle)}', '{cat}', '星野', {price}, {orig}, "
            f"{stock}, {random.randint(20, 260)}, 4.{random.randint(2, 9)}, {random.randint(8, 60)}, "
            f"'{esc('星野自营商品，' + subtitle)}', '{{\"续航\":\"28h\"}}', '降噪,长续航', "
            f"'{img}', '{img}', '{SELLER_ID}', 1, NOW() - INTERVAL {random.randint(20, 88)} DAY)"
        )
    run_sql(f"""
        INSERT INTO product
          (id, name, subtitle, category_id, brand, price, original_price, stock, sales,
           rating, review_count, description, attributes, tags, image, image_fallback,
           seller_id, status, created_at)
        VALUES {','.join(rows)};
    """)
    print(f"  · 已为卖家补 {len(rows)} 个商品")


# ---------------------------------------------------------------- 订单生成
def load_products():
    """读取所有在用商品，用于加权抽样。返回 [(id, name, cat, price, rating, seller_id)]"""
    out = run_sql("""
        SELECT id, name, category_id, price, IFNULL(rating,4.5), IFNULL(seller_id,'')
        FROM product WHERE status = 1;
    """)
    rows = []
    for line in out.strip().split("\n"):
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 6:
            continue
        rows.append((parts[0], parts[1], parts[2], Decimal(parts[3]), float(parts[4]), parts[5]))
    return rows


def weighted_pick(products, k):
    """按评分加权抽样，模拟'爆款更常被买'的真实分布"""
    # 评分越高越常被买；价格越高购买频次越低（真实消费分布的长尾特征）
    weights = [(max(0.5, p[4]) ** 3) / (1 + float(p[3]) / 120) for p in products]
    picked, pool, w = [], list(products), list(weights)
    for _ in range(min(k, len(pool))):
        choice = random.choices(range(len(pool)), weights=w, k=1)[0]
        picked.append(pool.pop(choice))
        w.pop(choice)
    return picked


def biz_day_offset():
    """近 90 天内的一天偏移量。
    周末与大促日概率更高；近 7 天额外加权，
    避免趋势图最右侧出现连续 0 的断崖（真实经营也很少完全没单）。
    """
    # 25% 的订单集中在最近 7 天，保证趋势图右端有内容
    if random.random() < 0.25:
        day = random.randint(1, 7)
    else:
        day = random.randint(1, 90)
    d = datetime.now() - timedelta(days=day)
    boost = 1.0
    if d.weekday() >= 5:            # 周末
        boost = 1.8
    if d.day in (15, 16, 30, 1):    # 月中/月末大促
        boost = 2.2
    return day, boost


def gen_orders(products, cart_pids=None, target=300):
    """生成订单。优先从已加购商品中下单，符合真实转化路径，
    也能保证漏斗「加购 > 下单」单调递减。"""
    order_rows, item_rows = [], []
    # 买家订单 + 卖家订单混合，让两端都有数据
    seller_products = [p for p in products if p[5] == SELLER_ID]
    # 已加购的商品池
    cart_set = set(cart_pids or [])
    cart_products = [p for p in products if p[0] in cart_set]

    status_pool = (
        ["COMPLETED"] * 70 + ["SHIPPED"] * 10 + ["PAID"] * 8 +
        ["CANCELLED"] * 7 + ["PENDING_PAYMENT"] * 5
    )

    made = 0
    attempts = 0
    while made < target and attempts < target * 6:
        attempts += 1
        day, boost = biz_day_offset()
        if random.random() > boost / 2.4:
            continue

        # 75% 的订单来自「已加购」商品（真实转化路径）
        if cart_products and random.random() < 0.75:
            picks = weighted_pick(cart_products, random.choice([1, 1, 2]))
        elif seller_products and random.random() < 0.35:
            picks = weighted_pick(seller_products, random.choice([1, 1, 2]))
        else:
            picks = weighted_pick(products, random.choice([1, 1, 1, 2, 3]))

        # 金额长尾：低价商品更常被买（按价格反比加权已由 weighted_pick 近似）
        created = datetime.now() - timedelta(
            days=day,
            hours=random.randint(0, 23),
            minutes=random.randint(0, 59),
        )
        status = random.choice(status_pool)

        oid = nid()
        # 订单号：SG + 日期 + 6 位随机
        order_no = "SG" + created.strftime("%Y%m%d") + str(random.randint(100000, 999999))

        total = Decimal("0")
        for (pid, pname, cat, price, rating, seller) in picks:
            qty = random.choices([1, 1, 1, 2, 3], k=1)[0]
            amount = price * qty
            total += amount
            seller_val = f"'{seller}'" if seller else "NULL"
            ship = "SHIPPED" if status in ("SHIPPED", "COMPLETED") else "PENDING"
            item_rows.append(
                f"('{oid}', '{pid}', '{esc(pname)}', NULL, {price}, {qty}, {seller_val}, "
                f"'{ship}', {'1' if ship == 'SHIPPED' else '0'})"
            )

        paid_at = "NULL"
        shipped_at = "NULL"
        finished_at = "NULL"
        if status in ("PAID", "SHIPPED", "COMPLETED"):
            paid_at = f"'{created + timedelta(minutes=random.randint(1, 30)):%Y-%m-%d %H:%M:%S}'"
        if status in ("SHIPPED", "COMPLETED"):
            shipped_at = f"'{created + timedelta(days=random.randint(0, 2)):%Y-%m-%d %H:%M:%S}'"
        if status == "COMPLETED":
            finished_at = f"'{created + timedelta(days=random.randint(3, 8)):%Y-%m-%d %H:%M:%S}'"

        order_rows.append(
            f"('{oid}', '{order_no}', '{BUYER_ID}', {total}, '{status}', 'addr_demo', "
            f"'体验用户', '13800138000', '江苏省南京市栖霞区文苑路 1 号 智能导购实验室', "
            f"'{DEMO_TAG}', {paid_at}, {shipped_at}, {finished_at}, "
            f"'{created:%Y-%m-%d %H:%M:%S}')"
        )
        made += 1

    # 批量插入
    BATCH = 200
    for i in range(0, len(order_rows), BATCH):
        run_sql(f"""
            INSERT INTO orders
              (id, order_no, user_id, total_amount, status, address_id, receiver, phone,
               address_text, remark, paid_at, shipped_at, finished_at, created_at)
            VALUES {','.join(order_rows[i:i+BATCH])};
        """)
    for i in range(0, len(item_rows), BATCH):
        run_sql(f"""
            INSERT INTO order_item
              (order_id, product_id, product_name, product_image, product_price,
               quantity, seller_id, ship_status, refund_status)
            VALUES {','.join(item_rows[i:i+BATCH])};
        """)
    print(f"  · 已生成 {len(order_rows)} 笔订单、{len(item_rows)} 条订单明细")
    return order_rows


# ---------------------------------------------------------------- 浏览/加购
def gen_behaviors(products, order_count):
    """浏览量约为订单的 15 倍，用于算转化漏斗"""
    n = order_count * 15
    browse_rows = []
    for _ in range(n):
        pid, pname, cat, price, rating, seller = random.choice(products)
        days_ago = random.randint(1, 90)
        browse_rows.append(
            f"('{BUYER_ID}', '{pid}', NOW() - INTERVAL {days_ago} DAY - INTERVAL {random.randint(0,86399)} SECOND)"
        )
    BATCH = 300
    for i in range(0, len(browse_rows), BATCH):
        run_sql(f"INSERT INTO browse_history (user_id, product_id, created_at) VALUES {','.join(browse_rows[i:i+BATCH])};")
    print(f"  · 已生成 {len(browse_rows)} 条浏览记录")

    # 加购：cart_item 对 (user_id, product_id) 有唯一约束，
    # 每个用户每种商品只能有一条记录。为了让卖家侧漏斗的「加购」层
    # 不至于过小（卖家商品只有 8 个），这里优先把卖家商品全部加入购物车，
    # 再用平台商品补足，quantity 取 1-3 模拟真实加购数量。
    seller_only = [p for p in products if p[5] == SELLER_ID]
    others = [p for p in products if p[5] != SELLER_ID]
    cart_target = min(max(60, order_count * 2), len(products) - 1)

    picked = list(seller_only)                       # 先把卖家商品全部覆盖
    remain = max(0, cart_target - len(picked))
    if remain and others:
        picked += random.sample(others, min(remain, len(others)))

    cart_rows = []
    for pid, *_ in picked:
        days_ago = random.randint(1, 90)
        cart_rows.append(
            f"('{BUYER_ID}', '{pid}', {random.choice([1,1,2,3])}, 1, "
            f"NOW() - INTERVAL {days_ago} DAY, NOW() - INTERVAL {days_ago} DAY)"
        )

    for i in range(0, len(cart_rows), BATCH):
        run_sql(f"INSERT IGNORE INTO cart_item (user_id, product_id, quantity, selected, created_at, updated_at) VALUES {','.join(cart_rows[i:i+BATCH])};")
    print(f"  · 已生成 {len(cart_rows)} 条加购记录")
    return [pid for pid, *_ in picked]


GOOD_REVIEWS = [
    "音质比想象中好，降噪效果明显，性价比很高",
    "续航很扎实，通勤一天不用充电，推荐",
    "佩戴舒服，跑步不脱落，防水也没问题",
    "做工不错，质量满意，物流也快",
    "整体好用，喜欢这个设计，会回购",
]
MID_REVIEWS = [
    "整体一般，没有特别惊艳的地方",
    "功能正常，但续航比标称略短",
    "价格偏贵，同类里不算划算",
]
BAD_REVIEWS = [
    "有点失望，降噪效果不明显",
    "用了两天出现问题，准备退货",
    "噪音比较大，体验不好",
]


def seed_seller_reviews():
    """给卖家商品生成评价，否则卖家端评分分布为空。"""
    out = run_sql(f"SELECT id FROM product WHERE seller_id='{SELLER_ID}' AND id LIKE 'p_seller_%';")
    pids = [l.strip() for l in out.strip().splitlines() if l.strip()]
    rows = []
    for pid in pids:
        for _ in range(random.randint(12, 40)):
            star = random.choices([5, 4, 3, 2, 1], weights=[50, 25, 13, 7, 5], k=1)[0]
            if star >= 4:
                content = random.choice(GOOD_REVIEWS)
            elif star == 3:
                content = random.choice(MID_REVIEWS)
            else:
                content = random.choice(BAD_REVIEWS)
            days = random.randint(1, 88)
            rows.append(
                f"('{pid}', '{BUYER_ID}', '{esc('体验用户' + str(random.randint(1, 99)))}', "
                f"{star}, '{esc(content)}', 1, NOW() - INTERVAL {days} DAY)"
            )
    BATCH = 300
    for i in range(0, len(rows), BATCH):
        run_sql(f"INSERT INTO product_review (product_id, user_id, username, rating, content, status, created_at) "
                f"VALUES {','.join(rows[i:i+BATCH])};")
    print(f"  · 已为卖家商品生成 {len(rows)} 条评价")


def main():
    print("开始生成演示分析数据…")
    cleanup()
    seed_seller_products()
    seed_seller_reviews()
    products = load_products()
    print(f"  · 可用商品 {len(products)} 个")
    # 先生成浏览与加购，再基于购物车生成订单：
    # 这样订单大多来自已加购商品，漏斗天然单调递减
    cart_pids = gen_behaviors(products, 300)
    gen_orders(products, cart_pids, target=300)

    # 汇总校验
    out = run_sql(f"""
        SELECT
          (SELECT COUNT(*) FROM orders WHERE remark='{DEMO_TAG}'),
          (SELECT COUNT(*) FROM order_item),
          (SELECT COUNT(*) FROM browse_history WHERE user_id='{BUYER_ID}'),
          (SELECT COUNT(*) FROM cart_item WHERE user_id='{BUYER_ID}'),
          (SELECT COUNT(*) FROM product WHERE seller_id='{SELLER_ID}');
    """)
    cnt = out.strip().split("\t")
    print("\n生成完成：")
    print(f"  订单 {cnt[0]} 笔 / 明细 {cnt[1]} 条 / 浏览 {cnt[2]} 条 / 加购 {cnt[3]} 条 / 卖家商品 {cnt[4]} 个")


if __name__ == "__main__":
    main()