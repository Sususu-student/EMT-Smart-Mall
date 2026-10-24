import { chromium } from 'playwright-core'

const BASE = 'http://127.0.0.1:5173'
const OUT = 'G:/EMT/.cache/shots-style'

const browser = await chromium.launch({
  executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
  headless: true
})
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 })

const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
page.on('pageerror', (e) => errors.push(String(e)))

// 登录
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.waitForTimeout(700)
await page.fill('input[placeholder*="用户名"], input[placeholder*="账号"]', 'demo')
await page.fill('input[type="password"]', 'Demo@123')
await page.keyboard.press('Enter')
await page.waitForTimeout(2200)

// 对话页
await page.goto(`${BASE}/chat`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/07-chat-welcome.png` })

// 发消息
const input = page.locator('textarea, input[placeholder*="描述你的购物需求"]').first()
await input.fill('想买跑步用的蓝牙耳机，预算 300 以内，要防水')
await page.locator('.send-btn').click()
await page.waitForTimeout(600)
await page.screenshot({ path: `${OUT}/08-chat-sending.png` })
await page.waitForTimeout(3500)
await page.screenshot({ path: `${OUT}/09-chat-answer.png` })

const styles = await page.evaluate(() => {
  const bubble = document.querySelector('.assistant-bubble')
  const userBubble = document.querySelector('.message.user .bubble')
  const node = document.querySelector('.timeline-node .node-pulse')
  const line = document.querySelector('.timeline-line')
  const cs = (el) => (el ? getComputedStyle(el) : null)
  return {
    assistantBg: cs(bubble)?.backgroundImage?.slice(0, 60),
    assistantBorderLeft: cs(bubble)?.borderLeftColor,
    assistantBorderWidth: cs(bubble)?.borderLeftWidth,
    userBg: cs(userBubble)?.backgroundColor,
    hasPulseDot: Boolean(node),
    lineBg: cs(line)?.backgroundImage?.slice(0, 50),
    stepCount: document.querySelectorAll('.timeline-item').length
  }
})

console.log(JSON.stringify({ styles, errors }, null, 2))
await browser.close()