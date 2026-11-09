import { JSEncrypt } from 'jsencrypt'
import request from './request'

/**
 * 获取指定服务的 RSA 公钥（登录用 auth，数据源操作用 catalog）。
 * 公钥由后端服务启动时生成；服务重启后会变化，因此不缓存，避免旧公钥导致密码解密失败。
 */
export async function getPublicKey(service: 'auth' | 'catalog' = 'catalog'): Promise<string> {
  const url = service === 'auth' ? '/auth/public-key' : '/catalog/discovery/public-key'
  const res: any = await request.get(url)
  return res.data
}

/**
 * 使用指定服务的 RSA 公钥加密明文密码（空密码直接返回空串）
 * 加密结果在 HTTP 报文中也不可见明文
 */
export async function encryptPassword(plain: string, service: 'auth' | 'catalog' = 'catalog'): Promise<string> {
  if (!plain) return ''
  const publicKey = await getPublicKey(service)
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey)
  return encryptor.encrypt(plain) || ''
}
