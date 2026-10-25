import { chromium } from 'playwright-core'
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const failed = []
page.on('requestfailed', (r) => failed.push({ url: r.url().slice(0,90), err: r.failure()?.errorText }))
const resp = []
page.on('response', (r) => { if (r.url().includes('unsplash')) resp.push({ url: r.url().slice(0,80), status: r.status() }) })
await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
await page.waitForTimeout(4000)
const info = await page.evaluate(() => {
  const wrap = document.querySelector('.visual-product')
  const img = wrap?.querySelector('img')
  const cs = img ? getComputedStyle(img) : null
  return {
    wrapHtml: wrap ? wrap.innerHTML.slice(0, 300) : null,
    imgSrc: img?.getAttribute('src')?.slice(0, 80) || null,
    currentSrc: img?.currentSrc?.slice(0, 80) || null,
    complete: img?.complete, naturalW: img?.naturalWidth,
    width: cs?.width, height: cs?.height, objectFit: cs?.objectFit,
    wrapSize: wrap ? { w: wrap.getBoundingClientRect().width, h: wrap.getBoundingClientRect().height } : null
  }
})
console.log(JSON.stringify({ info, unsplashResponses: resp, failed }, null, 2))
await browser.close()
