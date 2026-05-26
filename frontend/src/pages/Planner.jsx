import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Navigation } from '../components/Navigation'
import { plannerAPI } from '../api'
import {
  ListChecks, Target, TrendingUp, Calendar, Clock, AlertTriangle,
  CheckCircle, XCircle, BookOpen, Zap, BarChart3, ArrowRight,
  BrainCircuit, CalendarDays, GraduationCap, Lightbulb, ChevronRight
} from 'lucide-react'

export const Planner = () => {
  const [planner, setPlanner] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [completedTasks, setCompletedTasks] = useState(new Set())
  const navigate = useNavigate()

  useEffect(() => {
    fetchPlanner()
  }, [])

  const fetchPlanner = async () => {
    try {
      const response = await plannerAPI.get()
      setPlanner(response.data)
      setError('')
    } catch (err) {
      console.error('Error fetching planner:', err)
      setError('Failed to load planner')
    } finally {
      setLoading(false)
    }
  }

  const toggleTaskComplete = (taskIndex) => {
    setCompletedTasks(prev => {
      const newSet = new Set(prev)
      if (newSet.has(taskIndex)) {
        newSet.delete(taskIndex)
      } else {
        newSet.add(taskIndex)
      }
      return newSet
    })
  }

  const getComplexityColor = (level) => {
    switch (level) {
      case 'HARD': return 'text-red-400 bg-red-500/10'
      case 'MEDIUM': return 'text-yellow-400 bg-yellow-500/10'
      case 'EASY': return 'text-emerald-400 bg-emerald-500/10'
      default: return 'text-slate-400 bg-slate-500/10'
    }
  }

  const getPriorityColor = (level) => {
    switch (level) {
      case 'HIGH': return 'text-red-400'
      case 'MEDIUM': return 'text-yellow-400'
      case 'LOW': return 'text-slate-400'
      default: return 'text-slate-400'
    }
  }

  const getWeaknessBadge = (score) => {
    if (score >= 0.7) return { label: 'Critical', color: 'text-red-400 bg-red-500/10 border-red-500/20' }
    if (score >= 0.4) return { label: 'Needs Work', color: 'text-yellow-400 bg-yellow-500/10 border-yellow-500/20' }
    if (score >= 0.2) return { label: 'Fair', color: 'text-blue-400 bg-blue-500/10 border-blue-500/20' }
    return { label: 'Strong', color: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' }
  }

  const getActivityIcon = (type) => {
    switch (type) {
      case 'LEARN': return <BookOpen className="w-4 h-4 text-cyan-400" />
      case 'REVISION': return <GraduationCap className="w-4 h-4 text-blue-400" />
      case 'PRACTICE': return <Zap className="w-4 h-4 text-purple-400" />
      case 'TEST': return <Target className="w-4 h-4 text-orange-400" />
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
      <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
        <Navigation currentPage="planner" />
        <div className="flex items-center justify-center min-h-[80vh]">
          <div className="text-center">
            <div className="animate-spin rounded-full h-16 w-16 border-4 border-cyan-500/20 border-t-cyan-500 mx-auto mb-6"></div>
            <p className="text-slate-300 text-lg font-semibold">Generating your study planner...</p>
            <p className="text-slate-500 text-sm mt-2">Analyzing your progress and building roadmap</p>
          </div>
        </div>
      </div>
    )
  }

  if (error || !planner || planner.totalTopics === 0) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
        <Navigation currentPage="planner" />
        <div className="flex items-center justify-center min-h-[80vh] px-4">
          <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-2xl p-8 max-w-md text-center">
            <Target className="w-16 h-16 text-cyan-400 mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-slate-100 mb-2">No Study Plan Yet</h2>
            <p className="text-slate-400 mb-6">Upload a PDF and analyze it to generate your personalized study planner.</p>
            <button
              onClick={() => navigate('/upload')}
              className="bg-gradient-to-r from-cyan-500 to-blue-500 text-white font-semibold py-3 px-6 rounded-xl hover:from-cyan-600 hover:to-blue-600 transition-all"
            >
              Upload PDF
            </button>
          </div>
        </div>
      </div>
    )
  }

  const completedCount = completedTasks.size
  const totalTasksToday = planner.todayTasks?.length || 0

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
      <Navigation currentPage="planner" />

      <main className="max-w-7xl mx-auto px-6 py-8">
        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400">
            {error}
          </div>
        )}

        {/* Header Stats */}
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4 mb-8">
          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-xs">Today's Tasks</p>
                <p className="text-2xl font-bold text-cyan-400">{completedCount}/{totalTasksToday}</p>
              </div>
              <ListChecks className="w-6 h-6 text-cyan-500/30" />
            </div>
            {totalTasksToday > 0 && (
              <div className="mt-2 w-full bg-slate-700 rounded-full h-1.5">
                <div
                  className="bg-gradient-to-r from-cyan-500 to-blue-500 h-1.5 rounded-full transition-all"
                  style={{ width: `${(completedCount / totalTasksToday) * 100}%` }}
                ></div>
              </div>
            )}
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-xs">Topics</p>
                <p className="text-2xl font-bold text-blue-400">{planner.totalTopics}</p>
              </div>
              <BookOpen className="w-6 h-6 text-blue-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-xs">Weak Topics</p>
                <p className="text-2xl font-bold text-red-400">{planner.weakTopicsCount}</p>
              </div>
              <AlertTriangle className="w-6 h-6 text-red-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-xs">Avg Mastery</p>
                <p className={`text-2xl font-bold ${
                  planner.averageMastery >= 70 ? 'text-emerald-400' :
                  planner.averageMastery >= 50 ? 'text-yellow-400' : 'text-red-400'
                }`}>{planner.averageMastery?.toFixed(1) || 0}%</p>
              </div>
              <BarChart3 className="w-6 h-6 text-emerald-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-xs">Study Time</p>
                <p className="text-2xl font-bold text-purple-400">{planner.totalDurationMinutesToday || 0}m</p>
              </div>
              <Clock className="w-6 h-6 text-purple-500/30" />
            </div>
          </div>

          <div className="card">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-slate-400 text-xs">Days Left</p>
                <p className="text-2xl font-bold text-orange-400">{planner.daysUntilExam || 0}</p>
              </div>
              <Calendar className="w-6 h-6 text-orange-500/30" />
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Left Column - Today's Tasks & Weakness */}
          <div className="lg:col-span-2 space-y-8">
            {/* Today's Tasks - Execution Engine */}
            <div className="card">
              <div className="flex items-center justify-between mb-6">
                <h2 className="text-xl font-bold flex items-center gap-2">
                  <ListChecks className="w-5 h-5 text-cyan-400" />
                  Today's Tasks
                  {totalTasksToday > 0 && (
                    <span className="text-sm font-normal text-slate-400">
                      ({completedCount}/{totalTasksToday} completed)
                    </span>
                  )}
                </h2>
                <span className="text-sm text-slate-400 bg-slate-800 px-3 py-1 rounded-lg">
                  ~{planner.totalDurationMinutesToday}m total
                </span>
              </div>

              {planner.todayTasks?.length === 0 ? (
                <div className="text-center py-8 text-slate-500">
                  <CheckCircle className="w-12 h-12 mx-auto mb-3 text-emerald-500/50" />
                  <p>All tasks completed for today! Great work.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {planner.todayTasks?.map((task, index) => {
                    const isCompleted = completedTasks.has(index)
                    return (
                      <div
                        key={index}
                        className={`p-4 rounded-xl border transition-all cursor-pointer ${
                          isCompleted
                            ? 'bg-emerald-500/5 border-emerald-500/20'
                            : 'bg-slate-800/50 border-slate-700 hover:border-cyan-500/30 hover:bg-slate-800/70'
                        }`}
                        onClick={() => toggleTaskComplete(index)}
                      >
                        <div className="flex items-start gap-4">
                          {/* Checkbox */}
                          <div className={`w-6 h-6 rounded-full border-2 flex items-center justify-center flex-shrink-0 mt-1 transition-all ${
                            isCompleted
                              ? 'bg-emerald-500 border-emerald-500'
                              : 'border-slate-600 hover:border-cyan-500'
                          }`}>
                            {isCompleted && <CheckCircle className="w-4 h-4 text-white" />}
                          </div>

                          <div className="flex-1 min-w-0">
                            <div className="flex items-start justify-between mb-2">
                              <div>
                                <div className="flex items-center gap-2">
                                  {getActivityIcon(task.activityType)}
                                  <h3 className={`font-semibold text-sm ${
                                    isCompleted ? 'text-slate-500 line-through' : 'text-slate-200'
                                  }`}>
                                    {task.topicTitle}
                                  </h3>
                                </div>
                              </div>
                              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                                getComplexityColor(task.complexityLevel)
                              }`}>
                                {task.complexityLevel}
                              </span>
                            </div>

                            <div className="flex items-center gap-3 text-xs text-slate-400">
                              <span className="flex items-center gap-1">
                                {getActivityIcon(task.activityType)}
                                <span className="font-medium">{task.activityType}</span>
                              </span>
                              <span className="flex items-center gap-1">
                                <Clock className="w-3 h-3" />
                                {task.estimatedDurationMinutes}m
                              </span>
                              <span className={`font-medium ${getPriorityColor(task.priorityLevel)}`}>
                                {task.priorityLevel} Priority
                              </span>
                              {isCompleted && (
                                <span className="text-emerald-400 font-medium flex items-center gap-1">
                                  <CheckCircle className="w-3 h-3" />
                                  Done
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>

            {/* Weakness Analysis */}
            <div className="card">
              <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
                <AlertTriangle className="w-5 h-5 text-red-400" />
                Weakness Analysis
                {planner.weakTopicsCount > 0 && (
                  <span className="text-sm font-normal text-red-400">
                    ({planner.weakTopicsCount} topics need attention)
                  </span>
                )}
              </h2>

              {planner.weakTopics?.length === 0 ? (
                <div className="text-center py-8 text-slate-500">
                  <CheckCircle className="w-12 h-12 mx-auto mb-3 text-emerald-500/50" />
                  <p className="font-semibold text-emerald-400">No weak topics detected!</p>
                  <p className="text-sm mt-1">Keep up the good work with regular revision.</p>
                </div>
              ) : (
                <div className="space-y-4">
                  {planner.weakTopics?.map((topic) => {
                    const badge = getWeaknessBadge(topic.weaknessScore)
                    return (
                      <div key={topic.topicId} className="p-4 rounded-xl bg-slate-800/50 border border-slate-700">
                        <div className="flex items-start justify-between mb-3">
                          <div className="flex-1 min-w-0">
                            <h3 className="font-semibold text-slate-200">{topic.topicTitle}</h3>
                            <p className="text-xs text-slate-400 mt-1 leading-relaxed">{topic.whyImportant}</p>
                          </div>
                          <span className={`text-xs px-2 py-1 rounded-full border whitespace-nowrap ml-3 ${badge.color}`}>
                            {badge.label}
                          </span>
                        </div>

                        {/* Metrics Grid */}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mt-3">
                          <div className="bg-slate-900/50 rounded-lg p-2.5">
                            <p className="text-[10px] text-slate-500 uppercase tracking-wider">Weakness</p>
                            <p className="text-sm font-bold text-red-400">{(topic.weaknessScore * 100).toFixed(0)}%</p>
                          </div>
                          <div className="bg-slate-900/50 rounded-lg p-2.5">
                            <p className="text-[10px] text-slate-500 uppercase tracking-wider">Mastery</p>
                            <p className={`text-sm font-bold ${topic.masteryLevel >= 70 ? 'text-emerald-400' : topic.masteryLevel >= 50 ? 'text-yellow-400' : 'text-red-400'}`}>
                              {topic.masteryLevel.toFixed(1)}%
                            </p>
                          </div>
                          <div className="bg-slate-900/50 rounded-lg p-2.5">
                            <p className="text-[10px] text-slate-500 uppercase tracking-wider">Importance</p>
                            <p className="text-sm font-bold text-blue-400">{(topic.importanceScore * 100).toFixed(0)}%</p>
                          </div>
                          <div className="bg-slate-900/50 rounded-lg p-2.5">
                            <p className="text-[10px] text-slate-500 uppercase tracking-wider">Complexity</p>
                            <p className="text-sm font-bold text-purple-400">{(topic.complexityScore * 100).toFixed(0)}%</p>
                          </div>
                        </div>

                        {/* Mastery Bar */}
                        <div className="mt-3">
                          <div className="flex justify-between text-xs text-slate-500 mb-1">
                            <span>Progress</span>
                            <span>{topic.correctAttempts}/{topic.totalAttempts} correct</span>
                          </div>
                          <div className="w-full bg-slate-700 rounded-full h-2">
                            <div
                              className={`h-2 rounded-full transition-all ${getMasteryColor(topic.masteryLevel)}`}
                              style={{ width: `${topic.masteryLevel}%` }}
                            ></div>
                          </div>
                        </div>

                        <div className="mt-3 flex items-center justify-between text-xs">
                          <span className="text-slate-400">
                            <Clock className="w-3 h-3 inline mr-1" />
                            {topic.recommendedDuration}
                          </span>
                          <span className="text-slate-400">
                            {topic.daysUntilExam > 0 ? `${topic.daysUntilExam} days until exam` : 'Exam overdue'}
                          </span>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>

            {/* Priority Topics */}
            <div className="card">
              <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
                <Target className="w-5 h-5 text-cyan-400" />
                Priority Topics
                <span className="text-sm font-normal text-slate-400">(ranked by priority score)</span>
              </h2>

              <div className="space-y-2">
                {planner.priorityTopics?.slice(0, 8).map((topic, index) => (
                  <div key={topic.topicId} className="flex items-center gap-4 p-3 rounded-lg bg-slate-800/30 hover:bg-slate-800/50 transition-all">
                    <span className={`w-7 h-7 rounded-lg flex items-center justify-center text-xs font-bold ${
                      index < 3 ? 'bg-cyan-500/20 text-cyan-400' : 'bg-slate-700 text-slate-400'
                    }`}>
                      {index + 1}
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-slate-200 truncate">{topic.topicTitle}</p>
                      <div className="flex items-center gap-3 mt-1">
                        <span className={`text-[10px] px-1.5 py-0.5 rounded ${
                          getComplexityColor(topic.complexityScore >= 0.7 ? 'HARD' : topic.complexityScore >= 0.4 ? 'MEDIUM' : 'EASY')
                        }`}>
                          {topic.complexityScore >= 0.7 ? 'HARD' : topic.complexityScore >= 0.4 ? 'MEDIUM' : 'EASY'}
                        </span>
                        <div className="flex-1 max-w-[120px] bg-slate-700 rounded-full h-1.5">
                          <div
                            className={`h-1.5 rounded-full ${getMasteryColor(topic.masteryLevel)}`}
                            style={{ width: `${topic.masteryLevel}%` }}
                          ></div>
                        </div>
                        <span className="text-[10px] text-slate-500">{topic.masteryLevel.toFixed(0)}%</span>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-xs font-bold text-cyan-400">{topic.priorityScore.toFixed(2)}</p>
                      <p className="text-[10px] text-slate-500">priority</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Right Column - Roadmap, Revision, Recommendations */}
          <div className="space-y-8">
            {/* Smart Recommendations */}
            <div className="card">
              <h2 className="text-lg font-bold mb-4 flex items-center gap-2">
                <Lightbulb className="w-5 h-5 text-yellow-400" />
                Recommendations
              </h2>
              <div className="space-y-3">
                {planner.recommendations?.map((rec, i) => (
                  <div key={i} className="p-3 rounded-lg bg-slate-800/50 border border-slate-700/50 text-sm text-slate-300 leading-relaxed">
                    <span className="text-cyan-400 font-bold mr-2">•</span>
                    {rec}
                  </div>
                ))}
              </div>
            </div>

            {/* Study Roadmap */}
            <div className="card">
              <h2 className="text-lg font-bold mb-4 flex items-center gap-2">
                <CalendarDays className="w-5 h-5 text-blue-400" />
                Study Roadmap
              </h2>
              <div className="space-y-3 max-h-[500px] overflow-y-auto pr-1">
                {planner.studyRoadmap?.slice(0, 21).map((item, i) => (
                  <div key={i} className="flex items-start gap-3 p-3 rounded-lg bg-slate-800/30 border border-slate-700/50">
                    <div className="flex-shrink-0 w-8 h-8 rounded-lg bg-slate-700/50 flex items-center justify-center">
                      <span className="text-xs font-bold text-cyan-400">D{item.day}</span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        {getActivityIcon(item.activityType)}
                        <p className="text-sm font-semibold text-slate-200 truncate">{item.topicTitle}</p>
                      </div>
                      <div className="flex items-center gap-2 mt-1 text-[10px] text-slate-500">
                        <span>{item.activityType}</span>
                        <span>•</span>
                        <span>{item.estimatedDurationMinutes}m</span>
                        <span className={`px-1.5 py-0.5 rounded ${
                          item.complexityLevel === 'HARD' ? 'text-red-400 bg-red-500/10' :
                          item.complexityLevel === 'MEDIUM' ? 'text-yellow-400 bg-yellow-500/10' :
                          'text-emerald-400 bg-emerald-500/10'
                        }`}>{item.complexityLevel}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Revision Schedule */}
            <div className="card">
              <h2 className="text-lg font-bold mb-4 flex items-center gap-2">
                <GraduationCap className="w-5 h-5 text-purple-400" />
                Revision Schedule
              </h2>
              <div className="space-y-2">
                {planner.revisionSchedule?.slice(0, 6).map((item, i) => {
                  const isUrgent = item.weaknessScore >= 0.7
                  return (
                    <div key={i} className={`p-3 rounded-lg border ${
                      isUrgent
                        ? 'bg-red-500/5 border-red-500/20'
                        : 'bg-slate-800/30 border-slate-700/50'
                    }`}>
                      <div className="flex items-center justify-between">
                        <p className="text-sm font-semibold text-slate-200 truncate flex-1">{item.topicTitle}</p>
                        {isUrgent && <AlertTriangle className="w-3 h-3 text-red-400 ml-2 flex-shrink-0" />}
                      </div>
                      <div className="flex items-center gap-3 mt-1.5 text-[10px] text-slate-500">
                        <span className="flex items-center gap-1">
                          <Calendar className="w-3 h-3" />
                          {new Date(item.revisionDate).toLocaleDateString()}
                        </span>
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {item.frequency}
                        </span>
                      </div>
                      <div className="mt-1.5 w-full bg-slate-700 rounded-full h-1">
                        <div
                          className={`h-1 rounded-full ${item.weaknessScore >= 0.7 ? 'bg-red-500' : item.weaknessScore >= 0.4 ? 'bg-yellow-500' : 'bg-emerald-500'}`}
                          style={{ width: `${(1 - item.weaknessScore) * 100}%` }}
                        ></div>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>

            {/* Upcoming Tests/Practice Days */}
            {planner.practiceDays?.length > 0 && (
              <div className="card">
                <h2 className="text-lg font-bold mb-4 flex items-center gap-2">
                  <Target className="w-5 h-5 text-orange-400" />
                  Practice & Test Days
                </h2>
                <div className="flex flex-wrap gap-2">
                  {planner.practiceDays.map((day, i) => (
                    <span key={i} className="px-3 py-1.5 rounded-lg bg-orange-500/10 border border-orange-500/20 text-orange-400 text-sm font-medium">
                      Day {day}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Quick Actions */}
            <div className="card">
              <h2 className="text-lg font-bold mb-4">Quick Actions</h2>
              <div className="space-y-3">
                <button
                  onClick={() => navigate('/study')}
                  className="w-full bg-gradient-to-r from-cyan-500 to-blue-500 text-white font-semibold py-3 px-4 rounded-xl hover:from-cyan-600 hover:to-blue-600 transition-all flex items-center justify-center gap-2"
                >
                  <BookOpen className="w-4 h-4" />
                  Start Studying
                  <ArrowRight className="w-4 h-4" />
                </button>
                <button
                  onClick={() => navigate('/upload')}
                  className="w-full bg-slate-700 text-slate-200 font-semibold py-3 px-4 rounded-xl hover:bg-slate-600 transition-all flex items-center justify-center gap-2"
                >
                  <ChevronRight className="w-4 h-4" />
                  Upload New PDF
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}