import { chromium } from 'playwright-core'

const BASE = 'http://127.0.0.1:5173'
const OUT = 'G:/EMT/.cache/shots-carousel'

const browser = await chromium.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true
})
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 })

const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
page.on('pageerror', (e) => errors.push(String(e)))

await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)
await page.screenshot({ path: `${OUT}/01-carousel-a.png` })

const read = () =>
  page.evaluate(() => ({
    caption: document.querySelector('.product-caption strong')?.textContent?.trim() || null,
    price: document.querySelector('.product-caption span')?.textContent?.trim() || null,
    reason: document.querySelector('.visual-card p')?.textContent?.trim()?.slice(0, 60) || null,
    dots: document.querySelectorAll('.showcase-dot').length,
    activeDot: [...document.querySelectorAll('.showcase-dot')].findIndex((d) => d.classList.contains('active')),
    imgSrc: document.querySelector('.visual-product img')?.getAttribute('src') || null
  }))

const first = await read()
// 等待一次自动切换（3 秒 + 过渡）
await page.waitForTimeout(4200)
const second = await read()
await page.screenshot({ path: `${OUT}/02-carousel-b.png` })

// 点击第 3 个圆点手动切换
await page.locator('.showcase-dot').nth(2).click()
await page.waitForTimeout(1200)
const third = await read()
await page.screenshot({ path: `${OUT}/03-carousel-c.png` })

console.log(JSON.stringify({
  first, second, third,
  autoRotated: first.caption !== second.caption,
  manualSwitched: third.activeDot === 2,
  errors
}, null, 2))

await browser.close()