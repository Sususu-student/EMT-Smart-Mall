#!/usr/bin/env node
/**
 * 下载 Noto Serif SC 700 的全部 unicode-range 分片到 frontend/public/fonts/noto-serif-sc，
 * 并生成对应的 CSS。生产环境只需保留生成的 woff2 与 css，不需要联网。
 */
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const outDir = join(root, 'public', 'fonts', 'noto-serif-sc')
const cssSource = process.argv[2] || '/tmp/n700.css'
const css = await readFile(cssSource, 'utf8')

await mkdir(outDir, { recursive: true })

const blocks = css.split('@font-face').slice(1)
let ok = 0
let skipped = 0
const faces = []

for (const block of blocks) {
  const url = block.match(/url\((https:[^)]+\.woff2)\)/)?.[1]
  const range = block.match(/unicode-range:\s*([^;]+);/)?.[1]?.trim()
  if (!url || !range) continue
  const name = url.split('/').pop()
  const target = join(outDir, name)
  if (!existsSync(target)) {
    const res = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } })
    if (!res.ok) {
      console.warn('download failed', res.status, url)
      continue
    }
    await writeFile(target, Buffer.from(await res.arrayBuffer()))
    ok++
  } else {
    skipped++
  }
  faces.push({ name, range })
}

const cssOut = faces
  .map(
    (face) => `@font-face {
  font-family: 'Noto Serif SC';
  font-style: normal;
  font-weight: 700;
  font-display: swap;
  src: url('/fonts/noto-serif-sc/${face.name}') format('woff2');
  unicode-range: ${face.range};
}`
  )
  .join('\n\n')

await writeFile(join(root, 'src', 'styles', 'noto-serif-sc.css'), cssOut + '\n')

console.log(`下载完成：新增 ${ok}，已存在 ${skipped}，共 ${faces.length} 个分片`)