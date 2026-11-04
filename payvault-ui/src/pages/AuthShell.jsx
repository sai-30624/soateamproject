export default function AuthShell({ title, subtitle, children }) {
  return (
    <div className="auth">
      <div className="auth-art">
        <div className="brand big">
          <span className="brand-mark">PV</span>
          <span>PayVault</span>
        </div>
        <p className="tagline">A fault-tolerant wallet where every rupee moved is written to the ledger — balance before, balance after.</p>
        <div className="vault-lines" aria-hidden="true">
          <span>DEBIT&nbsp;&nbsp;150.00&nbsp;&nbsp;500.00 → 350.00</span>
          <span>CREDIT&nbsp;150.00&nbsp;&nbsp;&nbsp;20.00 → 170.00</span>
          <span>STATUS SUCCESS</span>
        </div>
      </div>
      <div className="auth-card">
        <h1>{title}</h1>
        <p className="muted">{subtitle}</p>
        {children}
      </div>
    </div>
  )
}
