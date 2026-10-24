import { chromium } from 'playwright-core'

const browser = await chromium.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true
})
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 })

const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))

await page.goto('http://127.0.0.1:5173/', { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
const topShot = await page.evaluate(() => getComputedStyle(document.querySelector('.notice')).height)

await page.evaluate(() => window.scrollTo(0, 700))
await page.waitForTimeout(1000)
await page.screenshot({ path: 'G:/EMT/.cache/shots-style/04b-header-scrolled.png' })

const info = await page.evaluate(() => {
  const h = document.querySelector('.header')
  const n = document.querySelector('.notice')
  const cs = getComputedStyle(h)
  return {
    noticeHeight: n.getBoundingClientRect().height,
    headerTop: h.getBoundingClientRect().top,
    background: cs.backgroundColor,
    blur: cs.backdropFilter,
    shadow: cs.boxShadow
  }
})

// 回到顶部应恢复
await page.evaluate(() => window.scrollTo(0, 0))
await page.waitForTimeout(900)
const restored = await page.evaluate(() => getComputedStyle(document.querySelector('.notice')).height)

console.log(JSON.stringify({ noticeAtTop: topShot, info, noticeRestored: restored, errors }, null, 2))
await browser.close()