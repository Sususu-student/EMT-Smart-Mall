#!/usr/bin/env node
/**
 * 把每个品类下「已人工核对内容正确」的本地图片，按顺序轮流分配给该品类商品。
 * 这样同一品类的商品图片会依次循环，不再全部一样；外链与本地兜底同时写入。
 *
 * 用法：node scripts/assign-product-images.mjs
 */
import { readdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const catalogPath = join(root, 'backend', 'src', 'main', 'resources', 'seed', 'catalog.json')
const imageRoot = join(root, 'frontend', 'public', 'images', 'catalog')

/**
 * 每个本地图片对应的 Unsplash 原图 id（用于生成外链）。
 * 只保留经过人工核对、内容确实匹配品类的图片；缺省时退回本地图。
 */
const SOURCE = {
  headphone: {
    '1.jpg': 'photo-1505740420928-5e560c06d30e',
    '2.jpg': 'photo-1583394838336-acd977736f90',
    '3.jpg': 'photo-1546435770-a3e426bf472b',
    '4.jpg': 'photo-1484704849700-f032a568e944',
    '5.jpg': 'photo-1545127398-14699f92334b',
    '6.jpg': 'photo-1577174881658-0f30ed549adc',
    '7.jpg': 'photo-1524678606370-a47ad25cb82a',
    '8.jpg': 'photo-1618366712010-f4ae9c647dcb',
    '9.jpg': 'photo-1590658268037-6bf12165a8df',
    '10.jpg': 'photo-1606220945770-b5b6c2c55bf1'
  },
  phone: {
    '1.jpg': 'photo-1511707171634-5f897ff02aa9',
    '2.jpg': 'photo-1598327105666-5b89351aff97',
    '3.jpg': 'photo-1580910051074-3eb694886505',
    '4.jpg': 'photo-1523206489230-c012c64b2b48',
    '5.jpg': 'photo-1601784551446-20c9e07cdbdb',
    '6.jpg': 'photo-1567581935884-3349723552ca',
    '7.jpg': 'photo-1512941937669-90a1b58e7e9c',
    '8.jpg': 'photo-1616348436168-de43ad0db179',
    '9.jpg': 'photo-1574944985070-8f3ebc6b79d2',
    '10.jpg': 'photo-1585060544812-6b45742d762f'
  },
  watch: {
    '1.jpg': 'photo-1523275335684-37898b6baf30',
    '2.jpg': 'photo-1546435770-a3e426bf472b',
    '3.jpg': 'photo-1434493789847-2f02dc6ca35d',
    '4.jpg': 'photo-1508057198894-247b23fe5ade',
    '5.jpg': 'photo-1522312346375-d1a52e2b99b3',
    '6.jpg': 'photo-1539874754764-5a96559165b0',
    '7.jpg': 'photo-1548171915-e79a380a2a4b',
    '8.jpg': 'photo-1611591437281-460bfbe1220a',
    '9.jpg': 'photo-1622434641406-a158123450f9',
    '10.jpg': 'photo-1594534475808-b18fc33b045e'
  },
  laptop: {
    '1.jpg': 'photo-1496181133206-80ce9b88a853',
    '2.jpg': 'photo-1517336714731-489689fd1ca8',
    '3.jpg': 'photo-1541807084-5c52b6b3adef',
    '4.jpg': 'photo-1525547719571-a2d4ac8945e2',
    '5.jpg': 'photo-1531297484001-80022131f5a1',
    '6.jpg': 'photo-1484788984921-03950022c9ef',
    '7.jpg': 'photo-1588872657578-7efd1f1555ed',
    '9.jpg': 'photo-1593642702821-c8da6771f0c6',
    '10.jpg': 'photo-1603302576837-37561b2e2302'
  },
  yoga: {
    '1.jpg': 'photo-1544367567-0f2fcb009e0b',
    '2.jpg': 'photo-1575052814086-f385e2e2ad1b',
    '3.jpg': 'photo-1506126613408-eca07ce68773',
    '4.jpg': 'photo-1599901860904-17e6ed7083a0',
    '5.jpg': 'photo-1518611012118-696072aa579a',
    '6.jpg': 'photo-1588286840104-8957b019727f',
    '7.jpg': 'photo-1552196563-55cd4e45efb3',
    '8.jpg': 'photo-1591291621164-2c6367723315',
    '9.jpg': 'photo-1601925260368-ae2f83cf8b7f',
    '10.jpg': 'photo-1518310383802-640c2de311b2'
  },
  running: {
    '1.jpg': 'photo-1542291026-7eec264c27ff',
    '2.jpg': 'photo-1595950653106-6c9ebd614d3a',
    '3.jpg': 'photo-1608231387042-66d1773070a5',
    '4.jpg': 'photo-1560769629-975ec94e6a86',
    '5.jpg': 'photo-1552346154-21d32810aba3',
    '6.jpg': 'photo-1606107557195-0e29a4b5b4aa',
    '7.jpg': 'photo-1587563871167-1ee9c731aefb',
    '8.jpg': 'photo-1539185441755-769473a23570',
    '9.jpg': 'photo-1465453869711-7e174808ace9',
    '10.jpg': 'photo-1491553895911-0055eca6402d'
  },
  skincare: {
    '1.jpg': 'photo-1556228720-195a672e8a03',
    '2.jpg': 'photo-1571781926291-c477ebfd024b',
    '3.jpg': 'photo-1596462502278-27bfdc403348',
    '4.jpg': 'photo-1620916566398-39f1143ab7be',
    '5.jpg': 'photo-1608248543803-ba4f8c70ae0b',
    '6.jpg': 'photo-1612817288484-6f916006741a',
    '7.jpg': 'photo-1585652757141-8837d676fac8',
    '8.jpg': 'photo-1617897903246-719242758050',
    '9.jpg': 'photo-1522335789203-aabd1fc54bc9',
    '10.jpg': 'photo-1512207736890-6ffed8a84e8d'
  },
  coffee: {
    '1.jpg': 'photo-1495474472287-4d71bcdd2085',
    '2.jpg': 'photo-1517668808822-9ebb02f2a0e6',
    '3.jpg': 'photo-1509042239860-f550ce710b93',
    '4.jpg': 'photo-1559056199-641a0ac8b55e',
    '5.jpg': 'photo-1572442388796-11668a67e53d',
    '6.jpg': 'photo-1521017432531-fbd92d768814',
    '7.jpg': 'photo-1442512595331-e89e73853f31',
    '8.jpg': 'photo-1514432324607-a09d9b4aefdd',
    '9.jpg': 'photo-1497935586351-b67a49e012bf',
    '10.jpg': 'photo-1554118811-1e0d58224f24'
  },
  beans: {
    '1.jpg': 'photo-1447933601403-0c6688de566e',
    '2.jpg': 'photo-1559056199-641a0ac8b55e',
    '3.jpg': 'photo-1524350876685-274059332603',
    '4.jpg': 'photo-1587734195503-904fca47e0e9',
    '5.jpg': 'photo-1611854779393-1b2da9d400fe',
    '6.jpg': 'photo-1502462041640-b3d7e50d0662',
    '7.jpg': 'photo-1497935586351-b67a49e012bf',
    '8.jpg': 'photo-1514432324607-a09d9b4aefdd',
    '9.jpg': 'photo-1442512595331-e89e73853f31',
    '10.jpg': 'photo-1521017432531-fbd92d768814'
  },
  laptopExtra: {},
  robot: {
    '1.jpg': 'photo-1485827404703-89b55fcc595e',
    '2.jpg': 'photo-1531746790731-6c087fecd65a',
    '3.jpg': 'photo-1527515637462-cff94eecc1ac',
    '4.jpg': 'photo-1626806787461-102c1bfaaea1',
    '5.jpg': 'photo-1519710164239-da123dc03ef4'
  },
  keyboard: {
    '1.jpg': 'photo-1587829741301-dc798b83add3',
    '2.jpg': 'photo-1595044426077-d36d9236d54a',
    '3.jpg': 'photo-1618384887929-16ec33fab9ef',
    '4.jpg': 'photo-1601445638532-3c6f6c3aa1d6',
    '5.jpg': 'photo-1615869442320-fd02a129c77c',
    '6.jpg': 'photo-1541140532154-b024d705b90a'
  },
  purifier: {
    '1.jpg': 'photo-1527515637462-cff94eecc1ac',
    '2.jpg': 'photo-1585771724684-38269d6639fd',
    '3.jpg': 'photo-1626806787461-102c1bfaaea1',
    '5.jpg': 'photo-1610557892470-55d9e80c0bce',
    '6.jpg': 'photo-1584622650111-993a426fbf0a'
  },
  backpack: {
    '1.jpg': 'photo-1553062407-98eeb64c6a62',
    '2.jpg': 'photo-1622560480605-d83c853bc5c3',
    '3.jpg': 'photo-1581605405669-fcdf81165afa',
    '4.jpg': 'photo-1547949003-9792a18a2601',
    '5.jpg': 'photo-1590874103328-eac38a683ce7',
    '6.jpg': 'photo-1577733966973-d680bffd2e80',
    '8.jpg': 'photo-1566150905458-1bf1fc113f0d',
    '9.jpg': 'photo-1622260614153-03223fb72052'
  },
  bag: {
    '1.jpg': 'photo-1548036328-c9fa89d128fa',
    '2.jpg': 'photo-1584917865442-de89df76afd3',
    '3.jpg': 'photo-1591561954557-26941169b49e',
    '4.jpg': 'photo-1594223274512-ad4803739b7c',
    '5.jpg': 'photo-1559563458-527698bf5295',
    '9.jpg': 'photo-1601924994987-69e26d50dc26'
  }
}

/** 品类 id -> 图片目录名 */
const CATEGORY_DIR = {
  cat_headphone: 'headphone',
  cat_phone: 'phone',
  cat_watch: 'watch',
  cat_keyboard: 'keyboard',
  cat_laptop: 'laptop',
  cat_robot: 'robot',
  cat_yoga: 'yoga',
  cat_running: 'running',
  cat_skincare: 'skincare',
  cat_coffee: 'coffee',
  cat_beans: 'beans',
  cat_purifier: 'purifier',
  cat_backpack: 'backpack',
  cat_bag: 'bag'
}

/** 读取每个品类可用的图片序号 */
const pool = {}
for (const [categoryId, dir] of Object.entries(CATEGORY_DIR)) {
  const full = join(imageRoot, dir)
  if (!existsSync(full)) {
    pool[categoryId] = []
    continue
  }
  const files = (await readdir(full))
    .filter((f) => f.endsWith('.jpg'))
    .sort((a, b) => Number.parseInt(a, 10) - Number.parseInt(b, 10))
  pool[categoryId] = files.map((f) => ({ dir, file: f, remote: SOURCE[dir]?.[f] || null }))
}

const catalog = JSON.parse(await readFile(catalogPath, 'utf8'))
const counter = {}
let assigned = 0
const missing = new Set()

for (const product of catalog.products) {
  const list = pool[product.categoryId]
  if (!list || list.length === 0) {
    missing.add(product.categoryId)
    continue
  }
  const index = counter[product.categoryId] || 0
  const pick = list[index % list.length]
  counter[product.categoryId] = index + 1

  // 优先 Unsplash 真实外链，加载失败时回退到本地已核对的同款图
  const local = `/images/catalog/${pick.dir}/${pick.file}`
  product.image = pick.remote
    ? `https://images.unsplash.com/${pick.remote}?auto=format&fit=crop&w=800&q=80`
    : local
  product.imageFallback = local
  assigned++
}

await writeFile(catalogPath, JSON.stringify(catalog, null, 2) + '\n', 'utf8')

console.log(`已为 ${assigned} / ${catalog.products.length} 个商品分配图片`)
for (const [categoryId, list] of Object.entries(pool)) {
  const used = counter[categoryId] || 0
  console.log(`  ${categoryId.padEnd(14)} 图片池 ${String(list.length).padStart(2)} 张，已分配 ${used} 个商品`)
}
if (missing.size) {
  console.warn(`缺少图片的品类：${[...missing].join(', ')}`)
}