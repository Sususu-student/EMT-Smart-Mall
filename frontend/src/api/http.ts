import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'sg_token'

/**
 * API 基址。
 * - 本地开发：留空走 '/api'，由 Vite 代理转发到 8080
 * - 线上部署：在构建环境里设 VITE_API_BASE，例如 https://api.example.com/api
 */
export const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined)?.replace(/\/$/, '') || '/api'

/**
 * 后端返回的资源地址（如上传后的 /uploads/xxx.jpg）是相对根路径的，
 * 部署到独立前端域名后需要补上后端源，否则会打到前端自己身上。
 */
export const API_ORIGIN = API_BASE.startsWith('http') ? API_BASE.replace(/\/api$/, '') : ''

/** 把后端返回的相对路径转成可访问的绝对地址 */
export function resolveAssetUrl(url?: string | null): string {
  if (!url) return ''
  if (/^(https?:)?\/\//.test(url) || url.startsWith('data:') || url.startsWith('blob:')) {
    return url
  }
  return API_ORIGIN ? `${API_ORIGIN}${url.startsWith('/') ? '' : '/'}${url}` : url
}

const http = axios.create({
  baseURL: API_BASE,
  timeout: 60000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const message =
      error.response?.data?.message ||
      (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请检查后端服务是否启动')
    if (status === 401 && localStorage.getItem(TOKEN_KEY)) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('sg_user')
      ElMessage.error('登录已过期，请重新登录')
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.assign(`/login?redirect=${redirect}`)
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  return (await http.request(config)) as unknown as T
}

/** 上传文件：走 multipart/form-data，超时放宽到 2 分钟 */
export async function upload<T>(url: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  return (await http.request({
    url,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })) as unknown as T
}

export { TOKEN_KEY }
