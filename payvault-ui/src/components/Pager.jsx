export default function Pager({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null
  return (
    <div className="pager">
      <button className="ghost" disabled={page === 0} onClick={() => onChange(page - 1)}>Previous</button>
      <span className="muted">Page {page + 1} of {totalPages}</span>
      <button className="ghost" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
    </div>
  )
}
