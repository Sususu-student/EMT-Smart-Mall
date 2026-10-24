import { chromium } from 'playwright-core'

const BASE = 'http://127.0.0.1:5173'
const OUT = 'G:/EMT/.cache/shots-style'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'

const browser = await chromium.launch({ executablePath: CHROME, headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 })

const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
page.on('pageerror', (e) => errors.push(String(e)))

// 首页
await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.screenshot({ path: `${OUT}/01-home-hero.png` })
await page.screenshot({ path: `${OUT}/01-home-top.png`, clip: { x: 0, y: 0, width: 1440, height: 700 } })

// 字体渲染检查
const fontInfo = await page.evaluate(() => {
  const h1 = document.querySelector('.hero-copy h1')
  const price = document.querySelector('.price-strong')
  const catIcon = document.querySelector('.category-card .cat-icon svg')
  const get = (el) => (el ? getComputedStyle(el).fontFamily : null)
  return {
    h1Text: h1?.textContent?.trim().slice(0, 20),
    h1Font: get(h1),
    h1Weight: h1 ? getComputedStyle(h1).fontWeight : null,
    priceFont: get(price),
    hasSvgIcon: Boolean(catIcon),
    notoLoaded: document.fonts.check('700 48px "Noto Serif SC"'),
    interLoaded: document.fonts.check('700 18px "Inter"')
  }
})

// 品类区域
await page.locator('.category-section').scrollIntoViewIfNeeded()
await page.waitForTimeout(900)
await page.screenshot({ path: `${OUT}/02-home-category.png` })

// 商品卡 hover
await page.locator('.product-card').first().scrollIntoViewIfNeeded()
await page.waitForTimeout(600)
const card = page.locator('.product-card').first()
const before = await card.boundingBox()
await card.hover()
await page.waitForTimeout(600)
const after = await card.boundingBox()
await page.screenshot({ path: `${OUT}/03-card-hover.png` })

// 滚动后导航磨砂 + 公告条收起
await page.evaluate(() => window.scrollTo(0, 600))
await page.waitForTimeout(900)
const noticeHeight = await page.locator('.notice').evaluate((el) => el.getBoundingClientRect().height)
await page.screenshot({ path: `${OUT}/04-header-scrolled.png`, clip: { x: 0, y: 0, width: 1440, height: 260 } })

// 商品列表页侧栏图标
await page.goto(`${BASE}/products`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/05-products-sidebar.png` })

// 登录后 AI 对话页
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
await page.fill('input[placeholder*="用户名"], input[placeholder*="账号"]', 'demo')
await page.fill('input[type="password"]', 'Demo@123')
await page.keyboard.press('Enter')
await page.waitForTimeout(2500)
await page.goto(`${BASE}/chat`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.screenshot({ path: `${OUT}/06-chat.png` })

console.log(JSON.stringify({ fontInfo, hover: { beforeY: before?.y, afterY: after?.y }, noticeHeight, errors }, null, 2))
await browser.close()