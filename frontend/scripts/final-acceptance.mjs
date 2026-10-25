import { chromium } from 'playwright-core'

/**
 * 三任务验收脚本：图片真实性、角色注册与跳转、卖家发布商品、登录页轮播。
 * 针对 LLM 模式下单轮对话较慢的情况，所有等待都给了充裕时间。
 */
const BASE = 'http://127.0.0.1:5173'
const OUT = 'G:/EMT/.cache/shots-final'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'

const browser = await chromium.launch({ executablePath: CHROME, headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 })
const page = await context.newPage()
page.setDefaultTimeout(180000)

const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
page.on('pageerror', (e) => errors.push(String(e)))

const result = {}

// ===== 任务一：真实图片 + object-fit =====
await page.goto(`${BASE}/products`, { waitUntil: 'networkidle' })
await page.waitForTimeout(4000)
result.task1 = await page.evaluate(() => {
  const imgs = [...document.querySelectorAll('.product-card img')]
  return {
    total: imgs.length,
    loaded: imgs.filter((i) => i.complete && i.naturalWidth > 0).length,
    unsplash: imgs.filter((i) => (i.currentSrc || i.src).includes('images.unsplash.com')).length,
    objectFit: imgs[0] ? getComputedStyle(imgs[0]).objectFit : null
  }
})
await page.screenshot({ path: `${OUT}/task1-real-images.png` })

// ===== 任务三：登录页轮播 =====
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
const shot1 = await page.evaluate(() => document.querySelector('.product-caption strong')?.textContent?.trim())
await page.waitForTimeout(4000)
const shot2 = await page.evaluate(() => document.querySelector('.product-caption strong')?.textContent?.trim())
result.task3 = {
  first: shot1,
  second: shot2,
  rotated: shot1 !== shot2,
  dots: await page.locator('.showcase-dot').count(),
  imgLoaded: await page.evaluate(() => {
    const i = document.querySelector('.visual-product img')
    return Boolean(i && i.complete && i.naturalWidth > 0)
  })
}
await page.screenshot({ path: `${OUT}/task3-carousel.png` })

// ===== 任务二：注册页角色 =====
await page.locator('.mode-tabs button', { hasText: '注册' }).click()
await page.waitForTimeout(600)
result.task2_roles = await page.evaluate(() => {
  const opts = [...document.querySelectorAll('.role-option')]
  return { count: opts.length, labels: opts.map((o) => o.querySelector('strong')?.textContent?.trim()) }
})
await page.screenshot({ path: `${OUT}/task2-register-roles.png` })

// ===== 任务二：卖家登录 -> 卖家中心 -> 发布商品 =====
await page.locator('.mode-tabs button', { hasText: '登录' }).click()
await page.waitForTimeout(400)
await page.locator('.demo-actions button', { hasText: '卖家' }).click()
await page.waitForTimeout(300)
await page.locator('.submit').click()
await page.waitForURL(/\/seller/, { timeout: 60000 })
await page.waitForTimeout(2500)
result.task2_sellerUrl = page.url()
result.task2_sellerStats = await page.evaluate(() => ({
  stats: [...document.querySelectorAll('.stat-card strong')].map((s) => s.textContent.trim()),
  rows: document.querySelectorAll('.product-row').length
}))
await page.screenshot({ path: `${OUT}/task2-seller-center.png` })

// 发布商品
await page.locator('button', { hasText: '发布商品' }).first().click()
await page.waitForTimeout(800)
const suffix = String(Date.now()).slice(-6)
await page.locator('.publish-form input').first().fill(`验收测试商品 ${suffix}`)
await page.locator('.publish-form textarea').first().fill('续航=24h\n防水等级=IPX5')
await page.locator('.publish-form input[type="number"]').first().fill('188')
await page.waitForTimeout(400)
await page.screenshot({ path: `${OUT}/task2-publish-dialog.png` })
await page.locator('.el-dialog__footer button', { hasText: '确认上架' }).click()
await page.waitForTimeout(3500)
result.task2_published = await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.product-row .info strong')].map((s) => s.textContent.trim())
  return { names: rows }
})
await page.screenshot({ path: `${OUT}/task2-after-publish.png` })

// ===== 任务二：买家登录 -> 访问 /seller 被挡 =====
await page.evaluate(() => localStorage.clear())
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
await page.locator('.demo-actions button', { hasText: '用户' }).click()
await page.waitForTimeout(300)
await page.locator('.submit').click()
await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 60000 })
result.task2_buyerUrl = page.url()
await page.goto(`${BASE}/seller`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2000)
result.task2_buyerBlocked = page.url()

result.errors = errors
console.log(JSON.stringify(result, null, 2))
await browser.close()