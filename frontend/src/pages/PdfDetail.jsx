import React, { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { pdfAPI } from '../api'
import { motion } from 'framer-motion'
import { useCountUp } from '../hooks/useCountUp'
import { BookOpen, Target, TrendingUp, Zap, ArrowLeft, Brain, ClipboardList, BarChart3, CheckCircle, XCircle } from 'lucide-react'

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.07 } } }
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } } }

function StatCard({ icon: Icon, label, value, color = 'text-primary' }) {
  const count = useCountUp(value, 800)
  return (
    <div className="glass-pane rounded-xl p-4 border border-black/8">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-on-surface-variant/70 font-medium mb-1">{label}</p>
          <p className={`text-2xl font-bold ${color}`}>{count}</p>
        </div>
        <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
          <Icon className={`w-5 h-5 ${color}`} />
        </div>
      </div>
    </div>
  )
}

export const PdfDetail = () => {
  const { pdfId } = useParams()
  const navigate = useNavigate()
  const [detail, setDetail] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!pdfId) return
    pdfAPI.getDetail(pdfId)
      .then(res => setDetail(res.data))
      .catch(() => setError('Failed to load PDF details'))
      .finally(() => setLoading(false))
  }, [pdfId])

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="animate-spin rounded-full h-10 w-10 border-2 border-primary border-t-transparent mx-auto mb-4" />
      </div>
    )
  }

  if (error || !detail) {
    return (
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="text-center">
          <p className="text-on-surface-variant">{error || 'PDF not found'}</p>
          <button onClick={() => navigate('/dashboard')} className="btn-glass-primary mt-4">
            <ArrowLeft className="w-4 h-4" /> Back to Dashboard
          </button>
        </div>
      </div>
    )
  }

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-8">
      <motion.div variants={item}>
        <button onClick={() => navigate('/dashboard')} className="flex items-center gap-2 text-on-surface-variant/70 hover:text-primary mb-4 transition-colors">
          <ArrowLeft className="w-4 h-4" /> Back to Dashboard
        </button>
        <h1 className="text-[32px] font-semibold text-on-surface leading-[40px]">{detail.fileName}</h1>
        <p className="text-lg text-on-surface-variant/70 mt-1">
          Exam: {new Date(detail.examDate).toLocaleDateString()} &middot; {detail.isAnalyzed ? 'Analyzed' : 'Not analyzed'}
        </p>
      </motion.div>

      <motion.div variants={item} className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <StatCard icon={BookOpen} label="Topics" value={detail.totalTopics || 0} color="text-primary" />
        <StatCard icon={ClipboardList} label="Quizzes" value={detail.totalQuizzes || 0} color="text-violet-600" />
        <StatCard icon={TrendingUp} label="Avg Score" value={Math.round(detail.averageScore || 0)} color="text-emerald-600" />
        <StatCard icon={Target} label="Completion" value={Math.round(detail.overallCompletionPercentage || 0)} color="text-cyan-600" />
        <StatCard icon={Zap} label="Days Left" value={detail.daysUntilExam || 0} color="text-orange-600" />
      </motion.div>

      <motion.div variants={item} className="flex gap-4">
        <button onClick={() => navigate(`/study/${pdfId}`)} className="btn-glass-primary">
          <BookOpen className="w-4 h-4" /> Study This PDF
        </button>
        <button onClick={() => navigate('/study')} className="btn-glass-secondary">
          <BarChart3 className="w-4 h-4" /> Study All
        </button>
      </motion.div>

      <motion.div variants={item}>
        <div className="glass-pane rounded-xl p-6 border border-black/8">
          <h2 className="text-2xl font-semibold text-on-surface mb-5 flex items-center gap-2">
            <Brain className="w-5 h-5 text-primary" />
            Topics ({detail.totalTopics})
          </h2>
          <div className="space-y-4">
            {detail.topics?.map((topic, i) => (
              <motion.div
                key={topic.id}
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.06 }}
                className="p-4 rounded-xl bg-white/40 backdrop-blur-sm border border-black/8"
              >
                <div className="flex items-start justify-between mb-3">
                  <div className="flex-1 min-w-0">
                    <h3 className="font-semibold text-on-surface">{topic.title}</h3>
                    {topic.description && (
                      <p className="text-sm text-on-surface-variant/70 mt-1">{topic.description}</p>
                    )}
                  </div>
                  <div className="flex items-center gap-2 ml-4 flex-shrink-0">
                    <span className="text-xs px-2.5 py-1 rounded-full bg-primary/10 text-primary font-medium">
                      {topic.quizCount}Q
                    </span>
                    <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${
                      topic.weaknessLevel === 'HIGH' ? 'bg-red-50/80 text-red-700' :
                      topic.weaknessLevel === 'MEDIUM' ? 'bg-yellow-50 text-yellow-700' :
                      topic.weaknessLevel === 'LOW' ? 'bg-emerald-50 text-emerald-700' :
                      'bg-gray-100 text-gray-500'
                    }`}>
                      {topic.weaknessLevel === 'NOT_ATTEMPTED' ? 'Not Attempted' : topic.weaknessLevel}
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-3 text-sm">
                  <div>
                    <span className="text-on-surface-variant/70">Complexity: </span>
                    <span className="font-semibold text-on-surface">{topic.complexityScore?.toFixed(2)}</span>
                  </div>
                  <div>
                    <span className="text-on-surface-variant/70">Importance: </span>
                    <span className="font-semibold text-on-surface">{topic.importanceScore?.toFixed(2)}</span>
                  </div>
                  <div>
                    <span className="text-on-surface-variant/70">Priority: </span>
                    <span className="font-semibold text-on-surface">{topic.priorityScore?.toFixed(2)}</span>
                  </div>
                  <div>
                    <span className="text-on-surface-variant/70">Weakness: </span>
                    <span className="font-semibold text-on-surface">{topic.weaknessScore?.toFixed(2)}</span>
                  </div>
                </div>

                {topic.totalAttempts > 0 && (
                  <div className="pt-3 border-t border-white/20">
                    <div className="grid grid-cols-3 gap-4 text-sm">
                      <div className="flex items-center gap-1.5">
                        <CheckCircle className="w-4 h-4 text-emerald-500" />
                        <span className="text-on-surface-variant/70">Correct: <strong className="text-on-surface">{topic.correctAttempts}</strong></span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <XCircle className="w-4 h-4 text-error" />
                        <span className="text-on-surface-variant/70">Total: <strong className="text-on-surface">{topic.totalAttempts}</strong></span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <Target className="w-4 h-4 text-primary" />
                        <span className="text-on-surface-variant/70">Best Score: <strong className="text-on-surface">{topic.bestScore?.toFixed(1)}%</strong></span>
                      </div>
                    </div>
                    <div className="mt-2 w-full bg-white/40 rounded-full h-2">
                      <motion.div
                        initial={{ width: 0 }}
                        animate={{ width: `${topic.completionPercentage || 0}%` }}
                        className="h-2 rounded-full bg-gradient-to-r from-primary to-primary-container"
                      />
                    </div>
                    <p className="text-xs text-on-surface-variant/60 mt-1">{topic.completionPercentage?.toFixed(0)}% completion</p>
                  </div>
                )}

                {topic.totalAttempts === 0 && (
                  <div className="pt-3 border-t border-white/20">
                    <p className="text-sm text-on-surface-variant/50 italic">Not studied yet</p>
                  </div>
                )}
              </motion.div>
            ))}
            {(!detail.topics || detail.topics.length === 0) && (
              <div className="text-center py-10 text-on-surface-variant/50">
                <Brain className="w-10 h-10 mx-auto mb-2 opacity-40" />
                <p>No topics found for this PDF. Analyze it first.</p>
              </div>
            )}
          </div>
        </div>
      </motion.div>
    </motion.div>
  )
}
