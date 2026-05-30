import React, { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Clock, Play, Pause, RotateCcw, Target } from 'lucide-react'

export const StudyTimer = ({ onSessionEnd }) => {
  const [seconds, setSeconds] = useState(0)
  const [isRunning, setIsRunning] = useState(false)
  const [sessionGoal, setSessionGoal] = useState(25)
  const [showGoalInput, setShowGoalInput] = useState(false)

  useEffect(() => {
    let interval
    if (isRunning) {
      interval = setInterval(() => { setSeconds(s => s + 1) }, 1000)
    }
    return () => clearInterval(interval)
  }, [isRunning])

  const formatTime = (s) => {
    const h = Math.floor(s / 3600)
    const m = Math.floor((s % 3600) / 60)
    const sec = s % 60
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
    return `${m}:${String(sec).padStart(2, '0')}`
  }

  const handleReset = () => { setSeconds(0); setIsRunning(false) }
  const handleEndSession = () => {
    setIsRunning(false)
    if (onSessionEnd) onSessionEnd(seconds)
    handleReset()
  }

  const goalSec = sessionGoal * 60
  const progress = Math.min((seconds / goalSec) * 100, 100)
  const reached = seconds >= goalSec

  return (
    <div className="glass-pane rounded-xl p-5 border border-black/8">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Clock className="w-4 h-4 text-primary" />
          <h3 className="font-semibold text-sm text-on-surface">Study Timer</h3>
        </div>
        {isRunning && (
          <span className="text-xs text-emerald-600 bg-emerald-100/60 px-2 py-0.5 rounded-full font-medium">
            Active
          </span>
        )}
      </div>

      {/* Timer Display */}
      <div className="text-center mb-4">
        <motion.div
          className="text-4xl font-bold text-primary font-mono tracking-tight"
          key={seconds}
          initial={{ scale: 1.05 }}
          animate={{ scale: 1 }}
          transition={{ duration: 0.12 }}
        >
          {formatTime(seconds)}
        </motion.div>
        <p className="text-xs text-on-surface-variant/60 mt-1">
          Goal: {sessionGoal} min {reached && '✓'}
        </p>
      </div>

      {/* Progress Bar */}
      <div className="w-full h-1.5 rounded-full bg-white/40 mb-4 overflow-hidden">
        <motion.div
          className={`h-full rounded-full ${reached ? 'bg-emerald-500' : 'bg-gradient-to-r from-primary to-primary-container'}`}
          initial={{ width: 0 }}
          animate={{ width: `${progress}%` }}
          transition={{ duration: 0.3 }}
        />
      </div>

      {/* Controls */}
      <div className="flex gap-1.5">
        <button
          onClick={() => setIsRunning(!isRunning)}
          className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold transition-all ${
            isRunning
              ? 'bg-orange-100/80 text-orange-700 hover:bg-orange-200/80'
              : 'bg-primary/10 text-primary hover:bg-primary/20'
          }`}
        >
          {isRunning ? <><Pause className="w-3.5 h-3.5" /> Pause</> : <><Play className="w-3.5 h-3.5" /> Start</>}
        </button>
        <button
          onClick={handleReset}
          className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold bg-white/40 text-on-surface-variant hover:bg-white/60 border border-black/8 transition-all"
        >
          <RotateCcw className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={handleEndSession}
          className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold bg-red-50/80 text-red-700 hover:bg-red-100/80 transition-all"
        >
          End
        </button>
      </div>

      {/* Goal Settings */}
      <div className="mt-3 pt-3 border-t border-white/20">
        <button
          onClick={() => setShowGoalInput(!showGoalInput)}
          className="flex items-center gap-1.5 text-xs text-primary hover:text-primary/80 transition-all font-medium"
        >
          <Target className="w-3.5 h-3.5" />
          {showGoalInput ? 'Hide goal' : 'Set goal'}
        </button>
        <AnimatePresence>
          {showGoalInput && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="overflow-hidden"
            >
              <div className="mt-3 flex items-center gap-2">
                <input
                  type="number"
                  min="1"
                  max="180"
                  value={sessionGoal}
                  onChange={(e) => setSessionGoal(Math.max(1, parseInt(e.target.value) || 25))}
                  className="input-glass text-sm py-1.5 px-3 flex-1"
                />
                <span className="text-xs text-on-surface-variant/60">min</span>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
