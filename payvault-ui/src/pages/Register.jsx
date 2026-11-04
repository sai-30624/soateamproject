import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { errorMessage } from '../api.js'
import AuthShell from './AuthShell.jsx'

export default function Register() {
  const { register, isLoggedIn } = useAuth()
  const nav = useNavigate()
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (isLoggedIn) return <Navigate to="/" replace />
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      await register(form)
      nav('/')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Create your wallet" subtitle="It takes less than a minute.">
      <form onSubmit={submit} className="form">
        <label>Full name<input required maxLength={100} value={form.fullName} onChange={set('fullName')} /></label>
        <label>Email<input type="email" required value={form.email} onChange={set('email')} /></label>
        <label>Phone (10 digits)<input required pattern="[0-9]{10}" inputMode="numeric" value={form.phone} onChange={set('phone')} /></label>
        <label>Password (min 6)<input type="password" required minLength={6} value={form.password} onChange={set('password')} /></label>
        {error && <div className="alert bad">{error}</div>}
        <button disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
      </form>
      <p className="muted small">Already registered? <Link to="/login">Log in</Link></p>
    </AuthShell>
  )
}
