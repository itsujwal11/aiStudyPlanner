import React, { useState, useEffect } from 'react'
import { Navigation } from '../components/Navigation'
import { quizAPI } from '../api'
import { Calendar, TrendingUp, AlertCircle } from 'lucide-react'

export const ProgressTimeline = () => {
  const [timeline, setTimeline] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    fetchTimeline()
  }, [])

  const fetchTimeline = async () => {
    try {
      setLoading(true)
      await quizAPI.getById(1)
      const mockTimeline = generateMockTimeline()
      setTimeline(mockTimeline)
      setError('')
    } catch (err) {
      setError('Failed to load progress timeline')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  const generateMockTimeline = () => {
    const events = []
    const today = new Date()

    for (let i = 30; i >= 0; i--) {
      const date = new Date(today)
      date.setDate(date.getDate() - i)

      if (Math.random() > 0.6) {
        events.push({
          id: i,
          date: date.toLocaleDateString(),
          type: Math.random() > 0.5 ? 'quiz' : 'study',
          title: Math.random() > 0.5 ? 'Quiz Completed' : 'Study Session',
          score: Math.floor(Math.random() * 40 + 60),
          topics: Math.floor(Math.random() * 3 + 1),
        })
      }
    }

    return events
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-2 border-primary border-t-transparent mx-auto mb-4"></div>
          <p className="text-on-surface-variant/70">Loading timeline...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      <Navigation currentPage="timeline" />

      <main className="max-w-4xl mx-auto px-6 py-8">
        <div className="flex items-center gap-3 mb-8">
          <Calendar className="w-8 h-8 text-cyan-400" />
          <h1 className="text-4xl font-bold gradient-text">Study Progress Timeline</h1>
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-red-500" />
            <p className="text-red-400">{error}</p>
          </div>
        )}

        <div className="card">
          <div className="relative">
            <div className="absolute left-6 top-0 bottom-0 w-1 bg-gradient-to-b from-cyan-500 to-blue-500"></div>

            <div className="space-y-6 pl-20">
              {timeline.length === 0 ? (
                <p className="text-slate-400 text-center py-8">No study activity yet. Start studying to see your progress!</p>
              ) : (
                timeline.map((event) => (
                  <div key={event.id} className="relative">
                    <div className="absolute -left-7 top-1 w-4 h-4 bg-cyan-500 rounded-full border-4 border-slate-800"></div>

                    <div className="p-4 bg-slate-800/50 rounded-lg border border-slate-700/50 hover:border-primary/30 transition">
                      <div className="flex items-start justify-between mb-2">
                        <div>
                          <p className="text-sm text-slate-400">{event.date}</p>
                          <h3 className="text-lg font-semibold text-slate-200 mt-1">{event.title}</h3>
                        </div>
                        <span className={`px-3 py-1 rounded text-sm font-semibold ${
                          event.type === 'quiz'
                            ? 'bg-cyan-500/20 text-cyan-400'
                            : 'bg-blue-500/20 text-blue-400'
                        }`}>
                          {event.type === 'quiz' ? 'Quiz' : 'Study'}
                        </span>
                      </div>

                      <div className="flex items-center gap-6 mt-3">
                        <div className="flex items-center gap-2">
                          <TrendingUp className="w-4 h-4 text-emerald-400" />
                          <span className="text-sm text-slate-300">
                            Score: <span className="font-semibold text-emerald-400">{event.score}%</span>
                          </span>
                        </div>
                        <div className="text-sm text-slate-400">
                          Topics covered: <span className="font-semibold text-cyan-400">{event.topics}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="card">
            <p className="text-slate-400 text-sm">Total Study Sessions</p>
            <p className="text-3xl font-bold text-cyan-400 mt-2">{timeline.filter(e => e.type === 'study').length}</p>
          </div>
          <div className="card">
            <p className="text-slate-400 text-sm">Quizzes Completed</p>
            <p className="text-3xl font-bold text-blue-400 mt-2">{timeline.filter(e => e.type === 'quiz').length}</p>
          </div>
          <div className="card">
            <p className="text-slate-400 text-sm">Avg Score</p>
            <p className="text-3xl font-bold text-emerald-400 mt-2">
              {timeline.length > 0
                ? Math.round(timeline.reduce((sum, e) => sum + e.score, 0) / timeline.length)
                : 0}%
            </p>
          </div>
        </div>
      </main>
    </div>
  )
}
