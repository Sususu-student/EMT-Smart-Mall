import { request } from './http'
import type {
  Address,
  AddressPayload,
  AdminConversation,
  AgentPrompt,
  AgentTool,
  AdminProductPayload,
  AfterSale,
  ReviewAdmin,
  CartSummary,
  Category,
  ChatResponse,
  CompareResult,
  Conversation,
  Dashboard,
  ExecutionLog,
  LlmStatus,
  LoginResponse,
  Order,
  PageResult,
  Preference,
  Product,
  Profile,
  Review,
  Turn,
  User
} from '@/types'

export const authApi = {
  login: (data: { username: string; password: string }) =>
    request<LoginResponse>({ url: '/user/login', method: 'post', data }),
  register: (data: { username: string; password: string; nickname?: string; email?: string }) =>
    request<LoginResponse>({ url: '/user/register', method: 'post', data })
}

export const userApi = {
  profile: () => request<Profile>({ url: '/user/profile' }),
  updateProfile: (data: Partial<User>) => request<User>({ url: '/user/profile', method: 'put', data }),
  addresses: () => request<Address[]>({ url: '/user/address' }),
  createAddress: (data: AddressPayload) => request<Address>({ url: '/user/address', method: 'post', data }),
  updateAddress: (id: string, data: AddressPayload) =>
    request<Address>({ url: `/user/address/${id}`, method: 'put', data }),
  deleteAddress: (id: string) => request<void>({ url: `/user/address/${id}`, method: 'delete' }),
  setDefaultAddress: (id: string) => request<Address>({ url: `/user/address/${id}/default`, method: 'put' }),
  preferences: () => request<Preference[]>({ url: '/user/preference' }),
  savePreferences: (items: Preference[]) =>
    request<Preference[]>({ url: '/user/preference', method: 'put', data: { items } }),
  favorites: () => request<Product[]>({ url: '/user/favorites' }),
  toggleFavorite: (productId: string) =>
    request<boolean>({ url: `/user/favorite/${productId}`, method: 'post' }),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request<void>({ url: '/user/password', method: 'put', data }),
  history: (limit = 12) => request<Product[]>({ url: '/user/history', params: { limit } })
}

export const productApi = {
  list: (params: Record<string, unknown>) => request<PageResult<Product>>({ url: '/product/list', params }),
  search: (params: Record<string, unknown>) => request<PageResult<Product>>({ url: '/product/search', params }),
  detail: (id: string) => request<Product>({ url: `/product/${id}` }),
  categories: () => request<Category[]>({ url: '/product/category' }),
  related: (id: string) => request<Product[]>({ url: `/product/${id}/related` }),
  reviews: (id: string, params: { page?: number; size?: number }) =>
    request<PageResult<Review>>({ url: `/product/${id}/reviews`, params }),
  addReview: (id: string, data: { rating: number; content: string }) =>
    request<Review>({ url: `/product/${id}/review`, method: 'post', data }),
  batch: (ids: string[]) => request<Product[]>({ url: '/product/batch', params: { ids: ids.join(',') } })
}

export const cartApi = {
  summary: () => request<CartSummary>({ url: '/cart' }),
  add: (productId: string, quantity = 1) =>
    request<unknown>({ url: '/cart', method: 'post', data: { productId, quantity } }),
  update: (id: number, quantity: number) =>
    request<unknown>({ url: `/cart/${id}`, method: 'put', data: { quantity } }),
  remove: (id: number) => request<void>({ url: `/cart/${id}`, method: 'delete' }),
  select: (ids: number[] | null, selected: boolean) =>
    request<void>({ url: '/cart/select', method: 'put', data: { ids, selected } })
}

export const afterSaleApi = {
  create: (data: { orderId: string; type: string; reason: string }) =>
    request<AfterSale>({ url: '/after-sale', method: 'post', data }),
  mine: () => request<AfterSale[]>({ url: '/after-sale' }),
  byOrder: (orderId: string) => request<AfterSale[]>({ url: `/after-sale/order/${orderId}` })
}

export const orderApi = {
  create: (data: {
    productId?: string
    quantity?: number
    cartItemIds?: number[]
    addressId: string
    remark?: string
  }) => request<Order>({ url: '/order', method: 'post', data }),
  list: (params: { status?: string; page?: number; size?: number }) =>
    request<PageResult<Order>>({ url: '/order/list', params }),
  detail: (id: string) => request<Order>({ url: `/order/${id}` }),
  pay: (id: string) => request<Order>({ url: `/order/${id}/pay`, method: 'put' }),
  cancel: (id: string) => request<Order>({ url: `/order/${id}/cancel`, method: 'put' }),
  confirm: (id: string) => request<Order>({ url: `/order/${id}/confirm`, method: 'put' })
}

export const agentApi = {
  chat: (data: { message: string; conversationId?: string }) =>
    request<ChatResponse>({ url: '/agent/chat', method: 'post', data }),
  compare: (data: { productIds: string[]; weights?: Record<string, number>; conversationId?: string }) =>
    request<CompareResult>({ url: '/agent/compare', method: 'post', data }),
  conversations: () => request<Conversation[]>({ url: '/agent/conversations' }),
  turns: (id: string) => request<Turn[]>({ url: `/agent/conversations/${id}/turns` }),
  deleteConversation: (id: string) => request<void>({ url: `/agent/conversations/${id}`, method: 'delete' }),
  logs: (params: { taskId?: string; conversationId?: string }) =>
    request<ExecutionLog[]>({ url: '/agent/logs', params }),
  status: () => request<LlmStatus>({ url: '/agent/status' })
}

export const adminApi = {
  stats: () => request<Dashboard>({ url: '/admin/stats' }),
  products: (params: Record<string, unknown>) =>
    request<PageResult<Product>>({ url: '/admin/products', params }),
  categories: () => request<Category[]>({ url: '/admin/categories' }),
  createCategory: (data: Record<string, unknown>) =>
    request<Category>({ url: '/admin/category', method: 'post', data }),
  updateCategory: (id: string, data: Record<string, unknown>) =>
    request<Category>({ url: `/admin/category/${id}`, method: 'put', data }),
  deleteCategory: (id: string) => request<void>({ url: `/admin/category/${id}`, method: 'delete' }),
  createProduct: (data: AdminProductPayload) =>
    request<Product>({ url: '/admin/product', method: 'post', data }),
  importProducts: (items: AdminProductPayload[]) =>
    request<{ success: number; failed: number; errors: string[] }>({
      url: '/admin/products/import',
      method: 'post',
      data: items
    }),
  updateProduct: (id: string, data: AdminProductPayload) =>
    request<Product>({ url: `/admin/product/${id}`, method: 'put', data }),
  deleteProduct: (id: string) => request<void>({ url: `/admin/product/${id}`, method: 'delete' }),
  updateProductStatus: (id: string, status: number) =>
    request<void>({ url: `/admin/product/${id}/status`, method: 'put', params: { status } }),
  orders: (params: Record<string, unknown>) => request<PageResult<Order>>({ url: '/admin/orders', params }),
  ship: (id: string, data?: { carrier?: string; trackingNo?: string }) =>
    request<Order>({ url: `/admin/order/${id}/ship`, method: 'put', data }),
  reviews: (params: Record<string, unknown>) =>
    request<PageResult<ReviewAdmin>>({ url: '/admin/reviews', params }),
  updateReviewStatus: (id: number, status: number) =>
    request<void>({ url: `/admin/review/${id}/status`, method: 'put', params: { status } }),
  afterSales: (params: Record<string, unknown>) =>
    request<PageResult<AfterSale>>({ url: '/admin/after-sales', params }),
  handleAfterSale: (id: string, data: { status: string; adminNote?: string }) =>
    request<AfterSale>({ url: `/admin/after-sale/${id}/handle`, method: 'put', data }),
  conversations: (params: Record<string, unknown>) =>
    request<PageResult<AdminConversation>>({ url: '/admin/conversations', params }),
  agentPrompts: () => request<AgentPrompt[]>({ url: '/admin/agent/prompts' }),
  updateAgentPrompt: (key: string, content: string) =>
    request<AgentPrompt>({ url: `/admin/agent/prompts/${key}`, method: 'put', data: { content } }),
  resetAgentPrompt: (key: string) =>
    request<void>({ url: `/admin/agent/prompts/${key}/reset`, method: 'post' }),
  agentTools: () => request<AgentTool[]>({ url: '/admin/agent/tools' }),
  agentModel: () => request<LlmStatus>({ url: '/admin/agent/model' }),
  updateAgentModel: (data: { model: string; temperature: number }) =>
    request<LlmStatus>({ url: '/admin/agent/model', method: 'put', data }),
  conversationTurns: (id: string) =>
    request<Turn[]>({ url: `/admin/conversations/${id}/turns` }),
  users: (params: Record<string, unknown>) => request<PageResult<User>>({ url: '/admin/users', params }),
  updateUserStatus: (id: string, status: number) =>
    request<void>({ url: `/admin/user/${id}/status`, method: 'put', params: { status } }),
  agentLogs: (params: Record<string, unknown>) =>
    request<PageResult<ExecutionLog>>({ url: '/admin/agent-logs', params })
}
