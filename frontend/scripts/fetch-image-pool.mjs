#!/usr/bin/env node
/**
 * 任务一升级版：为每个品类准备 10 张互不相同的真实照片。
 * 1) 验证候选 URL 是否可用（HTTP 200 且 content-type 是图片）
 * 2) 下载到 frontend/public/images/catalog/<category>/<n>.jpg 作为本地兜底
 * 3) 输出可用清单供下一步写入 catalog.json
 *
 * 用法：node scripts/fetch-image-pool.mjs
 */
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const poolFile = join(root, '.cache', 'tmp', 'pool.txt')
const outRoot = join(root, 'frontend', 'public', 'images', 'catalog')
const manifestFile = join(root, '.cache', 'tmp', 'image-manifest.json')

const lines = (await readFile(poolFile, 'utf8')).split('\n').map((l) => l.trim()).filter(Boolean)
const byCategory = new Map()
for (const line of lines) {
  const [category, id] = line.split('|')
  if (!category || !id) continue
  if (!byCategory.has(category)) byCategory.set(category, [])
  byCategory.get(category).push(id)
}

const url = (id, w = 900) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&q=80`

const manifest = {}
let downloaded = 0
let unavailable = 0

for (const [category, ids] of byCategory) {
  const dir = join(outRoot, category)
  await mkdir(dir, { recursive: true })
  const usable = []
  let index = 0

  for (const id of ids) {
    const target = join(dir, `${index + 1}.jpg`)
    // 已存在则视为可用，跳过重复下载
    if (existsSync(target)) {
      usable.push({ id, local: `/images/catalog/${category}/${index + 1}.jpg`, remote: url(id) })
      index++
      continue
    }
    try {
      const res = await fetch(url(id))
      const type = res.headers.get('content-type') || ''
      if (!res.ok || !type.startsWith('image/')) {
        console.warn(`  ✗ ${category} ${id} -> ${res.status} ${type}`)
        unavailable++
        continue
      }
      await writeFile(target, Buffer.from(await res.arrayBuffer()))
      usable.push({ id, local: `/images/catalog/${category}/${index + 1}.jpg`, remote: url(id) })
      index++
      downloaded++
    } catch (e) {
      console.warn(`  ✗ ${category} ${id} -> ${e.message}`)
      unavailable++
    }
  }
  manifest[category] = usable
  console.log(`${category}: 可用 ${usable.length}/${ids.length}`)
}

await writeFile(manifestFile, JSON.stringify(manifest, null, 2), 'utf8')
console.log(`\n下载 ${downloaded} 张，不可用 ${unavailable} 张`)
console.log(`清单：${manifestFile}`)