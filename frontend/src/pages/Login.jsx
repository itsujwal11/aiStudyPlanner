import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { authAPI } from '../api'
import { Mail, Lock, AlertCircle, Eye, EyeOff } from 'lucide-react'
import { motion } from 'framer-motion'

export const Login = () => {
  const [formData, setFormData] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [seeding, setSeeding] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const response = await authAPI.login(formData)
      login(response.data, response.data.token)
      if (response.data.role === 'ADMIN') navigate('/admin')
      else navigate('/dashboard')
    } catch (err) {
      const msg = typeof err.response?.data === 'string' ? err.response.data : err.response?.data?.message || 'Login failed'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  const handleSeedAdmin = async () => {
    setSeeding(true)
    try {
      await authAPI.seedAdmin()
      setFormData({ email: 'admin@aasa.com', password: 'admin123' })
      setError('')
    } catch (err) {
      const msg = err.response ? (typeof err.response.data === 'string' ? err.response.data : 'Seed failed') : 'Backend unreachable — ensure the backend is running on port 9096'
      setFormData({ email: 'admin@aasa.com', password: 'admin123' })
      setError(msg + ' (fields pre-filled, try signing in)')
    } finally { setSeeding(false) }
  }

  const handleDemoMode = () => {
    const demoUser = {
      userId: 1,
      email: 'demo@example.com',
      name: 'Demo User',
      role: 'USER',
      token: 'demo-token-12345'
    }
    login(demoUser, demoUser.token)
    navigate('/dashboard')
  }

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4">
      <div className="mesh-bg" />
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="glass-pane rounded-xl p-8 border border-black/8 max-w-sm w-full"
      >
        <div className="text-center mb-8">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary to-primary-container flex items-center justify-center mx-auto mb-4 shadow-sm">
            <span className="text-white text-lg font-bold">AI</span>
          </div>
          <h1 className="text-xl text-on-surface font-bold">Welcome back</h1>
          <p className="text-sm text-on-surface-variant/70 mt-1">Sign in to your AASA account</p>
        </div>

        {error && (
          <div className="flex items-start gap-3 glass-pane rounded-xl p-4 border border-black/8 bg-red-50/80 border border-red-200/50 mb-6">
            <AlertCircle className="w-5 h-5 text-red-700 mt-0.5 flex-shrink-0" />
            <p className="text-red-700 text-sm">{error}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-sm font-medium text-on-surface-variant/80 mb-1.5">Email</label>
            <div className="relative cursor-text">
              <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50 pointer-events-none" />
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                className="input-glass pl-10 pr-4"
                placeholder="Enter your email address"
                autoFocus
                required
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-on-surface-variant/80 mb-1.5">Password</label>
            <div className="relative cursor-text">
              <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-variant/50 pointer-events-none" />
              <input
                type={showPassword ? 'text' : 'password'}
                name="password"
                value={formData.password}
                onChange={handleChange}
                className="input-glass pl-10 pr-10"
                placeholder="Enter your password"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-on-surface-variant/50 hover:text-on-surface-variant transition-all"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn-glass-primary w-full py-2.5 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? 'Signing in...' : 'Sign in'}
          </button>

          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-white/20" />
            </div>
            <div className="relative flex justify-center text-xs">
              <span className="bg-[rgba(255,255,255,0.6)] px-2 text-on-surface-variant/50">or</span>
            </div>
          </div>

          <button
            type="button"
            onClick={handleDemoMode}
            className="w-full text-sm text-on-surface-variant/70 hover:text-primary transition-all py-2"
          >
            Continue with Demo Mode
          </button>
          <button
            type="button"
            onClick={handleSeedAdmin}
            disabled={seeding}
            className="w-full text-xs text-on-surface-variant/50 hover:text-primary transition-all pb-2 disabled:opacity-50"
          >
            {seeding ? 'Seeding admin...' : 'Seed admin account (admin@aasa.com)'}
          </button>
        </form>

        <p className="text-center text-sm text-on-surface-variant/70 mt-6">
          Don't have an account?{' '}
          <Link to="/register" className="text-primary hover:text-primary/80 font-medium">
            Create one
          </Link>
        </p>
      </motion.div>
    </div>
  )
}
