// SSE 流式请求（POST + fetch 流式读取）
// 由于 EventSource 只支持 GET，这里用 fetch + ReadableStream 实现 POST 流式对话，
// 并携带 token 请求头。服务端 SSE 事件约定：
//   data: {"reasoning":"思考文本"}
//   data: {"delta":"文本片段"}
//   data: {"done":true}
//   data: {"error":"错误信息"}

export interface SseCallbacks {
  onReasoning?: (delta: string) => void
  onDelta?: (delta: string) => void
  onDone?: () => void
  onError?: (message: string) => void
}

export async function streamPost(
  url: string,
  body: unknown,
  callbacks: SseCallbacks,
  signal?: AbortSignal
): Promise<void> {
  const token = localStorage.getItem('token') || ''
  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      token
    },
    body: JSON.stringify(body),
    signal
  })

  // 非 2xx（如网关 401）
  if (!resp.ok) {
    let msg = `请求失败(${resp.status})`
    try {
      const err = await resp.json()
      if (err?.message) msg = err.message
      else if (err?.msg) msg = err.msg
    } catch (_e) {
      /* 忽略 */
    }
    throw new Error(msg)
  }

  // 后端可能以 JSON 返回错误（如登录失效、参数校验失败），而非 SSE 流
  const contentType = resp.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    let msg = '请求失败'
    try {
      const err = await resp.json()
      if (err?.message) msg = err.message
    } catch (_e) {
      /* 忽略 */
    }
    throw new Error(msg)
  }

  const reader = resp.body?.getReader()
  if (!reader) throw new Error('浏览器不支持流式读取')

  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const processLine = (line: string) => {
    const data = line.trim()
    if (!data.startsWith('data:')) return
    const payload = data.substring(5).trim()
    if (!payload) return
    try {
      const json = JSON.parse(payload)
      if (json.reasoning) callbacks.onReasoning?.(json.reasoning)
      if (json.delta) callbacks.onDelta?.(json.delta)
      if (json.done) callbacks.onDone?.()
      if (json.error) callbacks.onError?.(json.error)
    } catch (_e) {
      /* 忽略无法解析的行 */
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    lines.forEach(processLine)
  }
  if (buffer.trim()) processLine(buffer)
}
