import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AlertCircle, Mail } from 'lucide-react'
import { motion } from 'framer-motion'
import { authAPI } from '../api'

export const ForgotPassword = () => {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const response = await authAPI.forgotPassword({ email })
      navigate('/reset-password', { state: { email, message: response.data.message } })
    } catch (err) {
      setError(err.response?.data?.message || 'Could not request a password reset')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4">
      <div className="mesh-bg" />
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
        className="glass-pane rounded-xl p-8 border border-black/8 max-w-sm w-full">
        <h1 className="text-xl text-on-surface font-bold text-center">Forgot your password?</h1>
        <p className="text-sm text-on-surface-variant/70 mt-1 mb-6 text-center">
          We’ll email you a six-digit reset code.
        </p>
        {error && <div className="auth-error"><AlertCircle className="w-5 h-5" /><p>{error}</p></div>}
        <form onSubmit={submit} className="space-y-5">
          <div className="relative">
            <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50" />
            <input className="input-glass pl-10 pr-4" type="email" value={email}
              onChange={(event) => setEmail(event.target.value)} placeholder="Email address" required autoFocus />
          </div>
          <button className="btn-glass-primary w-full py-2.5" disabled={loading}>
            {loading ? 'Sending...' : 'Send reset code'}
          </button>
        </form>
        <p className="text-center text-sm mt-6"><Link to="/login" className="text-primary">Back to sign in</Link></p>
      </motion.div>
    </div>
  )
}
