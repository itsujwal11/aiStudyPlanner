import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Navigation } from '../components/Navigation'
import { pdfAPI, topicAPI, dashboardAPI } from '../api'
import { Upload, BookOpen, TrendingUp, Zap, Target, BarChart3 } from 'lucide-react'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

export const Dashboard = () => {
  const [dashboard, setDashboard] = useState(null)
  const [pdfs, setPdfs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    fetchDashboard()
  }, [])

  const fetchDashboard = async () => {
    try {
      const [dashRes, pdfRes] = await Promise.all([
        dashboardAPI.get(),
        pdfAPI.list(),
      ])
      setDashboard(dashRes.data)
      setPdfs(pdfRes.data)
      setError('')
    } catch (err) {
      setError('Failed to load dashboard')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-cyan-500 mx-auto mb-4"></div>
          <p className="text-slate-400">Loading dashboard...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      <Navigation currentPage="dashboard" />

      <main className="max-w-7xl mx-auto px-6 py-8">
        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-sm">Total PDFs</p>
                <p className="text-3xl font-bold text-cyan-400">{dashboard?.totalPdfs || 0}</p>
              </div>
              <BookOpen className="w-8 h-8 text-cyan-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-sm">Topics</p>
                <p className="text-3xl font-bold text-blue-400">{dashboard?.totalTopics || 0}</p>
              </div>
              <Target className="w-8 h-8 text-blue-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-sm">Avg Score</p>
                <p className="text-3xl font-bold text-emerald-400">
                  {dashboard?.averageScore?.toFixed(1) || 0}%
                </p>
              </div>
              <TrendingUp className="w-8 h-8 text-emerald-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-sm">Days Left</p>
                <p className="text-3xl font-bold text-orange-400">{dashboard?.daysUntilExam || 0}</p>
              </div>
              <Zap className="w-8 h-8 text-orange-500/30" />
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-2">
            <div className="card">
              <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
                <BarChart3 className="w-5 h-5 text-cyan-400" />
                Study Progress
              </h2>
              <div className="space-y-4">
                {dashboard?.rankedTopics?.slice(0, 5).map((topic) => (
                  <div key={topic.topicId} className="p-4 bg-slate-800/50 rounded-lg">
                    <div className="flex items-center justify-between mb-2">
                      <h3 className="font-semibold text-slate-200">{topic.topicTitle}</h3>
                      <span className="text-xs px-2 py-1 bg-cyan-500/20 text-cyan-400 rounded">
                        {topic.weaknessLevel}
                      </span>
                    </div>
                    <div className="w-full bg-slate-700 rounded-full h-2">
                      <div
                        className="bg-gradient-to-r from-cyan-500 to-blue-500 h-2 rounded-full transition-all"
                        style={{ width: `${topic.completionPercentage}%` }}
                      ></div>
                    </div>
                    <p className="text-xs text-slate-400 mt-2">
                      {topic.correctAttempts}/{topic.totalAttempts} correct • Best: {topic.bestScore?.toFixed(1)}%
                    </p>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="space-y-6">
            <div className="card">
              <h2 className="text-lg font-bold mb-4">Quick Actions</h2>
              <button
                onClick={() => navigate('/upload')}
                className="btn-primary w-full flex items-center justify-center gap-2 mb-3"
              >
                <Upload className="w-4 h-4" />
                Upload PDF
              </button>
              <button
                onClick={() => navigate('/study')}
                className="btn-secondary w-full flex items-center justify-center gap-2"
              >
                <BookOpen className="w-4 h-4" />
                Start Studying
              </button>
            </div>

            <div className="card">
              <h2 className="text-lg font-bold mb-4">Weak Topics</h2>
              <div className="space-y-2">
                {dashboard?.weakTopics?.slice(0, 3).map((topic) => (
                  <div key={topic.topicId} className="p-3 bg-red-500/10 rounded-lg border border-red-500/20">
                    <p className="text-sm font-semibold text-red-400">{topic.topicTitle}</p>
                    <p className="text-xs text-slate-400">Score: {topic.bestScore?.toFixed(1)}%</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        <div className="mt-8 card">
          <h2 className="text-xl font-bold mb-6">Your PDFs</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {pdfs.map((pdf) => (
              <div key={pdf.id} className="p-4 bg-slate-800/50 rounded-lg border border-slate-700/50">
                <h3 className="font-semibold text-slate-200 truncate">{pdf.fileName}</h3>
                <p className="text-xs text-slate-400 mt-1">
                  Exam: {new Date(pdf.examDate).toLocaleDateString()}
                </p>
                <p className="text-xs text-cyan-400 mt-2">
                  {pdf.topicCount} topics • {pdf.isAnalyzed ? 'Analyzed' : 'Pending'}
                </p>
              </div>
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}
