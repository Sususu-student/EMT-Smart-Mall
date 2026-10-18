import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'sg_token'

const http = axios.create({
  baseURL: '/api',
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

export { TOKEN_KEY }
