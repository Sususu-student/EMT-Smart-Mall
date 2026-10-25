import { chromium } from 'playwright-core'
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 })
const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
const failedImgs = []
page.on('requestfailed', (r) => { if (r.resourceType() === 'image') failedImgs.push(r.url().slice(0, 80)) })

await page.goto('http://127.0.0.1:5173/products', { waitUntil: 'networkidle' })
await page.waitForTimeout(3500)
await page.screenshot({ path: 'G:/EMT/.cache/shots-carousel/real-images.png' })

const stats = await page.evaluate(() => {
  const imgs = [...document.querySelectorAll('.product-card img')]
  return {
    total: imgs.length,
    loaded: imgs.filter((i) => i.complete && i.naturalWidth > 0).length,
    fromUnsplash: imgs.filter((i) => (i.currentSrc || i.src).includes('unsplash')).length,
    fromLocal: imgs.filter((i) => (i.currentSrc || i.src).includes('/images/external/')).length,
    objectFit: imgs[0] ? getComputedStyle(imgs[0]).objectFit : null,
    sample: imgs.slice(0, 3).map((i) => ({ w: i.naturalWidth, h: i.naturalHeight, fit: getComputedStyle(i).objectFit }))
  }
})
console.log(JSON.stringify({ stats, failedImgs, errors }, null, 2))
await browser.close()
