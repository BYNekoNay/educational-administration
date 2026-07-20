import axios from 'axios'

export function normalizeProtectedFileUrl(url: string) {
  return url.startsWith('/files/') ? `/api${url}` : url
}

export async function openProtectedFile(url: string) {
  const popup = window.open('', '_blank')
  try {
    const token = localStorage.getItem('token') || ''
    const response = await axios.get(normalizeProtectedFileUrl(url), {
      responseType: 'blob',
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    const objectUrl = URL.createObjectURL(response.data)
    if (popup) popup.location.href = objectUrl
    else window.open(objectUrl, '_blank')
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000)
  } catch (error) {
    popup?.close()
    throw error
  }
}
