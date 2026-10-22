import type { AttributeMeta, Order, Product } from '@/types'

export function money(value: number | string | null | undefined): string {
  const number = Number(value ?? 0)
  if (!Number.isFinite(number)) return '¥0'
  return `¥${number.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

export function plainPrice(value: number | string | null | undefined): string {
  const number = Number(value ?? 0)
  return Number.isInteger(number) ? String(number) : number.toFixed(2)
}

export function dateTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

export function dateOnly(value?: string): string {
  if (!value) return '—'
  return value.slice(0, 10)
}

export function orderStatusType(status: string): 'warning' | 'success' | 'info' | 'danger' | 'primary' {
  switch (status) {
    case 'PENDING_PAYMENT':
      return 'warning'
    case 'PAID':
      return 'primary'
    case 'SHIPPED':
      return 'success'
    case 'COMPLETED':
      return 'info'
    default:
      return 'danger'
  }
}

export function orderStatusLabel(order: Order): string {
  return order.statusLabel || order.status
}

export function intentLabel(intent?: string): string {
  const map: Record<string, string> = {
    search_product: '商品检索',
    compare: '商品对比',
    ask_detail: '商品详情',
    query_order: '订单查询',
    add_to_cart: '加入购物车',
    checkout: '协助下单',
    chitchat: '闲聊',
    clarify: '澄清需求'
  }
  return intent ? map[intent] || intent : '导购'
}

export interface AttributeEntry {
  key: string
  value: string
  unit?: string
  higherBetter?: boolean
}

export function attributeEntries(product?: Product | null): AttributeEntry[] {
  if (!product?.attributes) return []
  const schema = product.attributeSchema || []
  const keys = Object.keys(product.attributes)
  const ordered = [
    ...schema.map((item) => item.key).filter((key) => keys.includes(key)),
    ...keys.filter((key) => !schema.some((item) => item.key === key))
  ]
  return ordered.map((key) => {
    const meta = schema.find((item: AttributeMeta) => item.key === key)
    const raw = product.attributes[key]
    let value = String(raw)
    if (typeof raw === 'number' && Number.isInteger(raw)) value = raw.toFixed(0)
    if (typeof raw === 'boolean') value = raw ? '是' : '否'
    return { key, value, unit: meta?.unit, higherBetter: meta?.higherBetter }
  })
}

export function avatarText(name?: string): string {
  return (name || '智').slice(0, 1).toUpperCase()
}

export function percent(value: number): string {
  return `${Math.round(value)}%`
}
