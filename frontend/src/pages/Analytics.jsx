import React, { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { analyticsAPI } from '../api'
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'
import { TrendingUp, Target, Zap, Clock, AlertCircle, ArrowLeft } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.07 } } }
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35 } } }

export const Analytics = () => {
  const [analytics, setAnalytics] = useState(null)
  const [comparison, setComparison] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    fetchAnalytics()
  }, [])

  const fetchAnalytics = async () => {
    try {
      const [perfRes, compRes] = await Promise.all([
        analyticsAPI.getPerformance(),
        analyticsAPI.getComparison(),
      ])
      setAnalytics(perfRes.data)
      setComparison(compRes.data)
      setError('')
    } catch (err) {
      setError('Failed to load analytics')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-2 border-primary border-t-transparent mx-auto mb-4"></div>
          <p className="text-on-surface-variant/70">Loading analytics...</p>
        </div>
      </div>
    )
  }

  const COLORS = ['#06b6d4', '#0ea5e9', '#3b82f6', '#8b5cf6', '#ec4899']

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
      <button onClick={() => navigate('/dashboard')} className="flex items-center gap-2 text-on-surface-variant/70 hover:text-primary mb-4 transition-colors text-sm">
        <ArrowLeft className="w-4 h-4" /> Back to Dashboard
      </button>
      <h1 className="text-2xl md:text-4xl font-bold text-on-surface mb-0">Analytics & Insights</h1>

      {error && (
        <motion.div variants={item} className="glass-pane rounded-xl p-4 border border-black/8 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3 mb-6">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p>{error}</p>
        </motion.div>
      )}

      {/* Key Metrics */}
      <motion.div variants={item} className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <div className="glass-pane rounded-xl p-5 border border-black/8">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-on-surface-variant/70 text-sm">Total Quizzes</p>
              <p className="text-3xl font-bold text-on-surface">{analytics?.totalQuizzes || 0}</p>
            </div>
            <Target className="w-8 h-8 text-cyan-500/30" />
          </div>
        </div>

        <div className="glass-pane rounded-xl p-5 border border-black/8">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-on-surface-variant/70 text-sm">Correct Answers</p>
              <p className="text-3xl font-bold text-on-surface">{analytics?.correctAnswers || 0}</p>
            </div>
            <TrendingUp className="w-8 h-8 text-emerald-500/30" />
          </div>
        </div>

        <div className="glass-pane rounded-xl p-5 border border-black/8">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-on-surface-variant/70 text-sm">Overall Accuracy</p>
              <p className="text-3xl font-bold text-on-surface">{analytics?.overallAccuracy?.toFixed(1) || 0}%</p>
            </div>
            <Zap className="w-8 h-8 text-blue-500/30" />
          </div>
        </div>

        <div className="glass-pane rounded-xl p-5 border border-black/8">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-on-surface-variant/70 text-sm">Avg Time</p>
              <p className="text-3xl font-bold text-on-surface">{analytics?.averageTimeSeconds || 0}s</p>
            </div>
            <Clock className="w-8 h-8 text-orange-500/30" />
          </div>
        </div>
      </motion.div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
        {/* Difficulty Distribution */}
        <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
          <h2 className="text-xl font-bold text-on-surface mb-6">Performance by Difficulty</h2>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={Object.entries(analytics?.byDifficulty || {}).map(([key, value]) => ({
              name: key,
              accuracy: value.accuracy,
              attempts: value.attempts,
            }))}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="name" stroke="#94a3b8" />
              <YAxis stroke="#94a3b8" />
              <Tooltip contentStyle={{ backgroundColor: 'rgba(255,255,255,0.85)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255,255,255,0.3)' }} />
              <Legend />
              <Bar dataKey="accuracy" fill="#06b6d4" name="Accuracy %" />
              <Bar dataKey="attempts" fill="#0ea5e9" name="Attempts" />
            </BarChart>
          </ResponsiveContainer>
        </motion.div>

        {/* Trend */}
        <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
          <h2 className="text-xl font-bold text-on-surface mb-6">7-Day Trend</h2>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={analytics?.trend || []}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="date" stroke="#94a3b8" />
              <YAxis stroke="#94a3b8" />
              <Tooltip contentStyle={{ backgroundColor: 'rgba(255,255,255,0.85)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255,255,255,0.3)' }} />
              <Legend />
              <Line type="monotone" dataKey="accuracy" stroke="#06b6d4" name="Accuracy %" />
            </LineChart>
          </ResponsiveContainer>
        </motion.div>
      </div>

      {/* Topic Comparison */}
      <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
        <h2 className="text-xl font-bold text-on-surface mb-6">Topic Performance Comparison</h2>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-black/8">
                <th className="text-left py-3 px-4 text-on-surface-variant/70">Topic</th>
                <th className="text-left py-3 px-4 text-on-surface-variant/70">Attempts</th>
                <th className="text-left py-3 px-4 text-on-surface-variant/70">Accuracy</th>
                <th className="text-left py-3 px-4 text-on-surface-variant/70">Complexity</th>
                <th className="text-left py-3 px-4 text-on-surface-variant/70">Priority</th>
              </tr>
            </thead>
            <tbody>
              {comparison?.map((topic, idx) => (
                <tr key={idx} className="bg-white/40 backdrop-blur-sm border border-black/8 rounded-xl hover:bg-white/60 [&>td:first-child]:rounded-l-xl [&>td:last-child]:rounded-r-xl">
                  <td className="py-3 px-4 text-on-surface">{topic.topic}</td>
                  <td className="py-3 px-4 text-on-surface">{topic.attempts}</td>
                  <td className="py-3 px-4">
                    <span className={`px-3 py-1 rounded text-sm font-medium ${
                      topic.accuracy >= 75 ? 'bg-emerald-100/80 text-emerald-700' :
                      topic.accuracy >= 50 ? 'bg-primary/10 text-primary' :
                      'bg-red-50/80 text-red-700'
                    }`}>
                      {topic.accuracy.toFixed(1)}%
                    </span>
                  </td>
                  <td className="py-3 px-4 text-on-surface">{topic.complexity?.toFixed(2)}</td>
                  <td className="py-3 px-4 text-on-surface">{topic.priority?.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </motion.div>
    </motion.div>
  )
}
