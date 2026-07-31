import React, { useState } from 'react'
import { useNavigate, Link, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { authAPI } from '../api'
import { Mail, Lock, AlertCircle, Eye, EyeOff } from 'lucide-react'
import { motion } from 'framer-motion'
import { GoogleSignInButton } from '../components/GoogleSignInButton'

const getError = (error, fallback) =>
  typeof error.response?.data === 'string'
    ? error.response.data
    : error.response?.data?.message || fallback

export const Login = () => {
  const [formData, setFormData] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const finishLogin = (data) => {
    login(data, data.token)
    navigate(data.role === 'ADMIN' ? '/admin' : '/dashboard')
  }

  const handleChange = (event) => {
    setFormData({ ...formData, [event.target.name]: event.target.value })
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const response = await authAPI.login(formData)
      finishLogin(response.data)
    } catch (err) {
      if (err.response?.data?.error === 'EMAIL_NOT_VERIFIED') {
        navigate('/verify-email', { state: { email: formData.email, message: err.response.data.message } })
        return
      }
      setError(getError(err, 'Login failed'))
    } finally {
      setLoading(false)
    }
  }

  const handleGoogle = async (credential) => {
    setError('')
    setLoading(true)
    try {
      const response = await authAPI.google({ credential })
      finishLogin(response.data)
    } catch (err) {
      setError(getError(err, 'Google Sign-In failed'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4">
      <div className="mesh-bg" />
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="glass-pane rounded-xl p-8 border border-black/8 max-w-sm w-full">
        <div className="text-center mb-8">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary to-primary-container flex items-center justify-center mx-auto mb-4 shadow-sm">
            <span className="text-white text-lg font-bold">AI</span>
          </div>
          <h1 className="text-xl text-on-surface font-bold">Welcome back</h1>
          <p className="text-sm text-on-surface-variant/70 mt-1">Sign in to your AASA account</p>
        </div>

        {location.state?.message && <div className="auth-success">{location.state.message}</div>}
        {error && <div className="auth-error"><AlertCircle className="w-5 h-5" /><p>{error}</p></div>}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-sm font-medium text-on-surface-variant/80 mb-1.5">Email</label>
            <div className="relative">
              <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50" />
              <input type="email" name="email" value={formData.email} onChange={handleChange}
                className="input-glass pl-10 pr-4" placeholder="Enter your email address"
                autoComplete="email" autoFocus required />
            </div>
          </div>
          <div>
            <div className="flex justify-between mb-1.5">
              <label className="text-sm font-medium text-on-surface-variant/80">Password</label>
              <Link to="/forgot-password" className="text-xs text-primary font-medium">Forgot password?</Link>
            </div>
            <div className="relative">
              <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50" />
              <input type={showPassword ? 'text' : 'password'} name="password"
                value={formData.password} onChange={handleChange} className="input-glass pl-10 pr-10"
                placeholder="Enter your password" autoComplete="current-password" required />
              <button type="button" onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-on-surface-variant/50">
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>
          <button type="submit" disabled={loading}
            className="btn-glass-primary w-full py-2.5 disabled:opacity-50">
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <div className="auth-divider"><span>or</span></div>
        <GoogleSignInButton onCredential={handleGoogle} onError={setError} />
        <p className="text-center text-sm text-on-surface-variant/70 mt-6">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="text-primary font-medium">Create one</Link>
        </p>
      </motion.div>
    </div>
  )
}
