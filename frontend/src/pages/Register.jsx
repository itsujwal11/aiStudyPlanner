import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { authAPI } from '../api'
import { Mail, Lock, User, AlertCircle, Eye, EyeOff } from 'lucide-react'
import { motion } from 'framer-motion'
import { GoogleSignInButton } from '../components/GoogleSignInButton'

const getError = (error, fallback) =>
  typeof error.response?.data === 'string'
    ? error.response.data
    : error.response?.data?.message || fallback

export const Register = () => {
  const [formData, setFormData] = useState({ email: '', password: '', name: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleChange = (event) => {
    setFormData({ ...formData, [event.target.name]: event.target.value })
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const response = await authAPI.register(formData)
      navigate('/verify-email', { state: {
        email: response.data.email || formData.email,
        message: response.data.message,
      } })
    } catch (err) {
      setError(getError(err, 'Registration failed'))
    } finally {
      setLoading(false)
    }
  }

  const handleGoogle = async (credential) => {
    setError('')
    setLoading(true)
    try {
      const response = await authAPI.google({ credential })
      login(response.data, response.data.token)
      navigate('/dashboard')
    } catch (err) {
      setError(getError(err, 'Google Sign-In failed'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4 py-8">
      <div className="mesh-bg" />
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
        className="glass-pane rounded-xl p-8 border border-black/8 max-w-sm w-full">
        <div className="text-center mb-7">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary to-primary-container flex items-center justify-center mx-auto mb-4">
            <span className="text-white text-lg font-bold">AI</span>
          </div>
          <h1 className="text-xl text-on-surface font-bold">Create account</h1>
          <p className="text-sm text-on-surface-variant/70 mt-1">Get started with AASA</p>
        </div>
        {error && <div className="auth-error"><AlertCircle className="w-5 h-5" /><p>{error}</p></div>}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="relative">
            <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50" />
            <input type="text" name="name" value={formData.name} onChange={handleChange}
              className="input-glass pl-10 pr-4" placeholder="Full name" autoComplete="name" required />
          </div>
          <div className="relative">
            <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50" />
            <input type="email" name="email" value={formData.email} onChange={handleChange}
              className="input-glass pl-10 pr-4" placeholder="Email address" autoComplete="email" required />
          </div>
          <div className="relative">
            <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50" />
            <input type={showPassword ? 'text' : 'password'} name="password"
              value={formData.password} onChange={handleChange} className="input-glass pl-10 pr-10"
              placeholder="Password" minLength="8" autoComplete="new-password" required />
            <button type="button" onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-on-surface-variant/50">
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
          <p className="text-xs text-on-surface-variant/60">Use uppercase, lowercase, and at least one number.</p>
          <button type="submit" disabled={loading} className="btn-glass-primary w-full py-2.5 disabled:opacity-50">
            {loading ? 'Creating account...' : 'Create account'}
          </button>
        </form>
        <div className="auth-divider"><span>or</span></div>
        <GoogleSignInButton onCredential={handleGoogle} onError={setError} />
        <p className="text-center text-sm text-on-surface-variant/70 mt-6">
          Already have an account? <Link to="/login" className="text-primary font-medium">Sign in</Link>
        </p>
      </motion.div>
    </div>
  )
}
