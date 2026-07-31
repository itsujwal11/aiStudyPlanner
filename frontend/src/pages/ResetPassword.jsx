import React, { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AlertCircle, Eye, EyeOff, Lock, Mail } from 'lucide-react'
import { motion } from 'framer-motion'
import { authAPI } from '../api'

export const ResetPassword = () => {
  const location = useLocation()
  const navigate = useNavigate()
  const [form, setForm] = useState({
    email: location.state?.email || '', code: '', newPassword: '',
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  
  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      await authAPI.resetPassword(form)
      navigate('/login', { state: { message: 'Password reset. You can now sign in.' } })
    } catch (err) {
      setError(err.response?.data?.message || 'Password reset failed')
    } finally {
      setLoading(false)
    }
  }

  const update = (event) => setForm({ ...form, [event.target.name]: event.target.value })

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4">
      <div className="mesh-bg" />
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
        className="glass-pane rounded-xl p-8 border border-black/8 max-w-sm w-full">
        <h1 className="text-xl text-on-surface font-bold text-center">Set a new password</h1>
        <p className="text-sm text-on-surface-variant/70 mt-1 mb-6 text-center">
          {location.state?.message || 'Enter the code from your email.'}
        </p>
        {error && <div className="auth-error"><AlertCircle className="w-5 h-5" /><p>{error}</p></div>}
        <form onSubmit={submit} className="space-y-4">
          <div className="relative">
            <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50" />
            <input className="input-glass pl-10 pr-4" name="email" type="email"
              value={form.email} onChange={update} placeholder="Email address" required />
          </div>
          <input className="input-glass px-4 text-center tracking-[0.45em] text-lg" name="code"
            value={form.code} onChange={(event) => setForm({
              ...form, code: event.target.value.replace(/\D/g, '').slice(0, 6),
            })} inputMode="numeric" autoComplete="one-time-code" placeholder="000000" pattern="\d{6}" required />
          <div className="relative">
            <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50" />
            <input className="input-glass pl-10 pr-10" name="newPassword"
              type={showPassword ? 'text' : 'password'} value={form.newPassword} onChange={update}
              placeholder="New password" minLength="8" required />
            <button type="button" onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3.5 top-1/2 -translate-y-1/2">
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
          <p className="text-xs text-on-surface-variant/60">Use uppercase, lowercase, and at least one number.</p>
          <button className="btn-glass-primary w-full py-2.5" disabled={loading}>
            {loading ? 'Resetting...' : 'Reset password'}
          </button>
        </form>
        <p className="text-center text-sm mt-6"><Link to="/login" className="text-primary">Back to sign in</Link></p>
      </motion.div>
    </div>
  )
}
