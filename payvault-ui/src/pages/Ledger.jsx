import { useEffect, useState } from 'react'
import api, { errorMessage } from '../api.js'
import { money, when } from '../format.js'
import Pager from '../components/Pager.jsx'

export default function Ledger() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [check, setCheck] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/wallets/me/ledger', { params: { page, size: 20 } })
      .then((r) => setData(r.data))
      .catch((e) => setError(errorMessage(e)))
  }, [page])

  useEffect(() => {
    api.get('/wallets/me/reconcile').then((r) => setCheck(r.data)).catch(() => {})
  }, [])

  return (
    <>
      <header className="page-head">
        <h1>Ledger statement</h1>
        {check && (
          <span className={`badge ${check.consistent ? 'ok' : 'bad'}`} title="Wallet balance vs. sum of all ledger entries">
            {check.consistent ? `Reconciled · ${check.entries} entries` : 'Mismatch!'}
          </span>
        )}
      </header>
      {error && <div className="alert bad">{error}</div>}
      <section className="card">
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Date</th><th>Reference</th><th>Type</th><th className="r">Amount</th><th className="r">Before</th><th className="r">After</th></tr>
            </thead>
            <tbody>
              {data?.content?.map((e) => (
                <tr key={e.id}>
                  <td>{when(e.createdAt)}</td>
                  <td className="mono">{e.txnRef}</td>
                  <td><span className={`badge ${e.entryType === 'CREDIT' ? 'ok' : 'bad'}`}>{e.entryType}</span></td>
                  <td className="r">{money(e.amount)}</td>
                  <td className="r muted">{money(e.balanceBefore)}</td>
                  <td className="r"><strong>{money(e.balanceAfter)}</strong></td>
                </tr>
              ))}
              {data && !data.content.length && <tr><td colSpan={6} className="muted">No ledger entries yet.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />
      </section>
    </>
  )
}
