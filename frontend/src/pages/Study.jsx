import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { topicAPI, quizAPI, dashboardAPI, recommendationAPI } from '../api'
import { StudyTimer } from '../components/StudyTimer'
import { BookOpen, AlertCircle, Loader, CheckCircle, XCircle, ArrowRight, BarChart3, Zap, TrendingUp, ListChecks } from 'lucide-react'

export const Study = () => {
  const [topics, setTopics] = useState([])
  const [allQuizzes, setAllQuizzes] = useState([])
  const [currentGlobalQuizIndex, setCurrentGlobalQuizIndex] = useState(0)
  const [selectedAnswer, setSelectedAnswer] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [startTime, setStartTime] = useState(null)
  const [quizStats, setQuizStats] = useState({ correct: 0, total: 0 })
  const [sessionComplete, setSessionComplete] = useState(false)
  const [topicQuizMap, setTopicQuizMap] = useState({})
  const navigate = useNavigate()

  useEffect(() => {
    fetchTopicsAndQuizzes()
  }, [])

  const fetchTopicsAndQuizzes = async () => {
    try {
      const topicsResponse = await topicAPI.getRanked()
      setTopics(topicsResponse.data)

      const quizMap = {}
      let allQuizzesArray = []

      for (const topic of topicsResponse.data) {
        const quizzesResponse = await quizAPI.getByTopic(topic.id)
        quizMap[topic.id] = quizzesResponse.data
        allQuizzesArray = [...allQuizzesArray, ...quizzesResponse.data.map(q => ({ ...q, topicId: topic.id }))]
      }

      setTopicQuizMap(quizMap)
      setAllQuizzes(allQuizzesArray)
      setStartTime(Date.now())
      setError('')
    } catch (err) {
      console.error('Error loading topics and quizzes:', err)
      setError('Failed to load study materials')
    } finally {
      setLoading(false)
    }
  }

  const handleSubmitQuiz = async () => {
    if (!selectedAnswer) {
      setError('Please select an answer')
      return
    }

    const timeTaken = Math.floor((Date.now() - startTime) / 1000)

    try {
      const response = await quizAPI.submit(currentQuiz.id, {
        selectedAnswer,
        timeTakenSeconds: timeTaken,
      })
      setResult(response.data)
      setSubmitted(true)

      setQuizStats(prev => ({
        correct: prev.correct + (response.data.isCorrect ? 1 : 0),
        total: prev.total + 1
      }))
    } catch (err) {
      console.error('Error submitting quiz:', err)
      setError('Failed to submit quiz')
    }
  }

  const handleNextQuiz = () => {
    if (currentGlobalQuizIndex < allQuizzes.length - 1) {
      setCurrentGlobalQuizIndex(currentGlobalQuizIndex + 1)
      setSelectedAnswer('')
      setSubmitted(false)
      setResult(null)
      setStartTime(Date.now())
    } else {
      handleSessionComplete()
    }
  }

  const calculateWeakness = (accuracy, attempts) => {
    let multiplier = 1.0
    if (attempts <= 3) {
      multiplier = 1.8
    } else if (attempts <= 8) {
      multiplier = 1.2
    }
    return (1 - accuracy) * multiplier
  }

  const handleSessionComplete = async () => {
    const accuracy = quizStats.total > 0 ? quizStats.correct / quizStats.total : 0
    const weakness = calculateWeakness(accuracy, quizStats.total)

    try {
      console.log('Starting session completion with weakness:', weakness)

      for (const topic of topics) {
        try {
          console.log('Updating topic:', topic.id, 'with weakness:', weakness)
          await topicAPI.updateWeakness(topic.id, {
            weakness,
            accuracy,
            attempts: quizStats.total
          })
          console.log('Successfully updated topic:', topic.id)
        } catch (topicErr) {
          console.error('Error updating topic', topic.id, ':', topicErr)
          throw topicErr
        }
      }

      console.log('All topics updated successfully')
      setSessionComplete(true)

      // Force-refresh adaptive views (dashboard/recommendations) so updated priorities/weakness are visible immediately.
      // These calls are intentionally fire-and-forget; we only need the backend to recompute/serve fresh data.
      try {
        await dashboardAPI.get()
        await recommendationAPI.getNextTopics(10)
      } catch (refreshErr) {
        console.warn('Adaptive refresh failed (non-blocking):', refreshErr)
      }

      // Redirect to planner after 2 seconds for immediate action
      setTimeout(() => {
        navigate('/planner')
      }, 2000)
    } catch (err) {
      console.error('Error completing session:', err)
      setError('Failed to complete session: ' + (err.response?.data?.message || err.message))
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
        <div className="text-center">
          <div className="animate-spin rounded-full h-16 w-16 border-4 border-cyan-500/20 border-t-cyan-500 mx-auto mb-6"></div>
          <p className="text-slate-300 text-lg font-semibold">Loading your study session...</p>
          <p className="text-slate-500 text-sm mt-2">Preparing quizzes from all topics</p>
        </div>
      </div>
    )
  }

  if (allQuizzes.length === 0) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 flex items-center justify-center px-4">
        <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-2xl p-8 max-w-md text-center">
          <AlertCircle className="w-16 h-16 text-amber-500 mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-slate-100 mb-2">No Quizzes Available</h2>
          <p className="text-slate-400">Upload and analyze a PDF to start your study session</p>
        </div>
      </div>
    )
  }

  const currentQuiz = allQuizzes[currentGlobalQuizIndex]
  const currentTopic = topics.find(t => t.id === currentQuiz.topicId)
  const progress = ((currentGlobalQuizIndex + 1) / allQuizzes.length) * 100
  const isLastQuiz = currentGlobalQuizIndex === allQuizzes.length - 1
  const accuracy = quizStats.total > 0 ? Math.round((quizStats.correct / quizStats.total) * 100) : 0

  if (sessionComplete) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 flex items-center justify-center px-4">
        <div className="text-center max-w-md">
          <div className="mb-6 animate-bounce">
            <CheckCircle className="w-20 h-20 text-emerald-500 mx-auto" />
          </div>
          <h2 className="text-4xl font-bold text-slate-100 mb-2">Session Complete! 🎉</h2>
          <p className="text-slate-400 mb-8">Your performance has been analyzed and topic priorities updated.</p>

          <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-xl p-6 mb-6">
            <div className="grid grid-cols-3 gap-4">
              <div>
                <p className="text-slate-400 text-sm mb-1">Correct</p>
                <p className="text-2xl font-bold text-emerald-400">{quizStats.correct}</p>
              </div>
              <div>
                <p className="text-slate-400 text-sm mb-1">Total</p>
                <p className="text-2xl font-bold text-slate-300">{quizStats.total}</p>
              </div>
              <div>
                <p className="text-slate-400 text-sm mb-1">Accuracy</p>
                <p className={`text-2xl font-bold ${
                  accuracy >= 70 ? 'text-emerald-400' : accuracy >= 50 ? 'text-yellow-400' : 'text-red-400'
                }`}>{accuracy}%</p>
              </div>
            </div>
          </div>

          <p className="text-slate-500 text-sm">Redirecting to your study planner...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
      {/* Header */}
      <div className="bg-slate-900/50 backdrop-blur border-b border-slate-800 sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-6 py-4">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h1 className="text-2xl font-bold text-slate-100">Study Session</h1>
              <p className="text-slate-400 text-sm mt-1">Complete all quizzes to update your learning priorities</p>
            </div>
            <div className="text-right">
              <p className="text-slate-400 text-sm">Question {currentGlobalQuizIndex + 1} of {allQuizzes.length}</p>
              <p className="text-cyan-400 font-semibold text-lg">{accuracy}% Accuracy</p>
            </div>
          </div>

          {/* Progress Bar */}
          <div className="w-full bg-slate-800 rounded-full h-1.5 overflow-hidden">
            <div
              className="bg-gradient-to-r from-cyan-500 via-blue-500 to-purple-500 h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${progress}%` }}
            ></div>
          </div>
        </div>
      </div>

      {/* Main Content with Sidebar */}
      <div className="max-w-7xl mx-auto px-6 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
          {/* Sidebar - Topics */}
          <div className="lg:col-span-1">
            <div className="sticky top-24 space-y-4">
              <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-2xl p-6">
                <h2 className="text-lg font-bold text-slate-100 mb-4 flex items-center gap-2">
                  <BookOpen className="w-5 h-5 text-cyan-400" />
                  Topics
                </h2>
                <div className="space-y-3 max-h-96 overflow-y-auto pr-2">
                  {topics.map((topic) => {
                    const topicQuizzes = topicQuizMap[topic.id] || []
                    const topicQuizzesAnswered = allQuizzes.filter(q => q.topicId === topic.id && currentGlobalQuizIndex >= allQuizzes.indexOf(q)).length

                    return (
                      <div
                        key={topic.id}
                        className="p-4 rounded-xl bg-slate-700/30 border border-slate-700 hover:border-cyan-500/50 transition-all"
                      >
                        <div className="flex items-start justify-between mb-2">
                          <h3 className="font-semibold text-slate-200 text-sm flex-1">{topic.title}</h3>
                          <span className="text-xs px-2 py-1 rounded-lg bg-cyan-500/20 text-cyan-400 font-semibold flex-shrink-0 ml-2">
                            {topicQuizzes.length}Q
                          </span>
                        </div>

                        <div className="space-y-2 text-xs">
                          <div className="flex justify-between items-center">
                            <span className="text-slate-400">Priority</span>
                            <span className="text-cyan-400 font-semibold">{topic.priorityScore?.toFixed(2)}</span>
                          </div>
                          <div className="flex justify-between items-center">
                            <span className="text-slate-400">Importance</span>
                            <span className="text-blue-400 font-semibold">{topic.importanceScore?.toFixed(2)}</span>
                          </div>
                          <div className="flex justify-between items-center">
                            <span className="text-slate-400">Complexity</span>
                            <span className="text-purple-400 font-semibold">{topic.complexityScore?.toFixed(2)}</span>
                          </div>
                          {topic.weaknessScore !== null && topic.weaknessScore !== undefined && (
                            <div className="flex justify-between items-center">
                              <span className="text-slate-400">Weakness</span>
                              <span className="text-red-400 font-semibold">{topic.weaknessScore?.toFixed(2)}</span>
                            </div>
                          )}
                        </div>

                        {/* Progress Bar for Topic */}
                        <div className="mt-3 pt-3 border-t border-slate-600">
                          <div className="w-full bg-slate-700 rounded-full h-1.5 overflow-hidden">
                            <div
                              className="bg-gradient-to-r from-cyan-500 to-blue-500 h-1.5 rounded-full transition-all"
                              style={{ width: `${topicQuizzes.length > 0 ? (topicQuizzesAnswered / topicQuizzes.length) * 100 : 0}%` }}
                            ></div>
                          </div>
                          <p className="text-xs text-slate-400 mt-1">{topicQuizzesAnswered}/{topicQuizzes.length} completed</p>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>

              {/* Session Stats Card */}
              {quizStats.total > 0 && (
                <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-2xl p-6">
                  <h3 className="text-sm font-bold text-slate-100 mb-4 flex items-center gap-2">
                    <BarChart3 className="w-4 h-4 text-cyan-400" />
                    Session Stats
                  </h3>
                  <div className="space-y-3 text-sm">
                    <div>
                      <div className="flex justify-between mb-1">
                        <span className="text-slate-400">Accuracy</span>
                        <span className="text-slate-200 font-semibold">{accuracy}%</span>
                      </div>
                      <div className="w-full bg-slate-700 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full transition-all ${
                            accuracy >= 70 ? 'bg-emerald-500' : accuracy >= 50 ? 'bg-yellow-500' : 'bg-red-500'
                          }`}
                          style={{ width: `${accuracy}%` }}
                        ></div>
                      </div>
                    </div>
                    <div className="flex justify-between text-slate-300">
                      <span>Correct: <span className="text-emerald-400 font-semibold">{quizStats.correct}</span></span>
                      <span>Total: <span className="text-cyan-400 font-semibold">{quizStats.total}</span></span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Main Quiz Content */}
          <div className="lg:col-span-3">
        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/30 rounded-lg flex items-center gap-3 backdrop-blur">
            <AlertCircle className="w-5 h-5 text-red-500 flex-shrink-0" />
            <p className="text-red-400 text-sm">{error}</p>
          </div>
        )}

        {/* Quiz Card */}
        <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-2xl p-8 shadow-2xl">
          {/* Topic & Difficulty */}
          <div className="flex items-center justify-between mb-6 pb-6 border-b border-slate-700">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-cyan-500/20 flex items-center justify-center">
                <BookOpen className="w-5 h-5 text-cyan-400" />
              </div>
              <div>
                <p className="text-slate-400 text-sm">Topic</p>
                <p className="text-slate-100 font-semibold">{currentTopic?.title || 'Loading...'}</p>
              </div>
            </div>

            <div className="text-right">
              <p className="text-slate-400 text-sm">Difficulty</p>
              <p className={`font-semibold text-sm ${
                currentQuiz.difficulty === 'hard' ? 'text-red-400' :
                currentQuiz.difficulty === 'medium' ? 'text-yellow-400' :
                'text-emerald-400'
              }`}>
                {currentQuiz.difficulty?.charAt(0).toUpperCase() + currentQuiz.difficulty?.slice(1)}
              </p>
            </div>
          </div>

          {/* Question */}
          <div className="mb-8">
            <h2 className="text-2xl font-bold text-slate-100 mb-8 leading-relaxed">{currentQuiz.question}</h2>

            {/* Options */}
            <div className="space-y-3">
              {[
                { label: 'A', value: currentQuiz.optionA },
                { label: 'B', value: currentQuiz.optionB },
                { label: 'C', value: currentQuiz.optionC },
                { label: 'D', value: currentQuiz.optionD },
              ].map((option) => {
                const isSelected = selectedAnswer === option.value
                const isCorrect = result?.correctAnswer === option.value
                const isUserWrong = submitted && selectedAnswer === option.value && !result?.isCorrect

                return (
                  <label
                    key={option.label}
                    className={`flex items-start p-4 rounded-xl border-2 cursor-pointer transition-all ${
                      isUserWrong
                        ? 'border-red-500/50 bg-red-500/10'
                        : submitted && isCorrect
                        ? 'border-emerald-500/50 bg-emerald-500/10'
                        : isSelected && !submitted
                        ? 'border-cyan-500 bg-cyan-500/10 shadow-lg shadow-cyan-500/20'
                        : 'border-slate-700 bg-slate-700/30 hover:border-slate-600 hover:bg-slate-700/50'
                    } ${submitted ? 'cursor-not-allowed' : 'hover:shadow-md'}`}
                  >
                    <input
                      type="radio"
                      name="answer"
                      value={option.value}
                      checked={isSelected}
                      onChange={(e) => setSelectedAnswer(e.target.value)}
                      disabled={submitted}
                      className="hidden"
                    />
                    <div className={`w-6 h-6 rounded-full border-2 flex items-center justify-center flex-shrink-0 mt-0.5 ${
                      isSelected ? 'border-cyan-500 bg-cyan-500' : 'border-slate-600'
                    }`}>
                      {isSelected && <div className="w-2 h-2 bg-white rounded-full"></div>}
                    </div>
                    <div className="ml-4 flex-1">
                      <p className="font-semibold text-slate-200">{option.label}. {option.value}</p>
                    </div>
                    {submitted && isCorrect && <CheckCircle className="w-6 h-6 text-emerald-500 flex-shrink-0" />}
                    {submitted && isUserWrong && <XCircle className="w-6 h-6 text-red-500 flex-shrink-0" />}
                  </label>
                )
              })}
            </div>
          </div>

          {/* Result Feedback */}
          {submitted && result && (
            <div className={`mb-8 p-6 rounded-xl border ${
              result.isCorrect
                ? 'bg-emerald-500/10 border-emerald-500/30'
                : 'bg-red-500/10 border-red-500/30'
            }`}>
              <div className="flex items-center gap-3 mb-3">
                {result.isCorrect ? (
                  <CheckCircle className="w-6 h-6 text-emerald-500" />
                ) : (
                  <XCircle className="w-6 h-6 text-red-500" />
                )}
                <p className={`font-semibold text-lg ${
                  result.isCorrect ? 'text-emerald-400' : 'text-red-400'
                }`}>
                  {result.isCorrect ? 'Correct!' : 'Incorrect'}
                </p>
              </div>
              {result.explanation && (
                <p className="text-slate-300 leading-relaxed">{result.explanation}</p>
              )}
            </div>
          )}

          {/* Action Button */}
          <div className="flex gap-4">
            {!submitted ? (
              <button
                onClick={handleSubmitQuiz}
                disabled={!selectedAnswer}
                className="flex-1 bg-gradient-to-r from-cyan-500 to-blue-500 hover:from-cyan-600 hover:to-blue-600 disabled:from-slate-600 disabled:to-slate-700 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold py-3 px-6 rounded-xl transition-all flex items-center justify-center gap-2 shadow-lg"
              >
                <CheckCircle className="w-5 h-5" />
                Submit Answer
              </button>
            ) : (
              <button
                onClick={handleNextQuiz}
                className="flex-1 bg-gradient-to-r from-cyan-500 to-blue-500 hover:from-cyan-600 hover:to-blue-600 text-white font-semibold py-3 px-6 rounded-xl transition-all flex items-center justify-center gap-2 shadow-lg"
              >
                {isLastQuiz ? (
                  <>
                    <CheckCircle className="w-5 h-5" />
                    Complete Session
                  </>
                ) : (
                  <>
                    Next Question
                    <ArrowRight className="w-5 h-5" />
                  </>
                )}
              </button>
            )}
          </div>
        </div>

        {/* Stats Bar */}
        {quizStats.total > 0 && (
          <div className="mt-8 grid grid-cols-4 gap-4">
            <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-xl p-4">
              <p className="text-slate-400 text-sm mb-1">Correct</p>
              <p className="text-2xl font-bold text-emerald-400">{quizStats.correct}</p>
            </div>
            <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-xl p-4">
              <p className="text-slate-400 text-sm mb-1">Total</p>
              <p className="text-2xl font-bold text-slate-300">{quizStats.total}</p>
            </div>
            <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-xl p-4">
              <p className="text-slate-400 text-sm mb-1">Accuracy</p>
              <p className={`text-2xl font-bold ${
                accuracy >= 70 ? 'text-emerald-400' : accuracy >= 50 ? 'text-yellow-400' : 'text-red-400'
              }`}>{accuracy}%</p>
            </div>
            <div className="bg-slate-800/50 backdrop-blur border border-slate-700 rounded-xl p-4">
              <p className="text-slate-400 text-sm mb-1">Remaining</p>
              <p className="text-2xl font-bold text-cyan-400">{allQuizzes.length - quizStats.total}</p>
            </div>
          </div>
        )}
        </div>
        </div>
      </div>
    </div>
  )
}
