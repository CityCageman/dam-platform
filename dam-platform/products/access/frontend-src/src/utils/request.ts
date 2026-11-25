import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['token'] = token
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        localStorage.removeItem('token')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    const data = error?.response?.data
    const msg = (typeof data === 'string' && data)
      ? (data.length > 200 ? data.slice(0, 200) : data)
      : (data?.message || error.message || '网络错误')
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  }
)

export default request
