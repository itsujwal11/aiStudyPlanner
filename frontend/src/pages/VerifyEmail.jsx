import React, { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AlertCircle, Mail, ShieldCheck } from 'lucide-react'
import { motion } from 'framer-motion'
import { authAPI } from '../api'
import { useAuth } from '../context/AuthContext'

const errorMessage = (error, fallback) =>
  typeof error.response?.data === 'string'
    ? error.response.data
    : error.response?.data?.message || fallback

export const VerifyEmail = () => {
  const location = useLocation()
  const navigate = useNavigate()
  const { login } = useAuth()
  const [email, setEmail] = useState(location.state?.email || '')
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState(location.state?.message || '')
  const [loading, setLoading] = useState(false)

  const verify = async (event) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const response = await authAPI.verifyEmail({ email, code })
      login(response.data, response.data.token)
      navigate('/dashboard')
    } catch (err) {
      setError(errorMessage(err, 'Verification failed'))
    } finally {
      setLoading(false)
    }
  }

  const resend = async () => {
    setError('')
    setMessage('')
    try {
      const response = await authAPI.resendVerification({ email })
      setMessage(response.data.message)
    } catch (err) {
      setError(errorMessage(err, 'Could not resend the code'))
    }
  }

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4">
      <div className="mesh-bg" />
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
        className="glass-pane rounded-xl p-8 border border-black/8 max-w-sm w-full">
        <ShieldCheck className="w-11 h-11 text-primary mx-auto mb-4" />
        <h1 className="text-xl text-on-surface font-bold text-center">Verify your email</h1>
        <p className="text-sm text-on-surface-variant/70 mt-1 mb-6 text-center">
          Enter the six-digit code sent to your inbox.
        </p>
        {error && <div className="auth-error"><AlertCircle className="w-5 h-5" /><p>{error}</p></div>}
        {message && <div className="auth-success">{message}</div>}
        <form onSubmit={verify} className="space-y-5">
          <div className="relative">
            <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50" />
            <input className="input-glass pl-10 pr-4" type="email" value={email}
              onChange={(event) => setEmail(event.target.value)} placeholder="Email address" required />
          </div>
          <input className="input-glass px-4 text-center tracking-[0.45em] text-lg" value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
            inputMode="numeric" autoComplete="one-time-code" placeholder="000000"
            pattern="\d{6}" required autoFocus />
          <button className="btn-glass-primary w-full py-2.5" disabled={loading}>
            {loading ? 'Verifying...' : 'Verify email'}
          </button>
        </form>
        <button type="button" onClick={resend} disabled={!email}
          className="w-full text-sm text-primary font-medium mt-4 disabled:opacity-50">
          Send a new code
        </button>
        <p className="text-center text-sm mt-5"><Link to="/login" className="text-primary">Back to sign in</Link></p>
      </motion.div>
    </div>
  )
}
