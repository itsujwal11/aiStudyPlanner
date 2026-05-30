import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { plannerAPI } from '../api'
import {
  ListChecks, Target, TrendingUp, Calendar, Clock, AlertTriangle,
  CheckCircle, XCircle, BookOpen, Zap, BarChart3, ArrowRight,
  BrainCircuit, CalendarDays, GraduationCap, Lightbulb, ChevronRight
} from 'lucide-react'
import { motion } from 'framer-motion'

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.07 } } }
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35 } } }

export const Planner = () => {
  const [planner, setPlanner] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [completedTasks, setCompletedTasks] = useState(new Set())
  const navigate = useNavigate()

  useEffect(() => { fetchPlanner() }, [])

  const fetchPlanner = async () => {
    try {
      const response = await plannerAPI.get()
      setPlanner(response.data)
      setError('')
    } catch (err) {
      console.error('Error fetching planner:', err)
      setError('Failed to load planner')
    } finally { setLoading(false) }
  }

  const toggleTaskComplete = (taskIndex) => {
    setCompletedTasks(prev => {
      const newSet = new Set(prev)
      if (newSet.has(taskIndex)) newSet.delete(taskIndex)
      else newSet.add(taskIndex)
      return newSet
    })
  }

  const getComplexityColor = (level) => {
    switch (level) {
      case 'HARD': return 'text-red-600 bg-red-500/10'
      case 'MEDIUM': return 'text-yellow-600 bg-yellow-500/10'
      case 'EASY': return 'text-emerald-600 bg-emerald-500/10'
      default: return 'text-slate-500 bg-slate-500/10'
    }
  }

  const getPriorityColor = (level) => {
    switch (level) {
      case 'HIGH': return 'text-red-600'
      case 'MEDIUM': return 'text-yellow-600'
      case 'LOW': return 'text-slate-500'
      default: return 'text-slate-500'
    }
  }

  const getWeaknessBadge = (score) => {
    if (score >= 0.7) return { label: 'Critical', color: 'text-red-600 bg-red-500/10 border-red-500/20' }
    if (score >= 0.4) return { label: 'Needs Work', color: 'text-yellow-600 bg-yellow-500/10 border-yellow-500/20' }
    if (score >= 0.2) return { label: 'Fair', color: 'text-blue-600 bg-blue-500/10 border-blue-500/20' }
    return { label: 'Strong', color: 'text-emerald-600 bg-emerald-500/10 border-emerald-500/20' }
  }

  const getActivityIcon = (type) => {
    switch (type) {
      case 'LEARN': return <BookOpen className="w-4 h-4 text-cyan-600" />
      case 'REVISION': return <GraduationCap className="w-4 h-4 text-blue-600" />
      case 'PRACTICE': return <Zap className="w-4 h-4 text-purple-600" />
      case 'TEST': return <Target className="w-4 h-4 text-orange-600" />
      default: return <BookOpen className="w-4 h-4 text-slate-400" />
    }
  }

  const getMasteryColor = (mastery) => {
    if (mastery >= 80) return 'bg-emerald-500'
    if (mastery >= 60) return 'bg-blue-500'
    if (mastery >= 40) return 'bg-yellow-500'
    return 'bg-red-500'
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-2 border-primary border-t-transparent mx-auto mb-4"></div>
          <p className="text-on-surface font-semibold">Generating your study planner...</p>
          <p className="text-on-surface-variant/50 text-sm mt-1">Analyzing your progress and building roadmap</p>
        </div>
      </div>
    )
  }

  if (error || !planner || planner.totalTopics === 0) {
    return (
      <div className="flex items-center justify-center min-h-[60vh] px-4">
        <div className="glass-pane rounded-xl p-8 max-w-md text-center">
          <Target className="w-16 h-16 text-primary mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-on-surface mb-2">No Study Plan Yet</h2>
          <p className="text-on-surface-variant/70 mb-6">Upload a PDF and analyze it to generate your personalized study planner.</p>
          <button onClick={() => navigate('/upload')} className="btn-glass-primary">
            Upload PDF
          </button>
        </div>
      </div>
    )
  }

  const completedCount = completedTasks.size
  const totalTasksToday = planner.todayTasks?.length || 0

  return (
    <motion.div variants={container} initial="hidden" animate="show">
      {error && (
        <motion.div variants={item} className="glass-pane rounded-xl p-4 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3 mb-6">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p>{error}</p>
        </motion.div>
      )}

      <motion.div variants={item} className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 mb-8">
        <div className="glass-pane rounded-xl p-5 border border-black/8 hover:bg-white/85 transition">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-on-surface-variant/70 font-medium mb-1">Today's Tasks</p>
              <p className="text-3xl font-bold text-primary">{completedCount}/{totalTasksToday}</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
              <ListChecks className="w-5 h-5 text-primary" />
            </div>
          </div>
          {totalTasksToday > 0 && (
            <div className="mt-3 w-full bg-white/40 rounded-full h-1.5">
              <div className="bg-gradient-to-r from-primary to-blue-500 h-1.5 rounded-full transition-all" style={{ width: `${(completedCount / totalTasksToday) * 100}%` }}></div>
            </div>
          )}
        </div>

        <div className="glass-pane rounded-xl p-5 border border-black/8 hover:bg-white/85 transition">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-on-surface-variant/70 font-medium mb-1">Topics</p>
              <p className="text-3xl font-bold text-blue-600">{planner.totalTopics}</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
              <BookOpen className="w-5 h-5 text-blue-600" />
            </div>
          </div>
        </div>

        <div className="glass-pane rounded-xl p-5 border border-black/8 hover:bg-white/85 transition">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-on-surface-variant/70 font-medium mb-1">Weak Topics</p>
              <p className="text-3xl font-bold text-red-600">{planner.weakTopicsCount}</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-red-500/10 flex items-center justify-center flex-shrink-0">
              <AlertTriangle className="w-5 h-5 text-red-600" />
            </div>
          </div>
        </div>

        <div className="glass-pane rounded-xl p-5 border border-black/8 hover:bg-white/85 transition">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-on-surface-variant/70 font-medium mb-1">Avg Mastery</p>
              <p className={`text-3xl font-bold ${planner.averageMastery >= 70 ? 'text-emerald-600' : planner.averageMastery >= 50 ? 'text-yellow-600' : 'text-red-600'}`}>
                {planner.averageMastery?.toFixed(1) || 0}%
              </p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-emerald-500/10 flex items-center justify-center flex-shrink-0">
              <BarChart3 className={`w-5 h-5 ${planner.averageMastery >= 70 ? 'text-emerald-600' : planner.averageMastery >= 50 ? 'text-yellow-600' : 'text-red-600'}`} />
            </div>
          </div>
        </div>

        <div className="glass-pane rounded-xl p-5 border border-black/8 hover:bg-white/85 transition">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-on-surface-variant/70 font-medium mb-1">Study Time</p>
              <p className="text-3xl font-bold text-purple-600">{planner.totalDurationMinutesToday || 0}m</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-purple-500/10 flex items-center justify-center flex-shrink-0">
              <Clock className="w-5 h-5 text-purple-600" />
            </div>
          </div>
        </div>

        <div className="glass-pane rounded-xl p-5 border border-black/8 hover:bg-white/85 transition">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-on-surface-variant/70 font-medium mb-1">Days Left</p>
              <p className="text-3xl font-bold text-orange-600">{planner.daysUntilExam || 0}</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-orange-500/10 flex items-center justify-center flex-shrink-0">
              <Calendar className="w-5 h-5 text-orange-600" />
            </div>
          </div>
        </div>
      </motion.div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-lg font-bold text-on-surface flex items-center gap-2">
                <ListChecks className="w-5 h-5 text-primary" />
                Today's Tasks
                {totalTasksToday > 0 && <span className="text-sm font-normal text-on-surface-variant/70">({completedCount}/{totalTasksToday} completed)</span>}
              </h2>
              <span className="text-sm text-on-surface-variant/70 bg-white/40 px-3 py-1 rounded-lg">~{planner.totalDurationMinutesToday}m total</span>
            </div>

            {planner.todayTasks?.length === 0 ? (
              <div className="text-center py-8 text-on-surface-variant/70">
                <CheckCircle className="w-10 h-10 mx-auto mb-2 text-emerald-400" />
                <p>All tasks completed for today!</p>
              </div>
            ) : (
              <div className="space-y-2">
                {planner.todayTasks?.map((task, index) => {
                  const isCompleted = completedTasks.has(index)
                  return (
                    <motion.div
                      key={index}
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: index * 0.05 }}
                      className={`p-4 rounded-xl border transition-all cursor-pointer ${isCompleted ? 'bg-emerald-500/5 border-emerald-500/20' : 'glass-pane-sm border border-black/8 hover:border-primary/30'}`}
                      onClick={() => toggleTaskComplete(index)}
                    >
                      <div className="flex items-start gap-3">
                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 mt-0.5 transition-all ${isCompleted ? 'bg-emerald-500 border-emerald-500' : 'border-white/40 hover:border-primary'}`}>
                          {isCompleted && <CheckCircle className="w-4 h-4 text-white" />}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-start justify-between mb-1">
                            <div className="flex items-center gap-2">
                              {getActivityIcon(task.activityType)}
                              <h3 className={`font-semibold text-sm ${isCompleted ? 'text-on-surface-variant/40 line-through' : 'text-on-surface'}`}>{task.topicTitle}</h3>
                            </div>
                            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${getComplexityColor(task.complexityLevel)}`}>{task.complexityLevel}</span>
                          </div>
                          <div className="flex items-center gap-3 text-xs text-on-surface-variant/70 mt-1">
                            <span className="font-medium">{task.activityType}</span>
                            <span className="flex items-center gap-1"><Clock className="w-3 h-3" />{task.estimatedDurationMinutes}m</span>
                            <span className={`font-medium ${getPriorityColor(task.priorityLevel)}`}>{task.priorityLevel} Priority</span>
                          </div>
                        </div>
                      </div>
                    </motion.div>
                  )
                })}
              </div>
            )}
          </motion.div>

          <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-lg font-bold text-on-surface mb-5 flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 text-red-500" />
              Weakness Analysis
              {planner.weakTopicsCount > 0 && <span className="text-sm font-normal text-red-500">({planner.weakTopicsCount} topics need attention)</span>}
            </h2>

            {planner.weakTopics?.length === 0 ? (
              <div className="text-center py-8 text-on-surface-variant/70">
                <CheckCircle className="w-10 h-10 mx-auto mb-2 text-emerald-400" />
                <p className="font-semibold text-emerald-600">No weak topics detected!</p>
                <p className="text-sm mt-1">Keep up the good work with regular revision.</p>
              </div>
            ) : (
              <div className="space-y-4">
                {planner.weakTopics?.map((topic) => {
                  const badge = getWeaknessBadge(topic.weaknessScore)
                  return (
                    <div key={topic.topicId} className="glass-pane-sm rounded-xl p-4 border border-black/8">
                      <div className="flex items-start justify-between mb-3">
                        <div className="flex-1 min-w-0">
                          <h3 className="font-semibold text-on-surface">{topic.topicTitle}</h3>
                          <p className="text-xs text-on-surface-variant/70 mt-1 leading-relaxed">{topic.whyImportant}</p>
                        </div>
                        <span className={`text-xs px-2 py-1 rounded-full border whitespace-nowrap ml-3 ${badge.color}`}>{badge.label}</span>
                      </div>

                      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                        <div className="bg-white/80 backdrop-blur-sm border border-black/6 rounded-lg p-2.5">
                          <p className="text-[10px] text-on-surface-variant/60 uppercase tracking-wider">Weakness</p>
                          <p className="text-sm font-bold text-red-600">{(topic.weaknessScore * 100).toFixed(0)}%</p>
                        </div>
                        <div className="bg-white/80 backdrop-blur-sm border border-black/6 rounded-lg p-2.5">
                          <p className="text-[10px] text-on-surface-variant/60 uppercase tracking-wider">Mastery</p>
                          <p className={`text-sm font-bold ${topic.masteryLevel >= 70 ? 'text-emerald-600' : topic.masteryLevel >= 50 ? 'text-yellow-600' : 'text-red-600'}`}>{topic.masteryLevel.toFixed(1)}%</p>
                        </div>
                        <div className="bg-white/80 backdrop-blur-sm border border-black/6 rounded-lg p-2.5">
                          <p className="text-[10px] text-on-surface-variant/60 uppercase tracking-wider">Importance</p>
                          <p className="text-sm font-bold text-blue-600">{(topic.importanceScore * 100).toFixed(0)}%</p>
                        </div>
                        <div className="bg-white/80 backdrop-blur-sm border border-black/6 rounded-lg p-2.5">
                          <p className="text-[10px] text-on-surface-variant/60 uppercase tracking-wider">Complexity</p>
                          <p className="text-sm font-bold text-purple-600">{(topic.complexityScore * 100).toFixed(0)}%</p>
                        </div>
                      </div>

                      <div className="mt-3">
                        <div className="flex justify-between text-xs text-on-surface-variant/60 mb-1">
                          <span>Progress</span>
                          <span>{topic.correctAttempts}/{topic.totalAttempts} correct</span>
                        </div>
                        <div className="w-full bg-white/40 rounded-full h-2">
                          <div className={`h-2 rounded-full transition-all ${getMasteryColor(topic.masteryLevel)}`} style={{ width: `${topic.masteryLevel}%` }}></div>
                        </div>
                      </div>

                      <div className="mt-3 flex items-center justify-between text-xs">
                        <span className="text-on-surface-variant/70"><Clock className="w-3 h-3 inline mr-1" />{topic.recommendedDuration}</span>
                        <span className="text-on-surface-variant/70">{topic.daysUntilExam > 0 ? `${topic.daysUntilExam} days until exam` : 'Exam overdue'}</span>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </motion.div>

          <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-lg font-bold text-on-surface mb-5 flex items-center gap-2">
              <Target className="w-5 h-5 text-primary" />
              Priority Topics
              <span className="text-sm font-normal text-on-surface-variant/70">(ranked by priority score)</span>
            </h2>
            <div className="space-y-2">
              {planner.priorityTopics?.slice(0, 8).map((topic, index) => (
                <motion.div key={topic.topicId} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: index * 0.05 }}
                  className="flex items-center gap-4 p-3 rounded-xl glass-pane-sm border border-black/8">
                  <span className={`w-7 h-7 rounded-lg flex items-center justify-center text-xs font-bold ${index < 3 ? 'bg-primary/10 text-primary' : 'bg-white/40 text-on-surface-variant/70'}`}>{index + 1}</span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-on-surface truncate">{topic.topicTitle}</p>
                    <div className="flex items-center gap-3 mt-1">
                      <span className={`text-[10px] px-1.5 py-0.5 rounded ${getComplexityColor(topic.complexityScore >= 0.7 ? 'HARD' : topic.complexityScore >= 0.4 ? 'MEDIUM' : 'EASY')}`}>
                        {topic.complexityScore >= 0.7 ? 'HARD' : topic.complexityScore >= 0.4 ? 'MEDIUM' : 'EASY'}
                      </span>
                      <div className="flex-1 max-w-[120px] bg-white/40 rounded-full h-1.5">
                        <motion.div initial={{ width: 0 }} animate={{ width: `${topic.masteryLevel}%` }} className={`h-1.5 rounded-full ${getMasteryColor(topic.masteryLevel)}`} transition={{ duration: 0.5 }} />
                      </div>
                      <span className="text-[10px] text-on-surface-variant/70">{topic.masteryLevel.toFixed(0)}%</span>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-xs font-bold text-primary">{topic.priorityScore.toFixed(2)}</p>
                    <p className="text-[10px] text-on-surface-variant/70">priority</p>
                  </div>
                </motion.div>
              ))}
            </div>
          </motion.div>
        </div>

        <div className="space-y-6">
          <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-lg font-bold text-on-surface mb-4 flex items-center gap-2">
              <Lightbulb className="w-5 h-5 text-yellow-500" />
              Recommendations
            </h2>
            <div className="overflow-x-auto -mx-6 px-6">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-black/8">
                    <th className="text-left text-xs font-bold text-on-surface-variant/70 uppercase tracking-wider pb-3 pr-4">#</th>
                    <th className="text-left text-xs font-bold text-on-surface-variant/70 uppercase tracking-wider pb-3">Recommendation</th>
                  </tr>
                </thead>
                <tbody>
                  {planner.recommendations?.map((rec, i) => (
                    <tr key={i} className="border-b border-black/8 last:border-b-0">
                      <td className="py-3 pr-4 align-top">
                        <span className="w-6 h-6 rounded-lg bg-primary/10 text-primary flex items-center justify-center text-xs font-bold">{i + 1}</span>
                      </td>
                      <td className="py-3 text-sm text-on-surface/80 leading-relaxed">{rec}</td>
                    </tr>
                  ))}
                  {(!planner.recommendations || planner.recommendations.length === 0) && (
                    <tr><td colSpan="2" className="py-6 text-center text-on-surface-variant/50 text-sm">No recommendations available.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </motion.div>

          <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-lg font-bold text-on-surface mb-4 flex items-center gap-2">
              <CalendarDays className="w-5 h-5 text-blue-500" />
              Study Roadmap
            </h2>
            <div className="space-y-2 max-h-[500px] overflow-y-auto pr-1">
              {planner.studyRoadmap?.slice(0, 21).map((item, i) => (
                <motion.div key={i} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.03 }}
                  className="flex items-start gap-3 p-3 rounded-xl glass-pane-sm border border-black/8">
                  <div className="flex-shrink-0 w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center">
                    <span className="text-xs font-bold text-primary">D{item.day}</span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      {getActivityIcon(item.activityType)}
                      <p className="text-sm font-semibold text-on-surface truncate">{item.topicTitle}</p>
                    </div>
                    <div className="flex items-center gap-2 mt-1 text-[10px] text-on-surface-variant/70">
                      <span>{item.activityType}</span>
                      <span>•</span>
                      <span>{item.estimatedDurationMinutes}m</span>
                      <span className={`px-1.5 py-0.5 rounded ${getComplexityColor(item.complexityLevel)}`}>{item.complexityLevel}</span>
                    </div>
                  </div>
                </motion.div>
              ))}
            </div>
          </motion.div>

          <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-lg font-bold text-on-surface mb-4 flex items-center gap-2">
              <GraduationCap className="w-5 h-5 text-purple-500" />
              Revision Schedule
            </h2>
            <div className="space-y-2">
              {planner.revisionSchedule?.slice(0, 6).map((item, i) => {
                const isUrgent = item.weaknessScore >= 0.7
                return (
                  <motion.div key={i} initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: i * 0.05 }}
                    className={`p-3 rounded-xl border ${isUrgent ? 'bg-red-500/5 border-red-500/20' : 'glass-pane-sm border border-black/8'}`}>
                    <div className="flex items-center justify-between">
                      <p className="text-sm font-semibold text-on-surface truncate flex-1">{item.topicTitle}</p>
                      {isUrgent && <AlertTriangle className="w-3 h-3 text-red-500 ml-2 flex-shrink-0" />}
                    </div>
                    <div className="flex items-center gap-3 mt-1.5 text-[10px] text-on-surface-variant/70">
                      <span className="flex items-center gap-1"><Calendar className="w-3 h-3" />{new Date(item.revisionDate).toLocaleDateString()}</span>
                      <span className="flex items-center gap-1"><Clock className="w-3 h-3" />{item.frequency}</span>
                    </div>
                    <div className="mt-1.5 w-full bg-white/40 rounded-full h-1">
                      <div className={`h-1 rounded-full ${item.weaknessScore >= 0.7 ? 'bg-red-500' : item.weaknessScore >= 0.4 ? 'bg-yellow-500' : 'bg-emerald-500'}`}
                        style={{ width: `${(1 - item.weaknessScore) * 100}%` }} />
                    </div>
                  </motion.div>
                )
              })}
            </div>
          </motion.div>

          {planner.practiceDays?.length > 0 && (
            <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
              <h2 className="text-lg font-bold text-on-surface mb-4 flex items-center gap-2">
                <Target className="w-5 h-5 text-orange-500" />
                Practice & Test Days
              </h2>
              <div className="flex flex-wrap gap-2">
                {planner.practiceDays.map((day, i) => (
                  <span key={i} className="px-3 py-1.5 rounded-lg bg-orange-600/10 border border-orange-600/20 text-orange-600 text-sm font-medium">Day {day}</span>
                ))}
              </div>
            </motion.div>
          )}

          <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-lg font-bold text-on-surface mb-4">Quick Actions</h2>
            <div className="space-y-3">
              <button onClick={() => navigate('/study')} className="btn-glass-primary w-full flex items-center justify-center gap-2">
                <BookOpen className="w-4 h-4" /> Start Studying <ArrowRight className="w-4 h-4" />
              </button>
              <button onClick={() => navigate('/upload')} className="btn-glass-secondary w-full flex items-center justify-center gap-2">
                <ChevronRight className="w-4 h-4" /> Upload New PDF
              </button>
            </div>
          </motion.div>
        </div>
      </div>
    </motion.div>
  )
}
