export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

export interface AttributeMeta {
  key: string
  unit?: string
  type?: 'number' | 'enum' | string
  higherBetter?: boolean
  weight?: number
  options?: string[]
}

export interface Product {
  id: string
  name: string
  subtitle?: string
  categoryId: string
  categoryName?: string
  brand?: string
  price: number
  originalPrice?: number
  stock: number
  sales: number
  rating: number
  reviewCount: number
  description?: string
  attributes: Record<string, string | number | boolean>
  attributeSchema?: AttributeMeta[]
  tags: string[]
  image: string
  images?: string[]
  favorited?: boolean
  status?: number
  createdAt?: string
}

export interface Category {
  id: string
  parentId?: string | null
  name: string
  icon?: string
  description?: string
  sortOrder?: number
  children: Category[]
}

export interface User {
  id: string
  username: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  role: 'USER' | 'ADMIN'
  status?: number
  createdAt?: string
}

export interface LoginResponse {
  token: string
  user: User
}

export interface Profile {
  user: User
  orderCount: number
  favoriteCount: number
  cartCount: number
}

export interface Address {
  id: string
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: number
  createdAt?: string
}

export interface AddressPayload {
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault?: number | boolean
}

export interface Preference {
  key: string
  value: string
  weight: number
}

export interface CartItem {
  id: number
  productId: string
  name: string
  image: string
  price: number
  originalPrice?: number
  stock: number
  quantity: number
  selected: number
  subtotal: number
  available: boolean
}

export interface CartSummary {
  items: CartItem[]
  totalQuantity: number
  selectedCount: number
  totalAmount: number
  savedAmount: number
}

export interface OrderItem {
  id: number
  productId: string
  productName: string
  productImage: string
  productPrice: number
  quantity: number
}

export interface Order {
  id: string
  orderNo: string
  status: string
  statusLabel: string
  totalAmount: number
  receiver: string
  phone: string
  addressText: string
  remark?: string
  createdAt: string
  paidAt?: string
  shippedAt?: string
  finishedAt?: string
  expiredAt?: string
  items: OrderItem[]
  totalQuantity: number
}

export interface Review {
  id: number
  productId: string
  username: string
  rating: number
  content: string
  createdAt: string
}

export interface RecommendedProduct {
  product: Product
  reason: string
  score: number
  highlights: string[]
  reasonFallback: boolean
}

export interface AgentStep {
  step: string
  stepName: string
  status: string
  durationMs: number
  summary: string
  createdAt: string
}

export interface CompareProduct {
  id: string
  name: string
  image: string
  price: number
  rating: number
  stock: number
  brand?: string
}

export interface CompareDimension {
  key: string
  unit?: string
  type: string
  higherBetter: boolean
  weight: number
  displayValues: Record<string, string>
  normalizedValues: Record<string, number>
  bestProductId?: string
}

export interface CompareScore {
  productId: string
  totalScore: number
  contributions: Record<string, number>
  rank: number
}

export interface CompareResult {
  products: CompareProduct[]
  dimensions: CompareDimension[]
  scores: CompareScore[]
  recommendedId: string
  recommendedName: string
  advice: string
  highlights: string[]
  degraded: boolean
  taskId: string
}

export interface ChatResponse {
  conversationId: string
  taskId: string
  intent: string
  reply: string
  products: RecommendedProduct[]
  clarifyOptions: string[]
  orders: Order[]
  compare?: CompareResult
  steps: AgentStep[]
  degraded: boolean
  memorySummary?: string
}

export interface Conversation {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  turnCount: number
}

export interface Turn {
  id: number
  role: 'user' | 'assistant'
  content: string
  intent?: string
  products?: RecommendedProduct[]
  taskId?: string
  createdAt: string
}

export interface ExecutionLog {
  id: number
  taskId: string
  conversationId?: string
  agentId: string
  step: string
  stepName: string
  input?: string
  output?: string
  status: string
  durationMs: number
  createdAt: string
}

export interface LlmStatus {
  configured: boolean
  available: boolean
  mode: string
  model: string
  callCount: number
  failCount: number
  lastError?: string
}

export interface Dashboard {
  userCount: number
  productCount: number
  orderCount: number
  todayOrderCount: number
  agentLogCount: number
  totalSales: number
  todaySales: number
  orderStatus: { status: string; label: string; count: number }[]
  topProducts: Product[]
  recentOrders: Order[]
  trend: { date: string; orders: number; amount: number }[]
}

export interface AdminProductPayload {
  name: string
  subtitle?: string
  categoryId: string
  brand?: string
  price: number
  originalPrice?: number
  stock: number
  description?: string
  attributes: Record<string, unknown>
  tags?: string
  image?: string
  status: number
}
