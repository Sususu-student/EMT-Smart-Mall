import { chromium } from 'playwright-core'
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
page.setDefaultTimeout(120000)
const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
await page.fill('input[autocomplete="username"]', 'demo')
await page.fill('input[autocomplete="current-password"]', 'Demo@123')
await page.click('button.submit')
await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 20000 })
await page.goto('http://127.0.0.1:5173/chat', { waitUntil: 'domcontentloaded' })
await page.waitForSelector('textarea')
await page.click('.new-chat-wide').catch(() => {})
await page.waitForTimeout(500)
await page.fill('textarea', '我想买个耳机，跑步用')
await page.click('.send-btn')
await page.waitForTimeout(40000)
console.log('after msg1:', await page.evaluate(() => ({
  products: document.querySelectorAll('.agent-product').length,
  text: document.body.innerText.slice(-260)
})))
await page.fill('textarea', '300以内，要防水')
await page.click('.send-btn')
await page.waitForTimeout(60000)
const final = await page.evaluate(() => ({
  products: document.querySelectorAll('.agent-product').length,
  text: document.body.innerText.slice(-300)
}))
console.log('after msg2:', JSON.stringify(final, null, 2))
console.log('errors:', errors.slice(0, 3))
await page.screenshot({ path: 'G:/EMT/.cache/shots-seller/05-chat-diag.png' })
await browser.close()
