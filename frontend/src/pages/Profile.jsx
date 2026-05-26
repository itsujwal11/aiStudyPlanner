import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Navigation } from '../components/Navigation'
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
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      <Navigation currentPage="profile" />

      <main className="max-w-2xl mx-auto px-6 py-8">
        <div className="flex items-center gap-3 mb-8">
          <Settings className="w-8 h-8 text-cyan-400" />
          <h1 className="text-4xl font-bold gradient-text">Profile Settings</h1>
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-red-500" />
            <p className="text-red-400">{error}</p>
          </div>
        )}

        {success && (
          <div className="mb-6 p-4 bg-emerald-500/10 border border-emerald-500/20 rounded-lg flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-emerald-500" />
            <p className="text-emerald-400">{success}</p>
          </div>
        )}

        <div className="card">
          <form onSubmit={handleSubmit} className="space-y-6">
            <div>
              <label className="block text-sm font-semibold text-slate-300 mb-2">
                Full Name
              </label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                disabled
                className="w-full px-4 py-2 bg-slate-700/50 border border-slate-600 rounded-lg text-slate-300 disabled:opacity-50"
              />
              <p className="text-xs text-slate-400 mt-1">Contact support to change your name</p>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-300 mb-2">
                Email
              </label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                disabled
                className="w-full px-4 py-2 bg-slate-700/50 border border-slate-600 rounded-lg text-slate-300 disabled:opacity-50"
              />
              <p className="text-xs text-slate-400 mt-1">Contact support to change your email</p>
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-300 mb-2">
                Exam Date
              </label>
              <input
                type="date"
                name="examDate"
                value={formData.examDate}
                onChange={handleChange}
                className="w-full px-4 py-2 bg-slate-700/50 border border-slate-600 rounded-lg text-slate-300 focus:border-cyan-500 focus:outline-none transition"
              />
              <p className="text-xs text-slate-400 mt-1">Update your exam date to get accurate recommendations</p>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full btn-primary flex items-center justify-center gap-2 disabled:opacity-50"
            >
              <Save className="w-4 h-4" />
              {loading ? 'Saving...' : 'Save Changes'}
            </button>
          </form>
        </div>

        <div className="mt-8 card border border-slate-700/50">
          <h2 className="text-xl font-bold mb-4 text-slate-200">Account Information</h2>
          <div className="space-y-3 text-sm text-slate-400">
            <p>Member since: {new Date(user?.createdAt || Date.now()).toLocaleDateString()}</p>
            <p>Account Status: <span className="text-emerald-400">Active</span></p>
            <p>Study Sessions: <span className="text-cyan-400">Track your progress in Analytics</span></p>
          </div>
        </div>
      </main>
    </div>
  )
}
