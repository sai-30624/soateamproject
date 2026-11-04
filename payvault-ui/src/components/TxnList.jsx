import { money, statusClass, when } from '../format.js'

export default function TxnList({ items, empty = 'No transactions yet.' }) {
  if (!items?.length) return <p className="muted">{empty}</p>
  return (
    <ul className="txns">
      {items.map((t) => {
        const incoming = t.direction !== 'SENT'
        const title =
          t.direction === 'DEPOSIT' ? 'Added to wallet'
            : t.direction === 'SENT' ? `To ${t.receiverName || 'user #' + t.receiverId}`
            : `From ${t.senderName || 'user #' + t.senderId}`
        return (
          <li key={t.txnRef}>
            <div>
              <div className="t-title">{title}</div>
              <div className="t-sub">
                <span className="mono">{t.txnRef}</span> · {when(t.createdAt)}
                {t.failureReason && <span className="t-reason"> · {t.failureReason}</span>}
              </div>
            </div>
            <div className="t-right">
              <div className={`amt ${incoming ? 'in' : 'out'}`}>{incoming ? '+' : '−'}{money(t.amount)}</div>
              <span className={`badge ${statusClass(t.status)}`}>{t.status}</span>
            </div>
          </li>
        )
      })}
    </ul>
  )
}
