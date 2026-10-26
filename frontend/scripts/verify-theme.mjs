import { chromium } from 'playwright-core'

const BASE = 'http://127.0.0.1:5173'
const OUT = 'G:/EMT/.cache/shots-theme'

const browser = await chromium.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true
})
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 })
page.setDefaultTimeout(60000)
const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
page.on('pageerror', (e) => errors.push(String(e)))

const result = {}

// 1) 默认浅色
await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
result.light = await page.evaluate(() => ({
  htmlClass: document.documentElement.className,
  colorScheme: document.documentElement.style.colorScheme,
  bodyBg: getComputedStyle(document.body).backgroundColor,
  bodyColor: getComputedStyle(document.body).color,
  hasToggle: Boolean(document.querySelector('.theme-toggle'))
}))
await page.screenshot({ path: `${OUT}/01-light-home.png` })

// 2) 点击切换到暗色
await page.click('.theme-toggle')
await page.waitForTimeout(900)
result.dark = await page.evaluate(() => {
  const cs = getComputedStyle(document.body)
  return {
    htmlClass: document.documentElement.className,
    colorScheme: document.documentElement.style.colorScheme,
    bodyBg: cs.backgroundColor,
    bodyColor: cs.color,
    stored: localStorage.getItem('sg_theme'),
    brandColor: getComputedStyle(document.documentElement).getPropertyValue('--brand').trim(),
    toggleDark: document.querySelector('.theme-toggle')?.classList.contains('dark')
  }
})
await page.screenshot({ path: `${OUT}/02-dark-home.png` })

// 3) 刷新后是否保持
await page.reload({ waitUntil: 'networkidle' })
await page.waitForTimeout(2000)
result.afterReload = await page.evaluate(() => ({
  htmlClass: document.documentElement.className,
  bodyBg: getComputedStyle(document.body).backgroundColor,
  toggleDark: document.querySelector('.theme-toggle')?.classList.contains('dark')
}))
await page.screenshot({ path: `${OUT}/03-dark-after-reload.png` })

// 4) 逛商品页与商品详情，看图片处理
await page.goto(`${BASE}/products`, { waitUntil: 'networkidle' })
await page.waitForTimeout(3500)
result.darkProducts = await page.evaluate(() => {
  const img = document.querySelector('.product-card img')
  const card = document.querySelector('.product-card')
  return {
    imgFilter: img ? getComputedStyle(img).filter : null,
    cardBg: card ? getComputedStyle(card).backgroundColor : null,
    cardBorder: card ? getComputedStyle(card).borderColor : null
  }
})
await page.screenshot({ path: `${OUT}/04-dark-products.png` })

// 5) 回到浅色确认可逆
await page.click('.theme-toggle')
await page.waitForTimeout(900)
result.backToLight = await page.evaluate(() => ({
  htmlClass: document.documentElement.className,
  bodyBg: getComputedStyle(document.body).backgroundColor,
  stored: localStorage.getItem('sg_theme')
}))

result.errors = errors
console.log(JSON.stringify(result, null, 2))
await browser.close()