import { chromium } from 'playwright-core'

const BASE = 'http://127.0.0.1:5173'
const OUT = 'G:/EMT/.cache/shots-seller'

const browser = await chromium.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true
})
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 })
const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
page.on('pageerror', (e) => errors.push(String(e)))

// 1) 注册页角色选择
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.locator('.mode-tabs button', { hasText: '注册' }).click()
await page.waitForTimeout(700)
await page.screenshot({ path: `${OUT}/01-register-roles.png` })
const roleUi = await page.evaluate(() => {
  const opts = [...document.querySelectorAll('.role-option')]
  return {
    count: opts.length,
    labels: opts.map((o) => o.querySelector('strong')?.textContent?.trim()),
    active: opts.findIndex((o) => o.classList.contains('active'))
  }
})

// 2) 卖家登录 -> 应跳 /seller
await page.locator('.mode-tabs button', { hasText: '登录' }).click()
await page.waitForTimeout(400)
await page.locator('.demo-actions button', { hasText: '卖家' }).click()
await page.waitForTimeout(400)
await page.locator('.submit').click()
await page.waitForTimeout(3000)
const afterSellerLogin = page.url()
await page.screenshot({ path: `${OUT}/02-seller-center.png` })
const sellerUi = await page.evaluate(() => ({
  stats: [...document.querySelectorAll('.stat-card strong')].map((s) => s.textContent.trim()),
  productRows: document.querySelectorAll('.product-row').length,
  firstProduct: document.querySelector('.info strong')?.textContent?.trim() || null,
  hasPublishBtn: [...document.querySelectorAll('button')].some((b) => b.textContent.includes('发布商品'))
}))

// 3) 打开发布商品对话框
await page.locator('button', { hasText: '发布商品' }).first().click()
await page.waitForTimeout(900)
await page.screenshot({ path: `${OUT}/03-publish-dialog.png` })
const dialogVisible = await page.locator('.publish-form').isVisible()
await page.keyboard.press('Escape')
await page.waitForTimeout(500)

// 4) 买家登录 -> 访问 /seller 应被挡回
await page.evaluate(() => localStorage.clear())
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
await page.locator('.demo-actions button', { hasText: '用户' }).click()
await page.waitForTimeout(300)
await page.locator('.submit').click()
await page.waitForTimeout(2500)
const afterBuyerLogin = page.url()
await page.goto(`${BASE}/seller`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)
const buyerBlockedUrl = page.url()

console.log(JSON.stringify({
  roleUi,
  afterSellerLogin,
  sellerUi,
  dialogVisible,
  afterBuyerLogin,
  buyerBlockedUrl,
  errors
}, null, 2))

await browser.close()