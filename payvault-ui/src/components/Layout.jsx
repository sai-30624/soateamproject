import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

const links = [
  ['/', 'Overview'],
  ['/deposit', 'Add money'],
  ['/transfer', 'Send'],
  ['/transactions', 'History'],
  ['/ledger', 'Ledger'],
  ['/profile', 'Profile'],
]

export default function Layout() {
  const { user, logout } = useAuth()
  return (
    <div className="shell">
      <aside className="side">
        <div className="brand">
          <span className="brand-mark">PV</span>
          <span>PayVault</span>
        </div>
        <nav>
          {links.map(([to, label]) => (
            <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="side-foot">
          <div className="who">{user?.fullName || '...'}</div>
          <button className="link" onClick={logout}>Log out</button>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
