import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { pdfAPI, dashboardAPI, adminAPI } from '../api'
import { useCountUp } from '../hooks/useCountUp'
import { motion } from 'framer-motion'
import { Upload, BookOpen, TrendingUp, Zap, Target, BarChart3, ArrowRight, FileText, Users, Database, Brain, ClipboardList, AlertCircle } from 'lucide-react'

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.07 } } }
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } } }

function GlassStatCard({ icon: Icon, label, value, color = 'text-primary' }) {
  const count = useCountUp(value, 800)
  return (
    <motion.div variants={item} className="glass-pane rounded-xl p-5 border border-black/8 hover:bg-white/85 transition-all duration-200">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-on-surface-variant/70 font-medium mb-1">{label}</p>
          <p className={`text-3xl font-bold ${color}`}>{count}</p>
        </div>
        <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
          <Icon className={`w-5 h-5 ${color}`} />
        </div>
      </div>
    </motion.div>
  )
}

function AdminDashboard() {
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    adminAPI.dashboard()
      .then(res => setStats(res.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="animate-spin rounded-full h-10 w-10 border-2 border-primary border-t-transparent mx-auto mb-4" />
      </div>
    )
  }

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-8">
      <motion.div variants={item}>
        <h1 className="text-2xl md:text-[40px] font-semibold text-on-surface leading-[48px]">Admin Dashboard</h1>
        <p className="text-lg text-on-surface-variant/70 mt-1">System overview and management</p>
      </motion.div>

      <motion.div variants={item} className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        <GlassStatCard icon={Users} label="Users" value={stats?.User || 0} color="text-primary" />
        <GlassStatCard icon={FileText} label="PDFs" value={stats?.PdfDocument || 0} color="text-secondary" />
        <GlassStatCard icon={Brain} label="Topics" value={stats?.Topic || 0} color="text-emerald-600" />
        <GlassStatCard icon={ClipboardList} label="Quizzes" value={stats?.Quiz || 0} color="text-violet-600" />
        <GlassStatCard icon={BarChart3} label="Attempts" value={stats?.QuizAttempt || 0} color="text-orange-600" />
        <GlassStatCard icon={TrendingUp} label="Progress" value={stats?.StudyProgress || 0} color="text-cyan-600" />
      </motion.div>

      <motion.div variants={item} className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <button onClick={() => navigate('/admin')} className="glass-pane rounded-xl p-6 border border-black/8 text-left hover:bg-white/85 transition-all group">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center">
              <Database className="w-6 h-6 text-primary" />
            </div>
            <div>
              <h3 className="text-lg font-semibold text-on-surface">Database Manager</h3>
              <p className="text-sm text-on-surface-variant/70">Browse, inspect and manage all database tables</p>
            </div>
            <ArrowRight className="w-5 h-5 text-on-surface-variant/30 group-hover:text-primary ml-auto" />
          </div>
        </button>

        <button onClick={() => navigate('/dashboard')} className="glass-pane rounded-xl p-6 border border-black/8 text-left hover:bg-white/85 transition-all group">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center">
              <Users className="w-6 h-6 text-primary" />
            </div>
            <div>
              <h3 className="text-lg font-semibold text-on-surface">User Dashboard</h3>
              <p className="text-sm text-on-surface-variant/70">Switch to the student study overview</p>
            </div>
            <ArrowRight className="w-5 h-5 text-on-surface-variant/30 group-hover:text-primary ml-auto" />
          </div>
        </button>
      </motion.div>
    </motion.div>
  )
}

export const Dashboard = () => {
  const [dashboard, setDashboard] = useState(null)
  const [pdfs, setPdfs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const { user, isAdmin } = useAuth()
  const navigate = useNavigate()

  useEffect(() => { fetchDashboard() }, [])

  const fetchDashboard = async () => {
    try {
      const [dashRes, pdfRes] = await Promise.all([dashboardAPI.get(), pdfAPI.list()])
      setDashboard(dashRes.data)
      setPdfs(pdfRes.data)
      setError('')
    } catch (err) {
      setError('Failed to load dashboard')
      console.error(err)
    } finally { setLoading(false) }
  }

  if (isAdmin) return <AdminDashboard />

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="text-center animate-fade-in">
          <div className="animate-spin rounded-full h-10 w-10 border-2 border-primary border-t-transparent mx-auto mb-4" />
          <p className="text-on-surface-variant">Loading dashboard...</p>
        </div>
      </div>
    )
  }

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-8">
      {error && (
        <motion.div variants={item} className="glass-pane rounded-xl p-4 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{error}</p>
        </motion.div>
      )}

      <motion.div variants={item}>
        <h1 className="text-2xl md:text-[40px] font-semibold text-on-surface leading-[48px]">
          Welcome back, {user?.name?.split(' ')[0] || 'Student'}
        </h1>
        <p className="text-lg text-on-surface-variant/70 mt-1">Here's your study overview</p>
      </motion.div>

      <motion.div variants={item} className="grid grid-cols-2 md:grid-cols-4 gap-5">
        <GlassStatCard icon={BookOpen} label="Total PDFs" value={dashboard?.totalPdfs || 0} color="text-primary" />
        <GlassStatCard icon={Target} label="Topics" value={dashboard?.totalTopics || 0} color="text-secondary" />
        <GlassStatCard icon={TrendingUp} label="Avg Score" value={Math.round(dashboard?.averageScore || 0)} color="text-emerald-600" />
        <GlassStatCard icon={Zap} label="Days Left" value={dashboard?.daysUntilExam || 0} color="text-orange-600" />
      </motion.div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <motion.div variants={item} className="lg:col-span-2">
          <div className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-2xl font-semibold text-on-surface mb-5 flex items-center gap-2">
              <BarChart3 className="w-5 h-5 text-primary" />
              Study Progress
            </h2>
            <div className="space-y-4">
              {dashboard?.rankedTopics?.slice(0, 5).map((topic, i) => (
                <motion.div
                  key={topic.topicId}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.08 }}
                  className="p-4 rounded-xl bg-white/40 backdrop-blur-sm border border-black/8"
                >
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-semibold text-on-surface">{topic.topicTitle}</h3>
                    <span className="text-xs px-2.5 py-1 rounded-full bg-primary/10 text-primary font-medium">{topic.weaknessLevel}</span>
                  </div>
                  <div className="w-full h-2 rounded-full bg-white/40">
                    <motion.div
                      initial={{ width: 0 }}
                      animate={{ width: `${topic.completionPercentage}%` }}
                      transition={{ duration: 0.8, delay: i * 0.08 + 0.3, ease: 'easeOut' }}
                      className="h-2 rounded-full bg-gradient-to-r from-primary to-primary-container"
                    />
                  </div>
                  <p className="text-xs text-on-surface-variant/60 mt-2">
                    {topic.correctAttempts}/{topic.totalAttempts} correct &middot; Best: {topic.bestScore?.toFixed(1)}%
                  </p>
                </motion.div>
              ))}
              {(!dashboard?.rankedTopics || dashboard.rankedTopics.length === 0) && (
                <div className="text-center py-10 text-on-surface-variant/50">
                  <BarChart3 className="w-10 h-10 mx-auto mb-2 opacity-40" />
                  <p>No study data yet. Upload a PDF to get started!</p>
                </div>
              )}
            </div>
          </div>
        </motion.div>

        <motion.div variants={item} className="space-y-6">
          <div className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-lg font-bold text-on-surface mb-4">Quick Actions</h2>
            <button onClick={() => navigate('/upload')} className="btn-glass-primary w-full mb-3">
              <Upload className="w-4 h-4" />
              Upload PDF
              <ArrowRight className="w-4 h-4" />
            </button>
            <button onClick={() => navigate('/study')} className="btn-glass-secondary w-full">
              <BookOpen className="w-4 h-4" />
              Start Studying
            </button>
          </div>

          <div className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-lg font-bold text-on-surface mb-4">Weak Topics</h2>
            <div className="space-y-2">
              {dashboard?.weakTopics?.slice(0, 3).map((topic, i) => (
                <motion.div
                  key={topic.topicId}
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: i * 0.08 }}
                  className="glass-pane-sm rounded-xl p-3 border-red-200/50"
                >
                  <p className="text-sm font-semibold text-red-700">{topic.topicTitle}</p>
                  <p className="text-xs text-red-700/70 mt-0.5">Score: {topic.bestScore?.toFixed(1)}%</p>
                </motion.div>
              ))}
              {(!dashboard?.weakTopics || dashboard.weakTopics.length === 0) && (
                <p className="text-sm text-on-surface-variant/50 text-center py-4">No weak topics &mdash; great job!</p>
              )}
            </div>
          </div>
        </motion.div>
      </div>

      <motion.div variants={item}>
        <div className="glass-pane rounded-xl p-6 border border-black/8">
          <h2 className="text-2xl font-semibold text-on-surface mb-5 flex items-center gap-2">
            <FileText className="w-5 h-5 text-primary" />
            Your PDFs
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {pdfs.map((pdf, i) => (
              <motion.div
                key={pdf.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.04 }}
                onClick={() => navigate(`/pdf/${pdf.id}`)}
                className="p-4 rounded-xl bg-white/40 backdrop-blur-sm border border-black/8 hover:bg-white/60 transition-all cursor-pointer"
              >
                <h3 className="font-semibold text-on-surface truncate">{pdf.fileName}</h3>
                <p className="text-xs text-on-surface-variant/60 mt-1">Exam: {new Date(pdf.examDate).toLocaleDateString()}</p>
                <p className="text-xs text-primary font-medium mt-2">{pdf.topicCount} topics &bull; {pdf.isAnalyzed ? 'Analyzed' : 'Pending'}</p>
              </motion.div>
            ))}
            {pdfs.length === 0 && (
              <div className="col-span-full text-center py-8 text-on-surface-variant/50">
                <BookOpen className="w-8 h-8 mx-auto mb-2 opacity-40" />
                <p>No PDFs uploaded yet</p>
              </div>
            )}
          </div>
        </div>
      </motion.div>
    </motion.div>
  )
}
