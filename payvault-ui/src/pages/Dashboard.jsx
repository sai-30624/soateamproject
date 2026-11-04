import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { errorMessage } from '../api.js'
import { money, when } from '../format.js'
import { useAuth } from '../context/AuthContext.jsx'
import TxnList from '../components/TxnList.jsx'

export default function Dashboard() {
  const { user } = useAuth()
  const [wallet, setWallet] = useState(null)
  const [recent, setRecent] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([api.get('/wallets/me'), api.get('/transactions', { params: { size: 5 } })])
      .then(([w, t]) => { setWallet(w.data); setRecent(t.data.content) })
      .catch((e) => setError(errorMessage(e)))
  }, [])

  return (
    <>
      <header className="page-head">
        <h1>Hi{user ? `, ${user.fullName.split(' ')[0]}` : ''}</h1>
      </header>
      {error && <div className="alert bad">{error}</div>}
      <section className="balance">
        <div className="label">Available balance</div>
        <div className="value">{wallet ? money(wallet.balance) : '—'}</div>
        <div className="muted small">{wallet && `Updated ${when(wallet.updatedAt)}`}</div>
        <div className="actions">
          <Link className="btn" to="/deposit">Add money</Link>
          <Link className="btn ghost" to="/transfer">Send money</Link>
        </div>
      </section>
      <section className="card">
        <div className="card-head">
          <h2>Recent activity</h2>
          <Link to="/transactions" className="small">See all</Link>
        </div>
        <TxnList items={recent} />
      </section>
    </>
  )
}
