import React, { useState, useEffect } from 'react'
import { Navigation } from '../components/Navigation'
import { analyticsAPI } from '../api'
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'
import { TrendingUp, Target, Zap, Clock } from 'lucide-react'

export const Analytics = () => {
  const [analytics, setAnalytics] = useState(null)
  const [comparison, setComparison] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

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
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-cyan-500 mx-auto mb-4"></div>
          <p className="text-slate-400">Loading analytics...</p>
        </div>
      </div>
    )
  }

  const COLORS = ['#06b6d4', '#0ea5e9', '#3b82f6', '#8b5cf6', '#ec4899']

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-8">
      <Navigation currentPage="analytics" />
      <div className="max-w-7xl mx-auto mt-8">
        <h1 className="text-4xl font-bold gradient-text mb-8">Analytics & Insights</h1>

        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400">
            {error}
          </div>
        )}

        {/* Key Metrics */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-sm">Total Quizzes</p>
                <p className="text-3xl font-bold text-cyan-400">{analytics?.totalQuizzes || 0}</p>
              </div>
              <Target className="w-8 h-8 text-cyan-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-sm">Correct Answers</p>
                <p className="text-3xl font-bold text-emerald-400">{analytics?.correctAnswers || 0}</p>
              </div>
              <TrendingUp className="w-8 h-8 text-emerald-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-sm">Overall Accuracy</p>
                <p className="text-3xl font-bold text-blue-400">{analytics?.overallAccuracy?.toFixed(1) || 0}%</p>
              </div>
              <Zap className="w-8 h-8 text-blue-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-sm">Avg Time</p>
                <p className="text-3xl font-bold text-orange-400">{analytics?.averageTimeSeconds || 0}s</p>
              </div>
              <Clock className="w-8 h-8 text-orange-500/30" />
            </div>
          </div>
        </div>

        {/* Charts */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
          {/* Difficulty Distribution */}
          <div className="card">
            <h2 className="text-xl font-bold mb-6">Performance by Difficulty</h2>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={Object.entries(analytics?.byDifficulty || {}).map(([key, value]) => ({
                name: key,
                accuracy: value.accuracy,
                attempts: value.attempts,
              }))}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="name" stroke="#94a3b8" />
                <YAxis stroke="#94a3b8" />
                <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #475569' }} />
                <Legend />
                <Bar dataKey="accuracy" fill="#06b6d4" name="Accuracy %" />
                <Bar dataKey="attempts" fill="#0ea5e9" name="Attempts" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Trend */}
          <div className="card">
            <h2 className="text-xl font-bold mb-6">7-Day Trend</h2>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={analytics?.trend || []}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="date" stroke="#94a3b8" />
                <YAxis stroke="#94a3b8" />
                <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #475569' }} />
                <Legend />
                <Line type="monotone" dataKey="accuracy" stroke="#06b6d4" name="Accuracy %" />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Topic Comparison */}
        <div className="card">
          <h2 className="text-xl font-bold mb-6">Topic Performance Comparison</h2>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-700">
                  <th className="text-left py-3 px-4 text-slate-300">Topic</th>
                  <th className="text-left py-3 px-4 text-slate-300">Attempts</th>
                  <th className="text-left py-3 px-4 text-slate-300">Accuracy</th>
                  <th className="text-left py-3 px-4 text-slate-300">Complexity</th>
                  <th className="text-left py-3 px-4 text-slate-300">Priority</th>
                </tr>
              </thead>
              <tbody>
                {comparison?.map((topic, idx) => (
                  <tr key={idx} className="border-b border-slate-700/50 hover:bg-slate-800/30">
                    <td className="py-3 px-4 text-slate-200">{topic.topic}</td>
                    <td className="py-3 px-4 text-slate-400">{topic.attempts}</td>
                    <td className="py-3 px-4">
                      <span className={`px-3 py-1 rounded text-sm ${
                        topic.accuracy >= 75 ? 'bg-emerald-500/20 text-emerald-400' :
                        topic.accuracy >= 50 ? 'bg-yellow-500/20 text-yellow-400' :
                        'bg-red-500/20 text-red-400'
                      }`}>
                        {topic.accuracy.toFixed(1)}%
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-400">{topic.complexity?.toFixed(2)}</td>
                    <td className="py-3 px-4 text-slate-400">{topic.priority?.toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  )
}