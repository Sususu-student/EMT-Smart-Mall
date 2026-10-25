#!/usr/bin/env node
/**
 * 任务一：把商品图片换成真实照片。
 * 1) seed/catalog.json 的 image 字段写成 Unsplash 外链
 * 2) 同时下载一份到 frontend/public/images/external/ 作为离线兜底
 *
 * 用法：node scripts/apply-real-images.mjs
 */
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const catalogPath = join(root, 'backend', 'src', 'main', 'resources', 'seed', 'catalog.json')
const outDir = join(root, 'frontend', 'public', 'images', 'external')

/** 每个品类对应的真实照片（已逐个验证过 HTTP 200） */
const PHOTOS = {
  headphone: 'photo-1505740420928-5e560c06d30e',
  phone: 'photo-1511707171634-5f897ff02aa9',
  watch: 'photo-1523275335684-37898b6baf30',
  keyboard: 'photo-1587829741301-dc798b83add3',
  laptop: 'photo-1496181133206-80ce9b88a853',
  robot: 'photo-1558317374-067fb5f30001',
  yoga: 'photo-1544367567-0f2fcb009e0b',
  running: 'photo-1542291026-7eec264c27ff',
  skincare: 'photo-1556228720-195a672e8a03',
  coffee: 'photo-1495474472287-4d71bcdd2085',
  beans: 'photo-1447933601403-0c6688de566e',
  purifier: 'photo-1527515637462-cff94eecc1ac',
  backpack: 'photo-1553062407-98eeb64c6a62',
  bag: 'photo-1548036328-c9fa89d128fa'
}

/** 品类 id -> 照片 key */
const CATEGORY_PHOTO = {
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

const IMG_PARAMS = '?auto=format&fit=crop&w=800&q=80'

function externalUrl(key) {
  return `https://images.unsplash.com/${PHOTOS[key]}${IMG_PARAMS}`
}

function localUrl(key) {
  return `/images/external/${key}.jpg`
}

await mkdir(outDir, { recursive: true })

// 1) 下载兜底图并验证确实是图片
let downloaded = 0
let skipped = 0
for (const [key, id] of Object.entries(PHOTOS)) {
  const target = join(outDir, `${key}.jpg`)
  if (existsSync(target)) {
    skipped++
    continue
  }
  const url = `https://images.unsplash.com/${id}?auto=format&fit=crop&w=800&q=80`
  const res = await fetch(url)
  if (!res.ok) {
    console.warn(`  ✗ ${key} 下载失败 ${res.status}`)
    continue
  }
  const type = res.headers.get('content-type') || ''
  if (!type.startsWith('image/')) {
    console.warn(`  ✗ ${key} 返回的不是图片：${type}`)
    continue
  }
  await writeFile(target, Buffer.from(await res.arrayBuffer()))
  downloaded++
}

// 2) 更新 catalog.json
const catalog = JSON.parse(await readFile(catalogPath, 'utf8'))
let changed = 0
const missing = new Set()

for (const product of catalog.products) {
  const photoKey = CATEGORY_PHOTO[product.categoryId]
  if (!photoKey) {
    missing.add(product.categoryId)
    continue
  }
  product.image = externalUrl(photoKey)
  // 兜底字段：前端加载失败时回退
  product.imageFallback = localUrl(photoKey)
  changed++
}

await writeFile(catalogPath, JSON.stringify(catalog, null, 2) + '\n', 'utf8')

console.log(`图片下载：新增 ${downloaded} 张，已存在 ${skipped} 张`)
console.log(`catalog.json：更新 ${changed} / ${catalog.products.length} 个商品图片`)
if (missing.size) {
  console.warn(`未映射的品类（保留原图）：${[...missing].join(', ')}`)
}
console.log(`示例：${catalog.products[0].image}`)
console.log(`兜底：${catalog.products[0].imageFallback}`)