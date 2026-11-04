const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })

export const money = (v) => inr.format(Number(v || 0))

export const when = (iso) =>
  iso ? new Date(iso).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : ''

export function statusClass(s) {
  return {
    SUCCESS: 'ok', REVERSED: 'warn', FAILED: 'bad',
    PENDING: 'info', DEBITED: 'info', REVERSING: 'warn',
  }[s] || 'info'
}
