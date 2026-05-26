import React, { useState, useEffect } from 'react'
import { Navigation } from '../components/Navigation'
import { recommendationAPI } from '../api'
import { Lightbulb, Calendar, Zap, AlertCircle } from 'lucide-react'

export const Recommendations = () => {
  const [nextTopics, setNextTopics] = useState([])
  const [insights, setInsights] = useState(null)
  const [schedule, setSchedule] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    fetchRecommendations()
  }, [])

  const fetchRecommendations = async () => {
    try {
      const [topicsRes, insightsRes, scheduleRes] = await Promise.all([
        recommendationAPI.getNextTopics(5),
        recommendationAPI.getInsights(),
        recommendationAPI.getSchedule(7),
      ])
      setNextTopics(topicsRes.data)
      setInsights(insightsRes.data)
      setSchedule(scheduleRes.data)
      setError('')
    } catch (err) {
      setError('Failed to load recommendations')
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
          <p className="text-slate-400">Loading recommendations...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-8">
      <Navigation currentPage="recommendations" />
      <div className="max-w-7xl mx-auto mt-8">
        <h1 className="text-4xl font-bold gradient-text mb-8">Study Recommendations</h1>

        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-red-500" />
            <p className="text-red-400">{error}</p>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Next Topics to Study */}
          <div className="lg:col-span-2">
            <div className="card">
              <h2 className="text-2xl font-bold mb-6 flex items-center gap-2">
                <Lightbulb className="w-6 h-6 text-cyan-400" />
                Recommended Topics
              </h2>
              <div className="space-y-4">
                {nextTopics.map((topic, idx) => (
                  <div key={topic.id} className="p-4 bg-slate-800/50 rounded-lg border border-slate-700/50 hover:border-cyan-500/50 transition">
                    <div className="flex items-start justify-between mb-2">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-xs font-bold bg-cyan-500/20 text-cyan-400 px-2 py-1 rounded">
                            #{idx + 1}
                          </span>
                          <h3 className="text-lg font-semibold text-slate-200">{topic.title}</h3>
                        </div>
                        <p className="text-sm text-slate-400">Priority: {topic.priority?.toFixed(2)}</p>
                      </div>
                      <Zap className="w-5 h-5 text-orange-400" />
                    </div>
                    <div className="w-full bg-slate-700 rounded-full h-2">
                      <div
                        className="bg-gradient-to-r from-cyan-500 to-blue-500 h-2 rounded-full"
                        style={{ width: `${Math.min(topic.priority * 100, 100)}%` }}
                      ></div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Study Insights */}
          <div className="space-y-6">
            <div className="card">
              <h3 className="text-lg font-bold mb-4">Your Insights</h3>
              <div className="space-y-3">
                <div>
                  <p className="text-sm text-slate-400">Total Attempts</p>
                  <p className="text-2xl font-bold text-cyan-400">{insights?.totalAttempts || 0}</p>
                </div>
                <div>
                  <p className="text-sm text-slate-400">Accuracy</p>
                  <p className="text-2xl font-bold text-emerald-400">
                    {insights?.accuracy?.toFixed(1) || 0}%
                  </p>
                </div>
                <div>
                  <p className="text-sm text-slate-400">Study Time</p>
                  <p className="text-2xl font-bold text-blue-400">
                    {Math.floor((insights?.totalTimeSeconds || 0) / 60)} min
                  </p>
                </div>
              </div>
            </div>

            {/* Strengths */}
            {insights?.strengths?.length > 0 && (
              <div className="card">
                <h3 className="text-lg font-bold mb-4">Your Strengths</h3>
                <div className="space-y-2">
                  {insights.strengths.map((strength, idx) => (
                    <div key={idx} className="p-2 bg-emerald-500/10 rounded border border-emerald-500/20">
                      <p className="text-sm font-semibold text-emerald-400">{strength.topic}</p>
                      <p className="text-xs text-slate-400">{strength.score?.toFixed(1)}%</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Weaknesses */}
            {insights?.weaknesses?.length > 0 && (
              <div className="card">
                <h3 className="text-lg font-bold mb-4">Areas to Improve</h3>
                <div className="space-y-2">
                  {insights.weaknesses.map((weakness, idx) => (
                    <div key={idx} className="p-2 bg-red-500/10 rounded border border-red-500/20">
                      <p className="text-sm font-semibold text-red-400">{weakness.topic}</p>
                      <p className="text-xs text-slate-400">{weakness.score?.toFixed(1)}%</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Study Schedule */}
        <div className="mt-8 card">
          <h2 className="text-2xl font-bold mb-6 flex items-center gap-2">
            <Calendar className="w-6 h-6 text-blue-400" />
            Suggested Study Schedule
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-7 gap-4">
            {schedule.map((day, idx) => (
              <div key={idx} className="p-4 bg-slate-800/50 rounded-lg border border-slate-700/50">
                <p className="text-sm font-semibold text-slate-300 mb-3">
                  {new Date(day.date).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}
                </p>
                <div className="space-y-2">
                  {day.topics?.map((topic, topicIdx) => (
                    <div key={topicIdx} className="text-xs p-2 bg-cyan-500/10 rounded border border-cyan-500/20">
                      <p className="font-semibold text-cyan-400 truncate">{topic.title}</p>
                      <p className="text-slate-400 text-xs">{topic.weakness}</p>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
