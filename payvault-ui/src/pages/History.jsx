import { useEffect, useState } from 'react'
import api, { errorMessage } from '../api.js'
import TxnList from '../components/TxnList.jsx'
import Pager from '../components/Pager.jsx'

export default function History() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/transactions', { params: { page, size: 15 } })
      .then((r) => setData(r.data))
      .catch((e) => setError(errorMessage(e)))
  }, [page])

  return (
    <>
      <header className="page-head"><h1>Transaction history</h1></header>
      {error && <div className="alert bad">{error}</div>}
      <section className="card">
        <TxnList items={data?.content} />
        <Pager page={page} totalPages={data?.totalPages} onChange={setPage} />
      </section>
    </>
  )
}
