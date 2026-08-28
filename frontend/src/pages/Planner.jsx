import React, { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { plannerAPI } from '../api'
import {
  ListChecks, Target, Calendar, Clock, AlertTriangle, CheckCircle2,
  BookOpen, Zap, ArrowRight, GraduationCap, Lightbulb, ArrowLeft, AlertCircle, Circle,
} from 'lucide-react'
import { motion } from 'framer-motion'

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.06 } } }
const item = { hidden: { opacity: 0, y: 14 }, show: { opacity: 1, y: 0, transition: { duration: 0.3 } } }

/**
 * Ticks are stored in the database, keyed per calendar day by
 * `topicId:ACTIVITY:sessionIndex` — never by list position, because the plan is
 * re-ranked after every quiz answer and a positional key marked the wrong topic
 * as done. The server sends `taskKey` and `completed` with each task, so a tick
 * survives a reload and follows the account rather than the browser.
 *
 * One stale `localStorage` key set is cleared on mount: an earlier version kept
 * completion in the browser and would otherwise leave a key behind every day.
 */
const clearLegacyLocalState = () => {
  try {
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i)
      if (key && (key.startsWith('planner-done-') || key.startsWith('planner-completed-'))) {
        localStorage.removeItem(key)
      }
    }
  } catch {
    // Storage unavailable (private mode): nothing to clear.
  }
}

/** Falls back to the title only if the server somehow omits a key. */
const keyOf = (task) =>
  task.taskKey ?? `${task.topicId ?? task.topicTitle}:${task.activityType}:${task.sessionIndex ?? 0}`

const ACTIVITY_ICONS = {
  LEARN: BookOpen,
  REVISION: GraduationCap,
  PRACTICE: Zap,
  TEST: Target,
}

const ActivityIcon = ({ type, className = 'w-4 h-4' }) => {
  const Icon = ACTIVITY_ICONS[type] ?? BookOpen
  return <Icon className={className} />
}

const complexityClass = (level) => ({
  HARD: 'text-red-600 bg-red-500/10',
  MEDIUM: 'text-yellow-700 bg-yellow-500/10',
  EASY: 'text-emerald-600 bg-emerald-500/10',
}[level] ?? 'text-slate-500 bg-slate-500/10')

const masteryBar = (mastery) => {
  if (mastery >= 80) return 'bg-emerald-500'
  if (mastery >= 60) return 'bg-blue-500'
  if (mastery >= 40) return 'bg-yellow-500'
  return 'bg-red-500'
}

export const Planner = () => {
  const [planner, setPlanner] = useState(null)
  const [loading, setLoading] = useState(true)
  // Kept separate on purpose: a failed tick must not make the page think
  // the plan failed to load and replace it with the empty state.
  const [loadError, setLoadError] = useState('')
  const [saveError, setSaveError] = useState('')
  // Only one section is on screen at a time — the page held everything at once,
  // which is what made it feel congested.
  const [tab, setTab] = useState('today')
  // Server-confirmed completion, seeded from the plan response.
  const [doneKeys, setDoneKeys] = useState(() => new Set())
  const [savingKeys, setSavingKeys] = useState(() => new Set())
  const navigate = useNavigate()

  useEffect(() => {
    clearLegacyLocalState()
    let cancelled = false
    plannerAPI.get()
      .then((res) => {
        if (cancelled) return
        setPlanner(res.data)
        setDoneKeys(new Set(
          (res.data?.todayTasks ?? []).filter((t) => t.completed).map(keyOf),
        ))
        setLoadError('')
      })
      .catch(() => { if (!cancelled) setLoadError('Failed to load your plan. Please try again.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  const tasks = useMemo(() => planner?.todayTasks ?? [], [planner])

  // Count only completions that match a task still in today's plan, so a
  // shrinking plan can never show "5/3 done" or a >100% progress bar.
  const doneCount = useMemo(
    () => tasks.filter((t) => doneKeys.has(keyOf(t))).length,
    [tasks, doneKeys],
  )
  const percent = tasks.length ? Math.round((doneCount / tasks.length) * 100) : 0

  /**
   * Optimistic: the checkbox flips immediately, then reverts if the save fails,
   * so a dropped request can never leave the UI claiming work is saved when it
   * is not.
   */
  const toggleTask = async (task) => {
    const key = keyOf(task)
    if (savingKeys.has(key)) return

    const nowCompleted = !doneKeys.has(key)
    setDoneKeys((prev) => {
      const next = new Set(prev)
      if (nowCompleted) next.add(key)
      else next.delete(key)
      return next
    })
    setSavingKeys((prev) => new Set(prev).add(key))

    try {
      await plannerAPI.toggleTask({
        topicId: task.topicId,
        activityType: task.activityType,
        sessionIndex: task.sessionIndex ?? 0,
        completed: nowCompleted,
      })
    } catch {
      setDoneKeys((prev) => {
        const next = new Set(prev)
        if (nowCompleted) next.delete(key)
        else next.add(key)
        return next
      })
      setSaveError('Could not save that tick — it has been undone. Try again.')
    } finally {
      setSavingKeys((prev) => {
        const next = new Set(prev)
        next.delete(key)
        return next
      })
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-2 border-primary border-t-transparent mx-auto mb-4" />
          <p className="text-on-surface font-semibold">Building your study plan...</p>
        </div>
      </div>
    )
  }

  if (loadError || !planner || planner.totalTopics === 0) {
    return (
      <div className="flex items-center justify-center min-h-[60vh] px-4">
        <div className="glass-pane rounded-xl p-8 max-w-md text-center">
          <Target className="w-14 h-14 text-primary mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-on-surface mb-2">
            {loadError ? 'Could not load your plan' : 'No study plan yet'}
          </h2>
          <p className="text-on-surface-variant/70 mb-6">
            {loadError || 'Upload a PDF and let it finish analyzing to get your personalised plan.'}
          </p>
          <button onClick={() => (loadError ? window.location.reload() : navigate('/upload'))} className="btn-glass-primary">
            {loadError ? 'Retry' : 'Upload PDF'}
          </button>
        </div>
      </div>
    )
  }

  const focusTopics = (planner.priorityTopics ?? []).slice(0, 6)

  const TABS = [
    { id: 'today', label: 'Today', count: tasks.length },
    { id: 'roadmap', label: 'Roadmap', count: null },
    { id: 'topics', label: 'Topics', count: planner.totalTopics },
  ]

  return (
    <motion.div variants={container} initial={false} animate="show" className="space-y-6">
      <button
        onClick={() => navigate('/dashboard')}
        className="flex items-center gap-2 text-on-surface-variant/70 hover:text-primary transition-colors text-sm"
      >
        <ArrowLeft className="w-4 h-4" /> Back to Dashboard
      </button>

      {saveError && (
        <motion.div variants={item} className="glass-pane rounded-xl p-4 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="flex-1">{saveError}</p>
          <button onClick={() => setSaveError('')} className="text-sm font-semibold hover:underline">
            Dismiss
          </button>
        </motion.div>
      )}

      {/* Tracker — today's progress is the headline, not one tile among six */}
      <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
        <div className="flex flex-col sm:flex-row sm:items-center gap-5">
          <div className="flex items-center gap-4 flex-1 min-w-0">
            <div className="relative w-16 h-16 flex-shrink-0">
              <svg className="w-16 h-16 -rotate-90" viewBox="0 0 36 36" aria-hidden="true">
                <circle cx="18" cy="18" r="15.9" fill="none" stroke="currentColor"
                  className="text-black/10" strokeWidth="3" />
                <circle cx="18" cy="18" r="15.9" fill="none" stroke="currentColor"
                  className="text-primary transition-all duration-500" strokeWidth="3"
                  strokeLinecap="round" strokeDasharray={`${percent} 100`} />
              </svg>
              <span className="absolute inset-0 flex items-center justify-center text-sm font-bold text-on-surface">
                {percent}%
              </span>
            </div>
            <div className="min-w-0">
              <h1 className="text-xl font-bold text-on-surface">Today&apos;s progress</h1>
              <p className="text-sm text-on-surface-variant/70 mt-0.5">
                {tasks.length === 0
                  ? 'Nothing scheduled for today.'
                  : doneCount === tasks.length
                    ? 'All tasks done — well played.'
                    : `${doneCount} of ${tasks.length} tasks done · about ${planner.totalDurationMinutesToday}m of study`}
              </p>
              {planner.daysUntilExam > 0 && (
                <p className="text-xs text-on-surface-variant/60 mt-1 flex items-center gap-1">
                  <Calendar className="w-3 h-3" />
                  {planner.daysUntilExam} days until your exam
                </p>
              )}
            </div>
          </div>
          <div className="flex gap-2 flex-shrink-0">
            <button onClick={() => navigate('/practice')} className="btn-glass-primary flex items-center gap-2">
              <Zap className="w-4 h-4" /> Practice
            </button>
            {/* Quick Answers is built from stored topics — no AI API call. */}
            <button onClick={() => navigate('/quick-answers')} className="btn-glass-secondary flex items-center gap-2">
              <Lightbulb className="w-4 h-4" /> Quick Answers
            </button>
          </div>
        </div>
      </motion.div>

      {/* One section at a time */}
      <motion.div variants={item} role="tablist" aria-label="Planner sections"
        className="flex gap-1 p-1 glass-pane rounded-xl border border-black/8 w-full sm:w-auto sm:inline-flex">
        {TABS.map(({ id, label, count }) => (
          <button
            key={id}
            role="tab"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
            className={`flex-1 sm:flex-none px-5 py-2 rounded-lg text-sm font-semibold transition-all ${
              tab === id
                ? 'bg-primary text-white shadow-sm'
                : 'text-on-surface-variant/70 hover:text-on-surface hover:bg-white/40'
            }`}
          >
            {label}
            {count != null && (
              <span className={`ml-2 text-xs ${tab === id ? 'text-white/70' : 'text-on-surface-variant/50'}`}>
                {count}
              </span>
            )}
          </button>
        ))}
      </motion.div>

      {/* Today's tasks — the checklist */}
      {tab === 'today' && (
      <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
        <h2 className="text-lg font-bold text-on-surface flex items-center gap-2 mb-4">
          <ListChecks className="w-5 h-5 text-primary" /> Today&apos;s tasks
        </h2>

        {tasks.length === 0 ? (
          <div className="text-center py-8 text-on-surface-variant/70">
            <CheckCircle2 className="w-10 h-10 mx-auto mb-2 text-emerald-400" />
            <p>Nothing scheduled — take a quiz to generate today&apos;s plan.</p>
          </div>
        ) : (
          <ul className="space-y-2">
            {tasks.map((task) => {
              const key = keyOf(task)
              const done = doneKeys.has(key)
              const saving = savingKeys.has(key)
              return (
                <li key={key}>
                  <button
                    type="button"
                    onClick={() => toggleTask(task)}
                    disabled={saving}
                    aria-pressed={done}
                    className={`w-full text-left p-4 rounded-xl border transition-all ${
                      saving ? 'opacity-60 cursor-wait ' : ''
                    }${
                      done
                        ? 'bg-emerald-500/5 border-emerald-500/20'
                        : 'glass-pane-sm border-black/8 hover:border-primary/30'
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      {done
                        ? <CheckCircle2 className="w-5 h-5 text-emerald-500 flex-shrink-0 mt-0.5" />
                        : <Circle className="w-5 h-5 text-on-surface-variant/30 flex-shrink-0 mt-0.5" />}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-start justify-between gap-3">
                          <span className={`font-semibold text-sm ${done ? 'text-on-surface-variant/40 line-through' : 'text-on-surface'}`}>
                            {task.topicTitle}
                          </span>
                          <span className={`text-xs px-2 py-0.5 rounded-full font-medium flex-shrink-0 ${complexityClass(task.complexityLevel)}`}>
                            {task.complexityLevel}
                          </span>
                        </div>
                        <div className="flex items-center gap-3 text-xs text-on-surface-variant/70 mt-1.5">
                          <span className="flex items-center gap-1">
                            <ActivityIcon type={task.activityType} className="w-3.5 h-3.5" />
                            {task.activityType}
                          </span>
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3" />{task.estimatedDurationMinutes}m
                          </span>
                        </div>
                      </div>
                    </div>
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </motion.div>
      )}

      {/* Roadmap — promoted out of the cramped sidebar, grouped by day with real dates */}
      {tab === 'roadmap' && (
      <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-on-surface flex items-center gap-2">
            <Calendar className="w-5 h-5 text-blue-500" /> Roadmap to your exam
          </h2>
          {planner.daysUntilExam > 0 && (
            <span className="text-sm text-on-surface-variant/70">{planner.daysUntilExam} days left</span>
          )}
        </div>

        {!planner.studyRoadmap?.length ? (
          <p className="text-on-surface-variant/70 text-sm py-6 text-center">
            The roadmap appears once topics have been analysed.
          </p>
        ) : (
          <ol className="space-y-3">
            {Object.entries(
              planner.studyRoadmap.reduce((days, entry) => {
                (days[entry.day] ||= []).push(entry)
                return days
              }, {}),
            ).slice(0, 14).map(([day, entries]) => (
              <li key={day} className="flex gap-4">
                <div className="flex flex-col items-center flex-shrink-0 w-14">
                  <span className="w-11 h-11 rounded-xl bg-primary/10 text-primary flex items-center justify-center text-sm font-bold">
                    D{day}
                  </span>
                  {entries[0]?.scheduledDate && (
                    <span className="text-[10px] text-on-surface-variant/60 mt-1 text-center">
                      {new Date(entries[0].scheduledDate).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                    </span>
                  )}
                </div>
                <div className="flex-1 min-w-0 space-y-2 pb-1">
                  {entries.map((entry, i) => (
                    <div key={`${day}-${i}`} className="glass-pane-sm rounded-lg px-3 py-2 border border-black/8">
                      <div className="flex items-center justify-between gap-3">
                        <span className="text-sm font-medium text-on-surface truncate">{entry.topicTitle}</span>
                        <span className="text-[10px] text-on-surface-variant/70 flex-shrink-0">
                          {entry.estimatedDurationMinutes}m
                        </span>
                      </div>
                      <div className="flex items-center gap-1.5 mt-1 text-[10px] text-on-surface-variant/70">
                        <ActivityIcon type={entry.activityType} className="w-3 h-3" />
                        {entry.activityType}
                      </div>
                    </div>
                  ))}
                </div>
              </li>
            ))}
          </ol>
        )}
      </motion.div>
      )}

      {tab === 'topics' && (
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Focus topics — weakness and priority merged into one ranked list */}
        <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
          <h2 className="text-lg font-bold text-on-surface flex items-center gap-2 mb-4">
            <Target className="w-5 h-5 text-primary" /> What to focus on
          </h2>
          {focusTopics.length === 0 ? (
            <p className="text-on-surface-variant/70 text-sm py-6 text-center">No topics ranked yet.</p>
          ) : (
            <ol className="space-y-2">
              {focusTopics.map((topic, i) => (
                <li key={topic.topicId} className="flex items-center gap-3 p-3 rounded-xl glass-pane-sm border border-black/8">
                  <span className={`w-7 h-7 rounded-lg flex items-center justify-center text-xs font-bold flex-shrink-0 ${
                    i < 3 ? 'bg-primary/10 text-primary' : 'bg-white/40 text-on-surface-variant/70'
                  }`}>{i + 1}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-on-surface truncate">{topic.topicTitle}</span>
                      {topic.weaknessScore >= 0.65 && (
                        <AlertTriangle className="w-3.5 h-3.5 text-red-500 flex-shrink-0" aria-label="Needs attention" />
                      )}
                    </div>
                    <div className="flex items-center gap-2 mt-1.5">
                      <div className="flex-1 max-w-[140px] bg-white/40 rounded-full h-1.5">
                        <div className={`h-1.5 rounded-full ${masteryBar(topic.masteryLevel)}`} style={{ width: `${topic.masteryLevel}%` }} />
                      </div>
                      <span className="text-[10px] text-on-surface-variant/70">
                        {topic.masteryLevel.toFixed(0)}% mastery
                      </span>
                    </div>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </motion.div>

        <div className="space-y-6">
          {/* Revision schedule */}
          <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
            <h2 className="text-lg font-bold text-on-surface flex items-center gap-2 mb-4">
              <GraduationCap className="w-5 h-5 text-purple-500" /> Next revisions
            </h2>
            {!planner.revisionSchedule?.length ? (
              <p className="text-on-surface-variant/70 text-sm py-4 text-center">Nothing due yet.</p>
            ) : (
              <ul className="space-y-2">
                {planner.revisionSchedule.slice(0, 5).map((entry, i) => (
                  <li key={i} className={`p-3 rounded-xl border ${
                    entry.weaknessScore >= 0.7 ? 'bg-red-500/5 border-red-500/20' : 'glass-pane-sm border-black/8'
                  }`}>
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-sm font-medium text-on-surface truncate">{entry.topicTitle}</span>
                      <span className="text-[10px] text-on-surface-variant/70 flex-shrink-0">{entry.frequency}</span>
                    </div>
                    <span className="text-[10px] text-on-surface-variant/60 flex items-center gap-1 mt-1">
                      <Calendar className="w-3 h-3" />
                      {new Date(entry.revisionDate).toLocaleDateString()}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </motion.div>

          {/* Recommendations — a plain list reads better than the old table */}
          {planner.recommendations?.length > 0 && (
            <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
              <h2 className="text-lg font-bold text-on-surface flex items-center gap-2 mb-4">
                <Lightbulb className="w-5 h-5 text-yellow-500" /> Suggestions
              </h2>
              <ul className="space-y-2.5">
                {planner.recommendations.map((rec, i) => (
                  <li key={i} className="flex gap-2.5 text-sm text-on-surface/80 leading-relaxed">
                    <ArrowRight className="w-4 h-4 text-primary flex-shrink-0 mt-0.5" />
                    <span>{rec}</span>
                  </li>
                ))}
              </ul>
            </motion.div>
          )}
        </div>
      </div>
      )}
    </motion.div>
  )
}
