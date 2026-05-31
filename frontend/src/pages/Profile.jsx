import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { motion } from 'framer-motion'
import { Settings, Save, AlertCircle } from 'lucide-react'

export const Profile = () => {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [formData, setFormData] = useState({
    name: user?.name || '',
    email: user?.email || '',
    examDate: '',
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    const savedExamDate = localStorage.getItem('examDate')
    if (savedExamDate) {
      setFormData(prev => ({
        ...prev,
        examDate: savedExamDate.split('T')[0],
      }))
    }
  }, [])

  const handleChange = (e) => {
    const { name, value } = e.target
    setFormData(prev => ({
      ...prev,
      [name]: value,
    }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccess('')

    try {
      if (formData.examDate) {
        localStorage.setItem('examDate', new Date(formData.examDate).toISOString())
      }
      setSuccess('Profile updated successfully!')
      setTimeout(() => setSuccess(''), 3000)
    } catch (err) {
      setError('Failed to update profile')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-2xl">
      <div className="flex items-center gap-3 mb-8">
        <Settings className="w-8 h-8 text-primary" />
        <h1 className="text-2xl md:text-4xl text-on-surface font-bold">Profile Settings</h1>
      </div>

      {error && (
        <div className="glass-pane rounded-xl p-4 border border-black/8 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3 mb-6">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p>{error}</p>
        </div>
      )}

      {success && (
        <div className="glass-pane rounded-xl p-4 border border-black/8 bg-emerald-50/80 border border-emerald-200/50 text-emerald-700 flex items-center gap-3 mb-6">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p>{success}</p>
        </div>
      )}

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="glass-pane rounded-xl p-6 border border-black/8"
        >
          <form onSubmit={handleSubmit} className="space-y-6">
            <div>
              <label className="block text-sm font-semibold text-on-surface-variant/70 mb-2">
                Full Name
              </label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                disabled
                className="input-glass px-4 w-full disabled:opacity-50 disabled:cursor-not-allowed"
              />
              <p className="text-xs text-on-surface-variant/70 mt-1">Contact support to change your name</p>
            </div>

            <div>
              <label className="block text-sm font-semibold text-on-surface-variant/70 mb-2">
                Email
              </label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                disabled
                className="input-glass px-4 w-full disabled:opacity-50 disabled:cursor-not-allowed"
              />
              <p className="text-xs text-on-surface-variant/70 mt-1">Contact support to change your email</p>
            </div>

            <div>
              <label className="block text-sm font-semibold text-on-surface-variant/70 mb-2">
                Exam Date
              </label>
              <input
                type="date"
                name="examDate"
                value={formData.examDate}
                onChange={handleChange}
                className="input-glass px-4 w-full"
              />
              <p className="text-xs text-on-surface-variant/70 mt-1">Update your exam date to get accurate recommendations</p>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full btn-glass-primary flex items-center justify-center gap-2 disabled:opacity-50"
            >
              <Save className="w-4 h-4" />
              {loading ? 'Saving...' : 'Save Changes'}
            </button>
          </form>
        </motion.div>

        <div className="mt-8 glass-pane rounded-xl p-6 border border-black/8">
          <h2 className="text-xl text-on-surface font-bold mb-4">Account Information</h2>
          <div className="space-y-3 text-sm text-on-surface-variant/70">
            <p>Member since: {new Date(user?.createdAt || Date.now()).toLocaleDateString()}</p>
            <p>Account Status: <span className="text-emerald-600">Active</span></p>
            <p>Study Sessions: <span className="text-primary">Track your progress in Analytics</span></p>
          </div>
        </div>
    </div>
  )
}
