import { chromium } from 'playwright-core'
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 })
await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
await page.waitForTimeout(5000)
await page.screenshot({ path: 'G:/EMT/.cache/shots-seller/04-login-final.png' })
const s = await page.evaluate(() => {
  const img = document.querySelector('.visual-product img')
  return { naturalW: img?.naturalWidth, complete: img?.complete, src: img?.currentSrc?.slice(0,60) }
})
console.log(JSON.stringify(s))
await browser.close()
