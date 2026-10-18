/**
 * 生成商品种子数据（确定性，可重复执行）
 * 用法: node scripts/generate-catalog.mjs
 * 输出: backend/src/main/resources/seed/catalog.json
 */
import fs from 'node:fs';
import path from 'node:path';

let seed = 20260418;
const rnd = () => {
  seed = (seed * 1103515245 + 12345) % 2147483648;
  return seed / 2147483648;
};
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const between = (min, max) => min + Math.floor(rnd() * (max - min + 1));
const money = (value) => Math.round(value / 10) * 10 - 1;

const roots = [
  ['cat_digital', '数码影音', '🎧', '耳机、手机与智能穿戴'],
  ['cat_computer', '电脑办公', '💻', '笔记本与外设'],
  ['cat_sport', '运动户外', '🏃', '跑步与瑜伽装备'],
  ['cat_beauty', '美妆个护', '✨', '护肤与个护'],
  ['cat_home', '家用电器', '🏠', '咖啡与空气健康'],
  ['cat_food', '食品饮料', '☕', '精品咖啡'],
  ['cat_bag', '箱包服饰', '🎒', '通勤与出行'],
  ['cat_smarthome', '智能家居', '🤖', '清洁与自动化']
];

const num = (key, unit, higherBetter = true, weight = 1) => ({ key, unit, type: 'number', higherBetter, weight });
const enums = (key, options, higherBetter = true, weight = 1) => ({ key, type: 'enum', options, higherBetter, weight });

const categories = roots.map(([id, name, icon, description], index) => ({
  id, parentId: null, name, icon, description, sortOrder: index + 1, attributeSchema: null
}));

const leaves = [
  {
    id: 'cat_headphone', parentId: 'cat_digital', name: '蓝牙耳机', icon: '🎧', image: 'headphone',
    desc: '通勤、运动、降噪全场景',
    schema: [num('续航', 'h'), enums('防水等级', ['IPX4', 'IPX5', 'IPX6', 'IPX7']), num('重量', 'g', false, 0.8), enums('降噪', ['无', '通话降噪', '主动降噪'], true, 1.2), enums('蓝牙版本', ['5.2', '5.3', '5.4'], true, 0.5)],
    brands: ['声屿', '澎湃', '云听', '律动'],
    series: ['Air', 'Liberty', 'Beat', 'Wave', 'Pods'],
    suffixes: ['主动降噪蓝牙耳机', '运动防水蓝牙耳机', '开放式蓝牙耳机'],
    count: 10,
    build(i) {
      const battery = [18, 24, 30, 36, 40, 48][i % 6];
      const waterproof = ['IPX5', 'IPX6', 'IPX4', 'IPX5', 'IPX7', 'IPX5', 'IPX4', 'IPX6', 'IPX5', 'IPX7'][i];
      const weight = [3.8, 4.2, 4.6, 5.2, 6.0][i % 5];
      const anc = waterproof === 'IPX4' ? '通话降噪' : (i % 3 === 0 ? '主动降噪' : '通话降噪');
      const bt = ['5.3', '5.4', '5.2'][i % 3];
      const price = [199, 279, 299, 349, 399, 459, 529, 599, 699, 899][i];
      return {
        attributes: { 续航: battery, 防水等级: waterproof, 重量: weight, 降噪: anc, 蓝牙版本: bt },
        price,
        name: `${pick(this.brands)} ${pick(this.series)} ${['3', '4', '5', 'Pro', 'Max'][i % 5]} ${waterproof === 'IPX4' ? '轻盈版' : '运动版'}`,
        subtitle: `${anc} · ${battery}h 续航 · ${waterproof} 防水`,
        tags: ['蓝牙耳机', '运动', anc.includes('主动') ? '降噪' : '通勤', '防水', '跑步']
      };
    }
  },
  {
    id: 'cat_phone', parentId: 'cat_digital', name: '智能手机', icon: '📱', image: 'phone',
    desc: '影像、续航与性能兼顾',
    schema: [num('屏幕', '英寸'), num('电池', 'mAh'), num('存储', 'GB'), num('像素', '万'), num('重量', 'g', false, 0.6)],
    brands: ['星野', '极光', '云舟', '青柚'],
    series: ['X', 'Note', 'Neo', 'Pro'],
    count: 10,
    build(i) {
      const screen = [6.1, 6.5, 6.7, 6.8][i % 4];
      const battery = [4500, 5000, 5500, 6000][i % 4];
      const storage = [128, 256, 512][i % 3];
      const pixel = [5000, 6400, 10800][i % 3];
      const weight = [178, 189, 199, 210][i % 4];
      return {
        attributes: { 屏幕: screen, 电池: battery, 存储: storage, 像素: pixel, 重量: weight },
        price: [1299, 1599, 1999, 2499, 2999, 3499, 3999, 4499, 5299, 5999][i],
        name: `${pick(this.brands)} ${pick(this.series)}${i + 1} ${storage}GB`,
        subtitle: `${screen} 英寸 · ${pixel} 万像素 · ${battery}mAh`,
        tags: ['智能手机', '5G', '拍照', storage >= 256 ? '大存储' : '日常']
      };
    }
  },
  {
    id: 'cat_watch', parentId: 'cat_digital', name: '智能手表', icon: '⌚', image: 'watch',
    desc: '健康监测与运动记录',
    schema: [num('续航', '天'), num('防水', 'ATM'), num('屏幕', '英寸'), num('重量', 'g', false, 0.7)],
    brands: ['星野', '云听', '律动'],
    series: ['Watch', 'Fit', 'Active'],
    count: 10,
    build(i) {
      const battery = [7, 14, 21][i % 3];
      const water = [5, 10][i % 2];
      const screen = [1.43, 1.5, 1.78][i % 3];
      const weight = [32, 38, 45][i % 3];
      return {
        attributes: { 续航: battery, 防水: water, 屏幕: screen, 重量: weight },
        price: [399, 499, 599, 799, 899, 1099, 1299, 1499, 1799, 1999][i],
        name: `${pick(this.brands)} ${pick(this.series)} ${i + 1} 代`,
        subtitle: `${battery} 天续航 · ${water}ATM 防水 · 血氧监测`,
        tags: ['智能手表', '运动', '健康监测', '防水']
      };
    }
  },
  {
    id: 'cat_laptop', parentId: 'cat_computer', name: '笔记本电脑', icon: '💻', image: 'laptop',
    desc: '轻薄本与性能本',
    schema: [num('屏幕', '英寸'), num('内存', 'GB'), num('硬盘', 'GB'), num('重量', 'kg', false, 1.0), num('续航', 'h')],
    brands: ['云舟', '极光', '磐石'],
    series: ['Air', 'Book', 'Studio'],
    count: 10,
    build(i) {
      const screen = [13.3, 14, 15.6, 16][i % 4];
      const memory = i % 2 === 0 ? 16 : 32;
      const disk = i % 3 === 0 ? 1024 : 512;
      const weight = [1.2, 1.4, 1.8, 2.1][i % 4];
      const battery = [8, 10, 12, 15][i % 4];
      return {
        attributes: { 屏幕: screen, 内存: memory, 硬盘: disk, 重量: weight, 续航: battery },
        price: [3999, 4499, 4999, 5499, 6299, 6999, 7599, 8299, 8999, 9999][i],
        name: `${pick(this.brands)} ${pick(this.series)} ${screen} 英寸`,
        subtitle: `${memory}GB 内存 · ${disk}GB 固态 · ${weight}kg`,
        tags: ['笔记本电脑', '轻薄本', screen >= 15.6 ? '大屏' : '便携', '办公']
      };
    }
  },
  {
    id: 'cat_keyboard', parentId: 'cat_computer', name: '机械键盘', icon: '⌨️', image: 'keyboard',
    desc: '办公与游戏的输入体验',
    schema: [num('键数', '键'), enums('连接方式', ['有线', '蓝牙', '三模']), enums('轴体', ['红轴', '茶轴', '青轴', '静音红轴']), num('重量', 'g', false, 0.6)],
    brands: ['磐石', '云舟', '律动'],
    series: ['K', 'Pro', 'Lite'],
    count: 10,
    build(i) {
      const keys = [68, 87, 104][i % 3];
      const conn = ['三模', '有线', '蓝牙'][i % 3];
      const axis = ['红轴', '茶轴', '青轴', '静音红轴'][i % 4];
      const weight = [520, 680, 820, 950][i % 4];
      return {
        attributes: { 键数: keys, 连接方式: conn, 轴体: axis, 重量: weight },
        price: [199, 249, 299, 359, 429, 499, 599, 699, 799, 899][i],
        name: `${pick(this.brands)} ${pick(this.series)}${keys} ${axis}`,
        subtitle: `${keys} 键 · ${conn}连接 · ${axis}`,
        tags: ['机械键盘', '办公', conn === '三模' ? '无线' : '有线', axis]
      };
    }
  },
  {
    id: 'cat_running', parentId: 'cat_sport', name: '跑步鞋', icon: '👟', image: 'shoes',
    desc: '缓震、轻量与透气',
    schema: [num('缓震', '级'), num('重量', 'g', false, 1.0), num('透气性', '级'), enums('适合路面', ['公路', '跑道', '越野', '全地形'])],
    brands: ['步履', '行野', '风驰'],
    series: ['疾风', '云履', '轻羽'],
    count: 10,
    build(i) {
      const cushion = [3, 4, 5][i % 3];
      const weight = [210, 235, 260, 285][i % 4];
      const breath = [3, 4, 5][i % 3];
      const road = ['公路', '跑道', '全地形', '越野'][i % 4];
      return {
        attributes: { 缓震: cushion, 重量: weight, 透气性: breath, 适合路面: road },
        price: [299, 369, 429, 499, 569, 649, 729, 829, 949, 1099][i],
        name: `${pick(this.brands)} ${pick(this.series)} ${i + 1} 代跑鞋`,
        subtitle: `${cushion} 级缓震 · ${weight}g · ${road}路面`,
        tags: ['跑步鞋', '运动', '缓震', '透气', road]
      };
    }
  },
  {
    id: 'cat_yoga', parentId: 'cat_sport', name: '瑜伽垫', icon: '🧘', image: 'yoga',
    desc: '防滑与支撑',
    schema: [num('厚度', 'mm'), num('长度', 'cm'), num('重量', 'g', false, 0.5), enums('材质', ['TPE', '天然橡胶', 'PU'])],
    brands: ['行野', '栖居', '云履'],
    series: ['静心', '大地', '轻行'],
    count: 10,
    build(i) {
      const thick = [5, 6, 8, 10][i % 4];
      const length = [183, 185, 200][i % 3];
      const weight = [800, 1000, 1200][i % 3];
      const material = ['TPE', '天然橡胶', 'PU'][i % 3];
      return {
        attributes: { 厚度: thick, 长度: length, 重量: weight, 材质: material },
        price: [69, 89, 109, 139, 169, 199, 239, 279, 329, 399][i],
        name: `${pick(this.brands)} ${pick(this.series)} ${thick}mm 瑜伽垫 ${String.fromCharCode(65 + i)}`,
        subtitle: `${thick}mm 加厚 · ${material} 材质 · 防滑纹理`,
        tags: ['瑜伽垫', '健身', '防滑', material]
      };
    }
  },
  {
    id: 'cat_skincare', parentId: 'cat_beauty', name: '护肤品', icon: '🧴', image: 'skincare',
    desc: '保湿、修护与防晒',
    schema: [num('容量', 'ml'), num('防晒指数', 'SPF'), enums('适合肤质', ['干性', '油性', '混合', '敏感']), enums('功效', ['保湿', '修护', '美白', '抗皱'])],
    brands: ['凝光', '青柚', '素研'],
    series: ['水光', '屏障', '清透'],
    count: 10,
    build(i) {
      const volume = [30, 50, 100][i % 3];
      const spf = [0, 30, 50][i % 3];
      const skin = ['干性', '油性', '混合', '敏感'][i % 4];
      const effect = ['保湿', '修护', '美白', '抗皱'][i % 4];
      return {
        attributes: { 容量: volume, 防晒指数: spf, 适合肤质: skin, 功效: effect },
        price: [89, 129, 169, 219, 269, 329, 399, 469, 579, 699][i],
        name: `${pick(this.brands)} ${pick(this.series)} ${effect}精华 ${volume}ml`,
        subtitle: `${skin}肌适用 · ${effect}${spf ? ` · SPF${spf}` : ''}`,
        tags: ['护肤品', effect, skin + '肌', spf ? '防晒' : '日常护理']
      };
    }
  },
  {
    id: 'cat_coffee', parentId: 'cat_home', name: '咖啡机', icon: '☕', image: 'coffee',
    desc: '在家喝到一杯好咖啡',
    schema: [num('容量', 'L'), num('功率', 'W', false, 0.5), num('压力', 'bar'), enums('清洗方式', ['手动', '可拆卸', '自动清洗'])],
    brands: ['岩咖', '栖居', '晨光'],
    series: ['Mini', 'Home', 'Pro'],
    count: 10,
    build(i) {
      const capacity = [0.6, 1.2, 1.8][i % 3];
      const power = [800, 1200, 1450][i % 3];
      const pressure = [15, 19, 20][i % 3];
      const clean = ['手动', '可拆卸', '自动清洗'][i % 3];
      return {
        attributes: { 容量: capacity, 功率: power, 压力: pressure, 清洗方式: clean },
        price: [499, 699, 899, 1199, 1499, 1799, 2199, 2599, 3199, 3999][i],
        name: `${pick(this.brands)} ${pick(this.series)} ${i + 1} 半自动咖啡机`,
        subtitle: `${pressure}bar 压力 · ${capacity}L 水箱 · ${clean}`,
        tags: ['咖啡机', '半自动', '家用', clean]
      };
    }
  },
  {
    id: 'cat_purifier', parentId: 'cat_home', name: '空气净化器', icon: '🌬️', image: 'purifier',
    desc: '除醛、除霾与静音',
    schema: [num('CADR', 'm³/h'), num('噪音', 'dB', false, 1.0), num('适用面积', '㎡'), num('功率', 'W', false, 0.4)],
    brands: ['栖居', '晨光', '磐石'],
    series: ['清风', '森呼吸', '净界'],
    count: 10,
    build(i) {
      const cadr = [300, 500, 700][i % 3];
      const noise = [28, 34, 40][i % 3];
      const area = [30, 50, 80][i % 3];
      const power = [35, 50, 65][i % 3];
      return {
        attributes: { CADR: cadr, 噪音: noise, 适用面积: area, 功率: power },
        price: [899, 1199, 1499, 1899, 2199, 2599, 2999, 3299, 3699, 3999][i],
        name: `${pick(this.brands)} ${pick(this.series)} ${i + 1} 空气净化器`,
        subtitle: `CADR ${cadr} · ${noise}dB 静音 · 适用 ${area}㎡`,
        tags: ['空气净化器', '除甲醛', '静音', '家用']
      };
    }
  },
  {
    id: 'cat_beans', parentId: 'cat_food', name: '咖啡豆', icon: '🫘', image: 'beans',
    desc: '产地与烘焙风味',
    schema: [num('规格', 'g'), enums('烘焙度', ['浅烘', '中烘', '深烘']), enums('产地', ['埃塞俄比亚', '哥伦比亚', '云南', '巴西']), enums('风味', ['花香果酸', '坚果可可', '焦糖甜感', '莓果'])],
    brands: ['岩咖', '晨光', '山间'],
    series: ['日晒', '水洗', '拼配'],
    count: 10,
    build(i) {
      const weight = [250, 500, 1000][i % 3];
      const roast = ['浅烘', '中烘', '深烘'][i % 3];
      const origin = ['埃塞俄比亚', '哥伦比亚', '云南', '巴西'][i % 4];
      const flavor = ['花香果酸', '坚果可可', '焦糖甜感', '莓果'][i % 4];
      return {
        attributes: { 规格: weight, 烘焙度: roast, 产地: origin, 风味: flavor },
        price: [59, 69, 79, 89, 99, 109, 129, 149, 169, 189][i],
        name: `${pick(this.brands)} ${origin} ${roast} 咖啡豆 ${weight}g`,
        subtitle: `${origin}产区 · ${roast} · ${flavor}`,
        tags: ['咖啡豆', roast, origin, flavor]
      };
    }
  },
  {
    id: 'cat_backpack', parentId: 'cat_bag', name: '双肩包', icon: '🎒', image: 'backpack',
    desc: '通勤、差旅与户外',
    schema: [num('容量', 'L'), num('重量', 'g', false, 0.8), enums('防水', ['否', '是']), enums('材质', ['尼龙', '聚酯纤维', '帆布'])],
    brands: ['行野', '拾光', '栖居'],
    series: ['通勤', '轻旅', '城市'],
    count: 10,
    build(i) {
      const capacity = [18, 24, 30][i % 3];
      const weight = [600, 800, 1000][i % 3];
      const waterproof = i % 2 === 0 ? '是' : '否';
      const material = ['尼龙', '聚酯纤维', '帆布'][i % 3];
      return {
        attributes: { 容量: capacity, 重量: weight, 防水: waterproof, 材质: material },
        price: [129, 169, 199, 239, 279, 329, 379, 429, 499, 599][i],
        name: `${pick(this.brands)} ${pick(this.series)} ${capacity}L 双肩包`,
        subtitle: `${capacity}L 容量 · ${material} · ${waterproof === '是' ? '防泼水' : '轻量'}`,
        tags: ['双肩包', '通勤', '电脑包', waterproof === '是' ? '防水' : '轻量']
      };
    }
  },
  {
    id: 'cat_robot', parentId: 'cat_smarthome', name: '扫地机器人', icon: '🤖', image: 'robot',
    desc: '吸拖一体与自动集尘',
    schema: [num('吸力', 'Pa'), num('续航', 'min'), num('噪音', 'dB', false, 0.8), num('水箱', 'L')],
    brands: ['磐石', '云舟', '栖居'],
    series: ['T', 'X', 'S'],
    count: 10,
    build(i) {
      const suction = [3000, 5000, 8000][i % 3];
      const battery = [120, 150, 180][i % 3];
      const noise = [55, 60, 65][i % 3];
      const tank = [0.3, 0.4, 0.5][i % 3];
      return {
        attributes: { 吸力: suction, 续航: battery, 噪音: noise, 水箱: tank },
        price: [1299, 1599, 1899, 2199, 2499, 2799, 3199, 3599, 3999, 4999][i],
        name: `${pick(this.brands)} ${pick(this.series)}${i + 1} 扫拖机器人`,
        subtitle: `${suction}Pa 吸力 · 自动集尘 · ${tank}L 水箱`,
        tags: ['扫地机器人', '扫拖一体', '自动集尘', '智能家居']
      };
    }
  }
];

const products = [];
for (const leaf of leaves) {
  const category = {
    id: leaf.id,
    parentId: leaf.parentId,
    name: leaf.name,
    icon: leaf.icon,
    description: leaf.desc,
    sortOrder: 10,
    attributeSchema: leaf.schema
  };
  categories.push(category);
  for (let i = 0; i < leaf.count; i++) {
    const built = leaf.build(i);
    const attrs = built.attributes;
    const attrText = Object.entries(attrs)
      .map(([k, v]) => {
        const meta = leaf.schema.find((s) => s.key === k);
        return `${k} ${v}${meta?.unit || ''}`;
      })
      .join('，');
    const id = `p_${leaf.id.replace('cat_', '')}_${String(i + 1).padStart(2, '0')}`;
    const rating = (4.3 + rnd() * 0.6).toFixed(1);
    const sales = between(60, 2600);
    products.push({
      id,
      name: built.name,
      subtitle: built.subtitle,
      categoryId: leaf.id,
      brand: built.name.split(' ')[0],
      price: money(built.price),
      originalPrice: money(built.price * (1.15 + rnd() * 0.25)),
      stock: between(30, 500),
      sales,
      rating: Number(rating),
      reviewCount: Math.max(12, Math.floor(sales / 7)),
      description: `${built.name}，${attrText}。${leaf.desc}。所有参数均来自商品库，支持同品类逐项对比；推荐理由只引用真实字段，不虚构价格与库存。`,
      attributes: attrs,
      tags: [...new Set([leaf.name, ...built.tags, built.name.split(' ')[0]])].join(','),
      image: `/img/${leaf.image}.svg`,
      status: 1
    });
  }
}

const output = { generatedAt: new Date().toISOString(), categories, products };
const target = path.resolve('backend/src/main/resources/seed/catalog.json');
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, JSON.stringify(output, null, 2), 'utf8');
console.log(`生成完成: ${categories.length} 个分类, ${products.length} 件商品 -> ${target}`);
