import { chromium } from 'playwright-core'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'

const BASE = process.env.SMOKE_BASE || 'http://127.0.0.1:5173'
const OUT = process.env.SHOT_DIR || fileURLToPath(new URL('../../.cache/shots', import.meta.url))
fs.mkdirSync(OUT, { recursive: true })

const browser = await chromium.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true,
  args: ['--no-sandbox', '--disable-gpu']
})

const errors = []
const failed = []

function watch(page) {
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(`console: ${message.text()}`)
  })
  page.on('requestfailed', (request) => {
    const failure = request.failure()?.errorText || ''
    if (!failure.includes('net::ERR_ABORTED')) failed.push(`${request.url()} ${failure}`)
  })
}

const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const page = await context.newPage()
watch(page)

await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(2200)
await page.screenshot({ path: `${OUT}/01-home.png`, fullPage: false })
console.log('home title:', await page.title())

await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
await page.fill('input[autocomplete="username"]', 'demo')
await page.fill('input[autocomplete="current-password"]', 'Demo@123')
await page.click('button.submit')
await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 })
console.log('logged in, url:', page.url())

await page.goto(`${BASE}/chat`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('textarea', { timeout: 10000 })
await page.click('.new-chat-wide')
await page.waitForTimeout(300)
await page.fill('textarea', '我想买个耳机，跑步用')
await page.click('.send-btn')
await page.waitForFunction(() => document.body.innerText.includes('预算'), { timeout: 20000 })
await page.waitForTimeout(600)
await page.screenshot({ path: `${OUT}/02-chat-clarify.png` })

await page.fill('textarea', '300以内，要防水')
await page.click('.send-btn')
await page.waitForFunction(() => document.querySelectorAll('.agent-product').length >= 2, { timeout: 30000 })
await page.waitForTimeout(900)
await page.screenshot({ path: `${OUT}/03-chat-results.png` })
console.log('products:', await page.locator('.agent-product').count())

await page.fill('textarea', '帮我对比前两个')
await page.click('.send-btn')
await page.waitForSelector('.chat-compare', { timeout: 30000 })
await page.waitForTimeout(500)
await page.screenshot({ path: `${OUT}/04-chat-compare.png` })

await page.click('.cc-btn')
await page.waitForURL(/\/compare/, { timeout: 10000 })
await page.waitForSelector('.matrix', { timeout: 20000 })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/05-compare.png`, fullPage: true })

await page.goto(`${BASE}/products`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1500)
await page.screenshot({ path: `${OUT}/06-products.png` })

await page.goto(`${BASE}/product/p_headphone_02`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1400)
await page.screenshot({ path: `${OUT}/07-detail.png`, fullPage: false })

await page.goto(`${BASE}/cart`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/08-cart.png` })

await page.goto(`${BASE}/checkout`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1500)
await page.screenshot({ path: `${OUT}/08b-checkout.png` })

await page.goto(`${BASE}/orders`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/09-orders.png` })
await page.locator('.order-body').first().click()
await page.waitForURL(/\/order\//, { timeout: 10000 })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/09b-order-detail.png` })

const adminContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const adminPage = await adminContext.newPage()
watch(adminPage)
await adminPage.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
await adminPage.fill('input[autocomplete="username"]', 'admin')
await adminPage.fill('input[autocomplete="current-password"]', 'Admin@123')
await adminPage.click('button.submit')
await adminPage.waitForURL(/\/admin/, { timeout: 15000 })
await adminPage.waitForTimeout(2000)
await adminPage.screenshot({ path: `${OUT}/10-admin.png` })

await adminPage.goto(`${BASE}/admin/agent-logs`, { waitUntil: 'domcontentloaded' })
await adminPage.waitForTimeout(1400)
await adminPage.screenshot({ path: `${OUT}/11-admin-logs.png` })

await adminPage.goto(`${BASE}/admin/products`, { waitUntil: 'domcontentloaded' })
await adminPage.waitForTimeout(1400)
await adminPage.screenshot({ path: `${OUT}/12-admin-products.png` })

await adminPage.goto(`${BASE}/admin/orders`, { waitUntil: 'domcontentloaded' })
await adminPage.waitForTimeout(1200)
await adminPage.screenshot({ path: `${OUT}/13-admin-orders.png` })

await adminPage.goto(`${BASE}/admin/users`, { waitUntil: 'domcontentloaded' })
await adminPage.waitForTimeout(1200)
await adminPage.screenshot({ path: `${OUT}/14-admin-users.png` })

await browser.close()

console.log('console errors:', errors.length)
errors.slice(0, 20).forEach((error) => console.log(' -', error))
console.log('failed requests:', failed.length)
failed.slice(0, 20).forEach((error) => console.log(' -', error))
