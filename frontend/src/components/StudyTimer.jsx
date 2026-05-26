import React, { useState, useEffect } from 'react'
import { Clock, Play, Pause, RotateCcw, AlertCircle } from 'lucide-react'

export const StudyTimer = ({ onSessionEnd }) => {
  const [seconds, setSeconds] = useState(0)
  const [isRunning, setIsRunning] = useState(false)
  const [sessionGoal, setSessionGoal] = useState(25)
  const [showGoalInput, setShowGoalInput] = useState(false)

  useEffect(() => {
    let interval
    if (isRunning) {
      interval = setInterval(() => {
        setSeconds(prev => prev + 1)
      }, 1000)
    }
    return () => clearInterval(interval)
  }, [isRunning])

  const formatTime = (totalSeconds) => {
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const secs = totalSeconds % 60

    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
    }
    return `${minutes}:${secs.toString().padStart(2, '0')}`
  }

  const handleReset = () => {
    setSeconds(0)
    setIsRunning(false)
  }

  const handleEndSession = () => {
    setIsRunning(false)
    if (onSessionEnd) {
      onSessionEnd(seconds)
    }
    handleReset()
  }

  const goalSeconds = sessionGoal * 60
  const progress = Math.min((seconds / goalSeconds) * 100, 100)
  const isGoalReached = seconds >= goalSeconds

  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-6">
        <Clock className="w-6 h-6 text-cyan-400" />
        <h2 className="text-2xl font-bold text-slate-200">Study Timer</h2>
      </div>

      <div className="bg-slate-800/50 rounded-lg p-8 mb-6 text-center">
        <div className="text-6xl font-bold text-cyan-400 font-mono mb-4">
          {formatTime(seconds)}
        </div>

        <div className="mb-6">
          <div className="w-full bg-slate-700 rounded-full h-3">
            <div
              className={`h-3 rounded-full transition-all ${
                isGoalReached
                  ? 'bg-gradient-to-r from-emerald-500 to-green-500'
                  : 'bg-gradient-to-r from-cyan-500 to-blue-500'
              }`}
              style={{ width: `${progress}%` }}
            ></div>
          </div>
          <p className="text-sm text-slate-400 mt-2">
            Goal: {sessionGoal} minutes {isGoalReached && '✓ Reached!'}
          </p>
        </div>

        <div className="flex gap-3 justify-center">
          <button
            onClick={() => setIsRunning(!isRunning)}
            className={`flex items-center gap-2 px-6 py-2 rounded-lg font-semibold transition ${
              isRunning
                ? 'bg-orange-500/20 text-orange-400 hover:bg-orange-500/30'
                : 'bg-cyan-500/20 text-cyan-400 hover:bg-cyan-500/30'
            }`}
          >
            {isRunning ? (
              <>
                <Pause className="w-4 h-4" />
                Pause
              </>
            ) : (
              <>
                <Play className="w-4 h-4" />
                Start
              </>
            )}
          </button>

          <button
            onClick={handleReset}
            className="flex items-center gap-2 px-6 py-2 bg-slate-700/50 text-slate-300 rounded-lg hover:bg-slate-700 transition font-semibold"
          >
            <RotateCcw className="w-4 h-4" />
            Reset
          </button>

          <button
            onClick={handleEndSession}
            className="flex items-center gap-2 px-6 py-2 bg-red-500/20 text-red-400 rounded-lg hover:bg-red-500/30 transition font-semibold"
          >
            End Session
          </button>
        </div>
      </div>

      <div className="border-t border-slate-700 pt-4">
        <button
          onClick={() => setShowGoalInput(!showGoalInput)}
          className="text-sm text-cyan-400 hover:text-cyan-300 transition"
        >
          {showGoalInput ? 'Hide' : 'Set'} Study Goal
        </button>

        {showGoalInput && (
          <div className="mt-4 p-4 bg-slate-800/50 rounded-lg">
            <label className="block text-sm text-slate-300 mb-2">
              Goal Duration (minutes)
            </label>
            <input
              type="number"
              min="1"
              max="180"
              value={sessionGoal}
              onChange={(e) => setSessionGoal(Math.max(1, parseInt(e.target.value) || 25))}
              className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-lg text-slate-200 focus:border-cyan-500 focus:outline-none"
            />
          </div>
        )}
      </div>
    </div>
  )
}
