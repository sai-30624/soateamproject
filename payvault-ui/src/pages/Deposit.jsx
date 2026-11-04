import { useRef, useState } from 'react'
import api, { errorMessage, newIdempotencyKey } from '../api.js'
import { money } from '../format.js'

export default function Deposit() {
  const [amount, setAmount] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  // One key per deposit attempt - a double click or retry reuses it, so money is added once.
  const key = useRef(newIdempotencyKey())

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError(''); setResult(null)
    try {
      const { data } = await api.post('/transactions/deposit', { amount: Number(amount) },
        { headers: { 'Idempotency-Key': key.current } })
      setResult(data)
      setAmount('')
      key.current = newIdempotencyKey()
    } catch (err) {
      setError(errorMessage(err))
      if (err.response?.status === 422) key.current = newIdempotencyKey()
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <header className="page-head"><h1>Add money</h1></header>
      <section className="card narrow">
        <form onSubmit={submit} className="form">
          <label>Amount (₹)
            <input type="number" min="1" max="100000" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} />
          </label>
          <div className="chips">
            {[500, 1000, 2000, 5000].map((v) => (
              <button type="button" key={v} className="chip" onClick={() => setAmount(String(v))}>{money(v)}</button>
            ))}
          </div>
          {error && <div className="alert bad">{error}</div>}
          {result && (
            <div className={`alert ${result.status === 'SUCCESS' ? 'ok' : 'info'}`}>
              {result.status === 'SUCCESS' ? `${money(result.amount)} added.` : `Deposit is ${result.status}. ${result.failureReason || ''}`}
              <div className="mono small">{result.txnRef}</div>
            </div>
          )}
          <button disabled={busy}>{busy ? 'Adding…' : 'Add money'}</button>
        </form>
        <p className="muted small">Demo top-up: no real payment gateway is involved.</p>
      </section>
    </>
  )
}
