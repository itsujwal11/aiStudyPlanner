import React, { useState, useEffect } from 'react'
import { recommendationAPI } from '../api'
import { Lightbulb, Calendar, Zap, AlertCircle } from 'lucide-react'
import { motion } from 'framer-motion'

const container = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.08 } },
}

const item = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { duration: 0.35 } },
}

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
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="glass-pane rounded-xl p-8 border border-black/8 text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-2 border-primary border-t-transparent mx-auto mb-4"></div>
          <p className="text-on-surface-variant/70">Loading recommendations...</p>
        </div>
      </div>
    )
  }

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-6">
        <motion.h1 className="text-2xl md:text-4xl text-on-surface font-bold mb-0" variants={item}>Study Recommendations</motion.h1>

        {error && (
          <motion.div className="glass-pane rounded-xl p-4 border border-black/8 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3 mb-6" variants={item}>
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <p>{error}</p>
          </motion.div>
        )}

        <motion.div className="grid grid-cols-1 lg:grid-cols-3 gap-8" variants={item}>
          <div className="lg:col-span-2">
            <div className="glass-pane rounded-xl p-6 border border-black/8">
              <h2 className="text-2xl text-on-surface font-bold mb-6 flex items-center gap-2">
                <Lightbulb className="w-6 h-6 text-primary" />
                Recommended Topics
              </h2>
              <div className="space-y-4">
                {nextTopics.map((topic, idx) => (
                  <div key={topic.id} className="glass-pane-sm rounded-xl p-4 hover:bg-white/85 transition">
                    <div className="flex items-start justify-between mb-2">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-xs font-bold bg-primary/10 text-primary px-2 py-1 rounded">
                            #{idx + 1}
                          </span>
                          <h3 className="text-lg font-semibold text-on-surface">{topic.title}</h3>
                        </div>
                        <p className="text-sm text-on-surface-variant/70">Priority: {topic.priority?.toFixed(2)}</p>
                      </div>
                      <Zap className="w-5 h-5 text-orange-500" />
                    </div>
                    <div className="w-full bg-white/40 rounded-full h-2">
                      <div
                        className="bg-gradient-to-r from-primary to-blue-500 h-2 rounded-full"
                        style={{ width: `${Math.min(topic.priority * 100, 100)}%` }}
                      ></div>
                    </div>
                  </div>
                ))}
                {nextTopics.length === 0 && (
                  <p className="text-center py-6 text-on-surface-variant/50">No recommended topics available.</p>
                )}
              </div>
            </div>
          </div>

          <div className="space-y-6">
            <div className="glass-pane rounded-xl p-6 border border-black/8">
              <h3 className="text-lg text-on-surface font-bold mb-4">Your Insights</h3>
              <div className="space-y-3">
                <div>
                  <p className="text-sm text-on-surface-variant/70">Total Attempts</p>
                  <p className="text-2xl font-bold text-primary">{insights?.totalAttempts || 0}</p>
                </div>
                <div>
                  <p className="text-sm text-on-surface-variant/70">Accuracy</p>
                  <p className="text-2xl font-bold text-emerald-600">
                    {insights?.accuracy?.toFixed(1) || 0}%
                  </p>
                </div>
                <div>
                  <p className="text-sm text-on-surface-variant/70">Study Time</p>
                  <p className="text-2xl font-bold text-blue-600">
                    {Math.floor((insights?.totalTimeSeconds || 0) / 60)} min
                  </p>
                </div>
              </div>
            </div>

            {insights?.strengths?.length > 0 && (
              <div className="glass-pane rounded-xl p-6 border border-black/8">
                <h3 className="text-lg text-on-surface font-bold mb-4">Your Strengths</h3>
                <div className="space-y-2">
                  {insights.strengths.map((strength, idx) => (
                    <div key={idx} className="glass-pane-sm rounded-xl p-3 border-emerald-200/50">
                      <p className="text-sm font-semibold text-emerald-700">{strength.topic}</p>
                      <p className="text-xs text-on-surface-variant/70">{strength.score?.toFixed(1)}%</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {insights?.weaknesses?.length > 0 && (
              <div className="glass-pane rounded-xl p-6 border border-black/8">
                <h3 className="text-lg text-on-surface font-bold mb-4">Areas to Improve</h3>
                <div className="space-y-2">
                  {insights.weaknesses.map((weakness, idx) => (
                    <div key={idx} className="glass-pane-sm rounded-xl p-3 border-red-200/50">
                      <p className="text-sm font-semibold text-red-700">{weakness.topic}</p>
                      <p className="text-xs text-on-surface-variant/70">{weakness.score?.toFixed(1)}%</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </motion.div>

        <motion.div className="mt-8 glass-pane rounded-xl p-6 border border-black/8" variants={item}>
          <h2 className="text-2xl text-on-surface font-bold mb-6 flex items-center gap-2">
            <Calendar className="w-6 h-6 text-primary" />
            Suggested Study Schedule
          </h2>
          <div className="overflow-x-auto -mx-6 px-6">
            <table className="w-full min-w-[600px]">
              <thead>
                <tr className="border-b border-black/8">
                  <th className="text-left text-xs font-bold text-on-surface-variant/70 uppercase tracking-wider pb-3 pr-4">Date</th>
                  <th className="text-left text-xs font-bold text-on-surface-variant/70 uppercase tracking-wider pb-3 pr-4">Day</th>
                  <th className="text-left text-xs font-bold text-on-surface-variant/70 uppercase tracking-wider pb-3">Topics</th>
                </tr>
              </thead>
              <tbody>
                {schedule.map((day, idx) => (
                  <tr key={idx} className="border-b border-black/6 last:border-b-0">
                    <td className="py-3 pr-4 text-sm font-semibold text-on-surface whitespace-nowrap">
                      {new Date(day.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                    </td>
                    <td className="py-3 pr-4 text-sm text-on-surface-variant/70">
                      {new Date(day.date).toLocaleDateString('en-US', { weekday: 'short' })}
                    </td>
                    <td className="py-3">
                      <div className="flex flex-wrap gap-1.5">
                        {day.topics?.map((topic, topicIdx) => (
                          <span key={topicIdx} className="text-xs font-semibold bg-primary/10 text-primary px-2 py-1 rounded-lg">{topic.title}</span>
                        ))}
                        {(!day.topics || day.topics.length === 0) && (
                          <span className="text-xs text-on-surface-variant/50 italic">Rest day</span>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {schedule.length === 0 && (
                  <tr><td colSpan="3" className="py-6 text-center text-on-surface-variant/50 text-sm">No schedule data available.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </motion.div>
    </motion.div>
  )
}
