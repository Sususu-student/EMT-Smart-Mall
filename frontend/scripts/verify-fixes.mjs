import { chromium } from 'playwright-core'

const BASE = 'http://127.0.0.1:5173'
const OUT = 'G:/EMT/.cache/shots-final'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'

const browser = await chromium.launch({ executablePath: CHROME, headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 })
page.setDefaultTimeout(90000)
const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
page.on('pageerror', (e) => errors.push(String(e)))

const result = {}

// 1) 忘记密码弹窗
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1800)
await page.locator('.forgot-link').click()
await page.waitForTimeout(900)
result.forgotDialog = await page.locator('.forgot-tip').isVisible()
await page.screenshot({ path: `${OUT}/fix4-forgot-password.png` })
await page.keyboard.press('Escape')
await page.waitForTimeout(500)

// 2) 卖家登录 -> 发布商品弹窗看上传组件
await page.locator('.demo-actions button', { hasText: '卖家' }).click()
await page.waitForTimeout(400)
await page.locator('.submit').click()
await page.waitForURL(/\/seller/, { timeout: 60000 })
await page.waitForTimeout(2500)
await page.locator('button', { hasText: '发布商品' }).first().click()
await page.waitForTimeout(1000)
result.uploadBox = await page.evaluate(() => ({
  hasDragger: Boolean(document.querySelector('.upload-box .el-upload-dragger')),
  text: document.querySelector('.upload-inner p')?.textContent?.trim() || null,
  hint: document.querySelector('.upload-inner span')?.textContent?.trim() || null,
  hasUrlInput: Boolean([...document.querySelectorAll('.publish-form label')].find((l) =>
    l.textContent.includes('主图 URL')))
}))
await page.screenshot({ path: `${OUT}/fix3-upload.png` })
await page.keyboard.press('Escape')
await page.waitForTimeout(400)

// 3) 管理员后台：重置密码按钮 + 卖家角色标签
await page.evaluate(() => localStorage.clear())
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
await page.locator('.demo-actions button', { hasText: '管理员' }).click()
await page.waitForTimeout(400)
await page.locator('.submit').click()
await page.waitForURL(/\/admin/, { timeout: 60000 })
await page.goto(`${BASE}/admin/users`, { waitUntil: 'networkidle' })
await page.waitForTimeout(2500)
result.adminUsers = await page.evaluate(() => ({
  hasResetBtn: [...document.querySelectorAll('button')].some((b) => b.textContent.includes('重置密码')),
  roles: [...document.querySelectorAll('.el-tag')].map((t) => t.textContent.trim()).slice(0, 6)
}))
await page.screenshot({ path: `${OUT}/fix4-admin-reset.png` })

result.errors = errors
console.log(JSON.stringify(result, null, 2))
await browser.close()