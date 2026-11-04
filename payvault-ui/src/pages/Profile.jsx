import { useEffect, useState } from 'react'
import api, { errorMessage } from '../api.js'
import { useAuth } from '../context/AuthContext.jsx'

export default function Profile() {
  const { user, setUser, logout } = useAuth()
  const [form, setForm] = useState({ fullName: '', phone: '' })
  const [msg, setMsg] = useState(null)

  useEffect(() => {
    if (user) setForm({ fullName: user.fullName, phone: user.phone || '' })
  }, [user])

  const save = async (e) => {
    e.preventDefault()
    setMsg(null)
    try {
      const { data } = await api.put('/users/me', form)
      setUser(data)
      setMsg({ tone: 'ok', text: 'Profile updated.' })
    } catch (err) {
      setMsg({ tone: 'bad', text: errorMessage(err) })
    }
  }

  return (
    <>
      <header className="page-head"><h1>Profile</h1></header>
      <section className="card narrow">
        <dl className="kv">
          <dt>Email</dt><dd>{user?.email}</dd>
          <dt>User ID</dt><dd className="mono">{user?.id}</dd>
          <dt>Status</dt><dd>{user?.status}</dd>
        </dl>
        <form onSubmit={save} className="form">
          <label>Full name<input required value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></label>
          <label>Phone<input required pattern="[0-9]{10}" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
          {msg && <div className={`alert ${msg.tone}`}>{msg.text}</div>}
          <button>Save changes</button>
        </form>
        <button className="ghost full" onClick={logout}>Log out</button>
      </section>
    </>
  )
}
