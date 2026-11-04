import { useRef, useState } from 'react'
import api, { errorMessage, newIdempotencyKey } from '../api.js'
import { money } from '../format.js'

export default function Transfer() {
  const [query, setQuery] = useState('')
  const [receiver, setReceiver] = useState(null)
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const key = useRef(newIdempotencyKey())

  const find = async (e) => {
    e.preventDefault()
    setError(''); setReceiver(null); setResult(null)
    try {
      const { data } = await api.get('/users/lookup', { params: { query } })
      setReceiver(data)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const send = async () => {
    setBusy(true); setError('')
    try {
      const res = await api.post('/transactions/transfer',
        { receiverId: receiver.id, amount: Number(amount), note: note || null },
        { headers: { 'Idempotency-Key': key.current } })
      setResult(res.data)
    } catch (err) {
      if (err.response?.data?.txnRef) setResult(err.response.data)
      else setError(errorMessage(err))
    } finally {
      setBusy(false)
      setConfirming(false)
    }
  }

  const reset = () => {
    setReceiver(null); setAmount(''); setNote(''); setResult(null); setQuery('')
    key.current = newIdempotencyKey()
  }

  if (result) {
    const tone = { SUCCESS: 'ok', FAILED: 'bad', REVERSED: 'warn' }[result.status] || 'info'
    const headline = {
      SUCCESS: `Sent ${money(result.amount)} to ${result.receiverName}`,
      FAILED: 'Transfer failed — no money moved',
      REVERSED: 'Transfer reversed — your money was refunded',
    }[result.status] || `Transfer is ${result.status} — it will complete automatically`
    return (
      <>
        <header className="page-head"><h1>Send money</h1></header>
        <section className="card narrow result">
          <div className={`alert ${tone}`}>
            <strong>{headline}</strong>
            {result.failureReason && <div>{result.failureReason}</div>}
          </div>
          <dl className="kv">
            <dt>Reference</dt><dd className="mono">{result.txnRef}</dd>
            <dt>Amount</dt><dd>{money(result.amount)}</dd>
            <dt>Status</dt><dd>{result.status}</dd>
          </dl>
          <button onClick={reset}>Make another transfer</button>
        </section>
      </>
    )
  }

  return (
    <>
      <header className="page-head"><h1>Send money</h1></header>
      <section className="card narrow">
        <form onSubmit={find} className="form inline">
          <label>Receiver phone or email
            <input required value={query} onChange={(e) => setQuery(e.target.value)} placeholder="9876543210" />
          </label>
          <button className="ghost">Find</button>
        </form>

        {receiver && (
          <div className="receiver">
            <div className="avatar">{receiver.fullName[0]}</div>
            <div>
              <div className="t-title">{receiver.fullName}</div>
              <div className="muted small">{receiver.maskedPhone}</div>
            </div>
          </div>
        )}

        {receiver && (
          <form className="form" onSubmit={(e) => { e.preventDefault(); setConfirming(true) }}>
            <label>Amount (₹)
              <input type="number" min="1" max="50000" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} />
            </label>
            <label>Note (optional)
              <input maxLength={140} value={note} onChange={(e) => setNote(e.target.value)} />
            </label>
            <button>Continue</button>
          </form>
        )}
        {error && <div className="alert bad">{error}</div>}
      </section>

      {confirming && (
        <div className="modal-bg" onClick={() => !busy && setConfirming(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2>Confirm transfer</h2>
            <p>Send <strong>{money(amount)}</strong> to <strong>{receiver.fullName}</strong>?</p>
            <div className="actions">
              <button className="ghost" disabled={busy} onClick={() => setConfirming(false)}>Cancel</button>
              <button disabled={busy} onClick={send}>{busy ? 'Sending…' : 'Send now'}</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
