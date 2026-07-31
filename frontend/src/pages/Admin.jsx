import React, { useState, useEffect } from 'react'
import { adminAPI, authAPI } from '../api'
import { useAuth } from '../context/AuthContext'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Database, ChevronRight, Trash2, ArrowLeft, Table2, Shield, RefreshCw } from 'lucide-react'

const TYPE_COLORS = {
  String: 'text-emerald-600 bg-emerald-50',
  Long: 'text-blue-600 bg-blue-50',
  Integer: 'text-blue-600 bg-blue-50',
  Double: 'text-violet-600 bg-violet-50',
  Boolean: 'text-orange-600 bg-orange-50',
  LocalDateTime: 'text-cyan-600 bg-cyan-50',
  LocalDate: 'text-cyan-600 bg-cyan-50',
}

function isDateLike(name, type) {
  return type === 'LocalDateTime' || type === 'LocalDate' || name.toLowerCase().includes('date') || name.toLowerCase().includes('time')
}

function formatValue(val, colName, colType) {
  if (val === null || val === undefined) return { text: 'NULL', classes: 'text-on-surface-variant/30 italic' }
  if (typeof val === 'boolean') return { text: String(val), classes: val ? 'text-emerald-600 font-medium' : 'text-on-surface-variant/50' }
  if (isDateLike(colName, colType) && typeof val === 'string') {
    try {
      return { text: new Date(val).toLocaleString(), classes: 'text-cyan-700 text-xs' }
    } catch {
      // Fall through to the raw value when a date cannot be formatted.
    }
  }
  let text = String(val)
  if (text.length > 60) text = text.substring(0, 60) + '...'
  if (colType === 'Double' || colType === 'Float') text = parseFloat(val).toFixed(2)
  return { text, classes: '' }
}

export const Admin = () => {
  const { isAdmin, loading: authLoading } = useAuth()
  const navigate = useNavigate()
  const [entities, setEntities] = useState(null)
  const [selectedEntity, setSelectedEntity] = useState(null)
  const [records, setRecords] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (authLoading) return
    if (!isAdmin) { navigate('/dashboard'); return }
    fetchEntities()
  }, [isAdmin, authLoading])

  const fetchEntities = async () => {
    setLoading(true)
    try {
      const res = await adminAPI.listEntities()
      setEntities(res.data)
      setError('')
    } catch (err) {
      const msg = err.response?.data?.error || err.message || 'Failed to load'
      setError(msg)
    } finally { setLoading(false) }
  }

  const fetchRecords = async (entityName) => {
    setLoading(true)
    setSelectedEntity(entityName)
    setRecords(null)
    setError('')
    try {
      const res = await adminAPI.listRecords(entityName, 200)
      setRecords(res.data)
    } catch (err) {
      const msg = err.response?.data?.error || err.message || 'Failed to load records'
      setError(msg)
    } finally { setLoading(false) }
  }

  const handleDelete = async (entityName, id) => {
    if (!window.confirm(`Delete ${entityName} #${id}?`)) return
    try {
      await adminAPI.deleteRecord(entityName, id)
      toast.success(`Deleted #${id}`)
      setRecords(prev => prev ? prev.filter(r => r.id !== id) : prev)
    } catch (err) {
      toast.error(err.response?.data?.error || 'Delete failed')
    }
  }

  const handleBack = () => {
    setSelectedEntity(null)
    setRecords(null)
    setError('')
    fetchEntities()
  }

  const seedAdmin = async () => {
    try {
      await authAPI.seedAdmin()
      toast.success('Admin seeded')
      fetchEntities()
    } catch (err) {
      toast.error(err.response?.data || 'Seed failed')
    }
  }

  if (authLoading) return null

  /* ── Record Table View ── */
  if (selectedEntity) {
    const entity = entities?.find(e => e.name === selectedEntity)
    const columns = entity?.fields?.filter(f => f.name !== 'id') || []

    return (
      <div className="space-y-6 animate-fade-in">
        <div className="flex items-start justify-between">
          <div>
            <button onClick={() => navigate('/dashboard')} className="flex items-center gap-2 text-on-surface-variant/70 hover:text-primary mb-4 transition-colors text-sm">
              <ArrowLeft className="w-4 h-4" /> Back to Dashboard
            </button>
            <button onClick={handleBack} className="flex items-center gap-1 text-sm text-primary hover:text-primary/80 mb-3">
              <ArrowLeft className="w-4 h-4" /> Back to tables
            </button>
            <h1 className="text-2xl font-bold text-on-surface">{entity?.tableName || selectedEntity}</h1>
            <p className="text-sm text-on-surface-variant/70">
              {records ? records.length : 0} records &middot; {columns.length + 1} columns
            </p>
          </div>
          <button onClick={() => fetchRecords(selectedEntity)} disabled={loading}
            className="btn-glass-secondary text-sm p-2.5 disabled:opacity-50" title="Refresh">
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>

        {error && (
          <div className="glass-pane rounded-xl p-4 border border-black/8 bg-red-50/80 border border-red-200/50 text-red-700 text-sm">{error}</div>
        )}

        {loading ? (
          <div className="flex justify-center py-16 text-on-surface-variant">Loading...</div>
        ) : (
          <div className="glass-pane rounded-xl overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr>
                    <th className="sticky left-0 z-10 bg-white/60 text-left py-3 px-4 text-on-surface-variant/50 font-semibold text-xs uppercase tracking-wider border-r border-b border-white/20" style={{width: '64px', minWidth: '64px'}}>ID</th>
                    {columns.map(col => {
                      const isLong = col.type === 'String' || col.type === 'LocalDateTime' || col.type === 'LocalDate'
                      const isNumeric = col.type === 'Long' || col.type === 'Integer' || col.type === 'Double' || col.type === 'Float'
                      return (
                          <th key={col.name} className={`text-left py-3 px-4 text-on-surface-variant/50 font-semibold text-xs uppercase tracking-wider whitespace-nowrap border-r border-b border-white/20 bg-white/60 ${isNumeric ? 'text-right' : ''}`}
                          style={{minWidth: isLong ? '140px' : '100px'}}>
                          <div className="flex items-center gap-2">
                            <span>{col.name.replace(/([A-Z])/g, ' $1').trim()}</span>
                            <span className={`text-[10px] px-1.5 py-0.5 rounded font-normal ${TYPE_COLORS[col.type] || 'text-gray-500 bg-gray-100'}`}>
                              {col.type === 'LocalDateTime' ? 'date' : col.type.toLowerCase()}
                            </span>
                          </div>
                        </th>
                      )
                    })}
                    <th className="text-center py-3 px-3 text-on-surface-variant/50 font-semibold text-xs uppercase tracking-wider bg-white/60 border-b border-white/20" style={{width: '56px', minWidth: '56px'}}>Del</th>
                  </tr>
                </thead>
                <tbody>
                  {!records || records.length === 0 ? (
                    <tr>
                      <td colSpan={columns.length + 2} className="text-center py-12 text-on-surface-variant/40 text-sm border-b border-white/20">
                        No records in this table
                      </td>
                    </tr>
                  ) : records.map((record, i) => (
                    <tr key={record.id || i} className={`${i % 2 === 0 ? 'bg-white/40' : 'bg-white/10'} hover:bg-primary/[0.04] transition-all`}>
                      <td className="sticky left-0 z-10 bg-white/50 text-center py-2.5 px-1 border-r border-b border-white/20">
                        <span className="inline-flex items-center justify-center w-7 h-6 rounded bg-white/70 text-[11px] font-bold text-on-surface-variant/60">{record.id ?? '?'}</span>
                      </td>
                      {columns.map(col => {
                        const { text, classes } = formatValue(record[col.name], col.name, col.type)
                        const isNumeric = col.type === 'Long' || col.type === 'Integer' || col.type === 'Double' || col.type === 'Float'
                        return (
                          <td key={col.name} className={`py-2.5 px-4 border-r border-b border-white/20 ${isNumeric ? 'text-right' : ''}`}
                            title={record[col.name] === null ? '' : String(record[col.name])}>
                            <div className="flex items-center gap-2 overflow-hidden">
                              <span className={`text-xs ${classes} truncate block`}>{text}</span>
                            </div>
                          </td>
                        )
                      })}
                      <td className="py-2.5 px-1 text-center border-b border-white/20">
                        <button onClick={() => handleDelete(selectedEntity, record.id)}
                          className="p-1 rounded-md text-on-surface-variant/30 hover:text-error hover:bg-error/10 transition-all" title="Delete record">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    )
  }

  /* ── Entity Grid View ── */
  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center">
            <Database className="w-5 h-5 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-on-surface">Database Admin</h1>
            <p className="text-sm text-on-surface-variant/70">View and manage database tables</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={fetchEntities} disabled={loading} className="btn-glass-secondary text-sm p-2.5 disabled:opacity-50" title="Refresh">
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          <button onClick={seedAdmin} className="btn-glass-secondary text-sm flex items-center gap-2">
            <Shield className="w-4 h-4" /> Seed Admin
          </button>
        </div>
      </div>

      {error && (
        <div className="glass-pane rounded-xl p-4 border border-black/8 bg-red-50/80 border border-red-200/50 text-red-700 text-sm">{error}</div>
      )}

      {loading ? (
        <div className="flex justify-center py-16 text-on-surface-variant">Loading tables...</div>
      ) : !entities || entities.length === 0 ? (
        <div className="glass-pane rounded-xl p-16 border border-black/8 text-center">
          <Database className="w-12 h-12 mx-auto mb-3 text-on-surface-variant/20" />
          <p className="text-on-surface-variant/50">No tables found</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {entities.map(entity => (
            <button key={entity.name} onClick={() => fetchRecords(entity.name)}
              className="glass-pane rounded-xl p-5 border border-black/8 text-left hover:bg-white/85 transition-all group">
              <div className="flex items-start justify-between mb-3">
                <div className="w-9 h-9 rounded-lg bg-primary/10 flex items-center justify-center">
                  <Table2 className="w-4 h-4 text-primary" />
                </div>
                <ChevronRight className="w-4 h-4 text-on-surface-variant/30 group-hover:text-primary transition-all" />
              </div>
              <h3 className="font-semibold text-on-surface mb-1">{entity.tableName}</h3>
              <div className="flex items-center gap-2 text-xs text-on-surface-variant/60">
                <span className="font-medium text-on-surface-variant/80">{entity.rowCount}</span> records
                <span>&middot;</span>
                <span className="font-medium text-on-surface-variant/80">{entity.fields?.length || 0}</span> columns
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
