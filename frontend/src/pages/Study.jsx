import React, { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { pdfAPI, topicAPI, quizAPI, dashboardAPI, recommendationAPI } from '../api'
import { StudyTimer } from '../components/StudyTimer'
import { motion, AnimatePresence } from 'framer-motion'
import confetti from 'canvas-confetti'
import toast from 'react-hot-toast'
import { BookOpen, AlertCircle, CheckCircle, XCircle, ArrowRight, BarChart3, ArrowLeft } from 'lucide-react'

const container = { hidden: { opacity: 0 }, show: { opacity: 1, transition: { staggerChildren: 0.07 } } }
const item = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.35 } } }

export const Study = () => {
  const { pdfId } = useParams()
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

  useEffect(() => { fetchTopicsAndQuizzes() }, [pdfId])

  const fetchTopicsAndQuizzes = async () => {
    try {
      const topicsResponse = pdfId ? await topicAPI.getByPdf(pdfId) : await topicAPI.getRanked()
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
    } finally { setLoading(false) }
  }

  const handleSubmitQuiz = async () => {
    if (!selectedAnswer) { setError('Please select an answer'); return }
    const timeTaken = Math.floor((Date.now() - startTime) / 1000)
    try {
      const response = await quizAPI.submit(currentQuiz.id, { selectedAnswer, timeTakenSeconds: timeTaken })
      setResult(response.data)
      setSubmitted(true)
      setQuizStats(prev => ({ correct: prev.correct + (response.data.isCorrect ? 1 : 0), total: prev.total + 1 }))
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
    if (attempts <= 3) multiplier = 1.8
    else if (attempts <= 8) multiplier = 1.2
    return (1 - accuracy) * multiplier
  }

  const fireConfetti = () => {
    const duration = 2000
    const end = Date.now() + duration
    const frame = () => {
      confetti({ particleCount: 3, angle: 60, spread: 55, origin: { x: 0, y: 0.7 }, colors: ['#0058bc', '#0070eb', '#adc6ff', '#61f5ed'] })
      confetti({ particleCount: 3, angle: 120, spread: 55, origin: { x: 1, y: 0.7 }, colors: ['#0058bc', '#0070eb', '#adc6ff', '#61f5ed'] })
      if (Date.now() < end) requestAnimationFrame(frame)
    }
    frame()
  }

  const handleSessionComplete = async () => {
    const accuracy = quizStats.total > 0 ? quizStats.correct / quizStats.total : 0
    const weakness = calculateWeakness(accuracy, quizStats.total)
    try {
      for (const topic of topics) {
        await topicAPI.updateWeakness(topic.id, { weakness, accuracy, attempts: quizStats.total })
      }
      try { await dashboardAPI.get(); await recommendationAPI.getNextTopics(10) } catch (_) {}
      fireConfetti()
      toast.success('Session complete! Great work!')
      setSessionComplete(true)
      setTimeout(() => navigate('/planner'), 2500)
    } catch (err) {
      console.error('Error completing session:', err)
      setError('Failed to complete session: ' + (err.response?.data?.message || err.message))
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center animate-fade-in">
          <div className="animate-spin rounded-full h-10 w-10 border-2 border-primary border-t-transparent mx-auto mb-4" />
          <p className="text-on-surface-variant">Loading your study session...</p>
        </div>
      </div>
    )
  }

  if (allQuizzes.length === 0) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="glass-pane rounded-xl p-8 border border-black/8 max-w-md text-center animate-fade-in">
          <AlertCircle className="w-12 h-12 text-amber-400 mx-auto mb-4" />
          <h2 className="text-xl font-bold text-on-surface mb-2">No Quizzes Available</h2>
          <p className="text-on-surface-variant/70">Upload and analyze a PDF to start your study session</p>
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
      <div className="flex items-center justify-center min-h-[60vh]">
        <motion.div initial={{ scale: 0.9, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} className="text-center max-w-md">
          <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 200, delay: 0.2 }}>
            <CheckCircle className="w-20 h-20 text-emerald-500 mx-auto mb-4" />
          </motion.div>
          <h2 className="text-3xl font-bold text-on-surface mb-2">Session Complete!</h2>
          <p className="text-on-surface-variant/70 mb-8">Your performance has been analyzed and topic priorities updated.</p>
          <div className="glass-pane rounded-xl p-6 border border-black/8 mb-6">
            <div className="grid grid-cols-3 gap-4">
              <div>
                <p className="text-on-surface-variant/70 text-sm mb-1">Correct</p>
                <p className="text-2xl font-bold text-emerald-600">{quizStats.correct}</p>
              </div>
              <div>
                <p className="text-on-surface-variant/70 text-sm mb-1">Total</p>
                <p className="text-2xl font-bold text-on-surface">{quizStats.total}</p>
              </div>
              <div>
                <p className="text-on-surface-variant/70 text-sm mb-1">Accuracy</p>
                <p className={`text-2xl font-bold ${accuracy >= 70 ? 'text-emerald-600' : accuracy >= 50 ? 'text-yellow-600' : 'text-error'}`}>{accuracy}%</p>
              </div>
            </div>
          </div>
          <p className="text-on-surface-variant/70 text-sm">Redirecting to your study planner...</p>
        </motion.div>
      </div>
    )
  }

  return (
    <motion.div variants={container} initial={false} animate="show" className="space-y-6">
      {/* Error */}
      {error && (
        <motion.div variants={item} className="glass-pane rounded-xl p-4 border border-black/8 flex items-center gap-3 bg-red-50/80 border border-red-200/50">
          <AlertCircle className="w-5 h-5 text-red-700 flex-shrink-0" />
          <p className="text-red-700 text-sm">{error}</p>
        </motion.div>
      )}

      <motion.div variants={item}>
        <button onClick={() => navigate('/dashboard')} className="flex items-center gap-2 text-on-surface-variant/70 hover:text-primary transition-colors text-sm"><ArrowLeft className="w-4 h-4" /> Back to Dashboard</button>
      </motion.div>

      {/* Header */}
      <motion.div variants={item} className="glass-pane rounded-xl p-6 border border-black/8">
        <div className="flex items-center justify-between mb-3">
          <div>
            <h1 className="text-xl font-bold text-on-surface">Study Session</h1>
            <p className="text-sm text-on-surface-variant/70">Complete all quizzes to update your learning priorities</p>
          </div>
          <div className="text-right">
            <p className="text-sm text-on-surface-variant/70">Question {currentGlobalQuizIndex + 1} of {allQuizzes.length}</p>
            <p className="text-primary font-semibold">{accuracy}% Accuracy</p>
          </div>
        </div>
        <div className="w-full bg-white/40 rounded-full h-1.5 overflow-hidden backdrop-blur-sm">
          <motion.div
            className="bg-gradient-to-r from-primary to-primary-container h-1.5 rounded-full"
            initial={{ width: 0 }}
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.4 }}
          />
        </div>
      </motion.div>

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Sidebar */}
        <div className="lg:col-span-1 order-2 lg:order-1">
          <div className="space-y-6 lg:sticky lg:top-28">
            <div className="glass-pane rounded-xl p-6 border border-black/8">
              <h2 className="text-lg font-bold text-on-surface mb-4 flex items-center gap-2">
                <BookOpen className="w-5 h-5 text-primary" />
                Topics
              </h2>
              <div className="space-y-3 max-h-96 overflow-y-auto pr-2">
                {topics.map((topic) => {
                  const topicQuizzes = topicQuizMap[topic.id] || []
                  const topicQuizzesAnswered = allQuizzes.filter(q => q.topicId === topic.id && currentGlobalQuizIndex >= allQuizzes.indexOf(q)).length
                  return (
                    <div key={topic.id} className="bg-white/40 backdrop-blur-sm border border-black/8 rounded-xl p-4">
                      <div className="flex items-start justify-between mb-2">
                        <h3 className="font-semibold text-on-surface text-sm flex-1">{topic.title}</h3>
                        <span className="text-xs px-2 py-1 rounded-lg bg-primary/10 text-primary font-semibold flex-shrink-0 ml-2">{topicQuizzes.length}Q</span>
                      </div>
                      <div className="space-y-1.5 text-xs">
                        <div className="flex justify-between">
                          <span className="text-on-surface-variant/70">Priority</span>
                          <span className="text-primary font-semibold">{topic.priorityScore?.toFixed(2)}</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-on-surface-variant/70">Weakness</span>
                          <span className="text-error font-semibold">{topic.weaknessScore?.toFixed(2)}</span>
                        </div>
                      </div>
                      <div className="mt-2 pt-2 border-t border-white/20">
                        <div className="w-full bg-white/40 rounded-full h-1.5 overflow-hidden">
                          <motion.div
                            className="bg-gradient-to-r from-primary to-primary-container h-1.5 rounded-full"
                            initial={{ width: 0 }}
                            animate={{ width: `${topicQuizzes.length > 0 ? (topicQuizzesAnswered / topicQuizzes.length) * 100 : 0}%` }}
                            transition={{ duration: 0.5 }}
                          />
                        </div>
                        <p className="text-xs text-on-surface-variant/70 mt-1">{topicQuizzesAnswered}/{topicQuizzes.length} completed</p>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>

            {quizStats.total > 0 && (
              <div className="glass-pane rounded-xl p-6 border border-black/8">
                <h3 className="text-sm font-bold text-on-surface mb-4 flex items-center gap-2">
                  <BarChart3 className="w-4 h-4 text-primary" />
                  Session Stats
                </h3>
                <div className="space-y-3 text-sm">
                  <div>
                    <div className="flex justify-between mb-1">
                      <span className="text-on-surface-variant/70">Accuracy</span>
                      <span className="text-on-surface font-semibold">{accuracy}%</span>
                    </div>
                    <div className="w-full bg-white/40 rounded-full h-2 backdrop-blur-sm">
                      <motion.div
                        className={`h-2 rounded-full ${accuracy >= 70 ? 'bg-emerald-500' : accuracy >= 50 ? 'bg-yellow-500' : 'bg-error'}`}
                        initial={{ width: 0 }}
                        animate={{ width: `${accuracy}%` }}
                        transition={{ duration: 0.5 }}
                      />
                    </div>
                  </div>
                  <div className="flex justify-between text-on-surface-variant">
                    <span>Correct: <span className="text-emerald-600 font-semibold">{quizStats.correct}</span></span>
                    <span>Total: <span className="text-primary font-semibold">{quizStats.total}</span></span>
                  </div>
                </div>
              </div>
            )}

            <StudyTimer />
          </div>
        </div>

        {/* Main Quiz Content */}
        <div className="lg:col-span-3 order-1 lg:order-2">
          <AnimatePresence mode="wait">
            <motion.div
              key={currentGlobalQuizIndex}
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              transition={{ duration: 0.25 }}
            >
              <div className="glass-pane rounded-xl p-8 border border-black/8">
                <div className="flex items-center justify-between mb-6 pb-6 border-b border-white/20">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                      <BookOpen className="w-5 h-5 text-primary" />
                    </div>
                    <div>
                      <p className="text-on-surface-variant/70 text-sm">Topic</p>
                      <p className="text-on-surface font-semibold">{currentTopic?.title || 'Loading...'}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-on-surface-variant/70 text-sm">Difficulty</p>
                    <p className={`font-semibold text-sm ${
                      currentQuiz.difficulty === 'hard' ? 'text-error' :
                      currentQuiz.difficulty === 'medium' ? 'text-yellow-600' : 'text-emerald-600'
                    }`}>
                      {currentQuiz.difficulty?.charAt(0).toUpperCase() + currentQuiz.difficulty?.slice(1)}
                    </p>
                  </div>
                </div>

                <div className="mb-8">
                  <h2 className="text-xl font-bold text-on-surface mb-8 leading-relaxed">{currentQuiz.question}</h2>
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
                            isUserWrong ? 'border-red-200/50 bg-red-50/80' :
                            submitted && isCorrect ? 'border-emerald-200 bg-emerald-50/80' :
                            isSelected && !submitted ? 'border-primary/20 bg-primary/10' :
                            'bg-white/40 backdrop-blur-sm border border-black/8 hover:bg-white/60'
                          } ${submitted ? 'cursor-not-allowed' : ''}`}
                        >
                          <input type="radio" name="answer" value={option.value} checked={isSelected}
                            onChange={(e) => setSelectedAnswer(e.target.value)} disabled={submitted} className="hidden" />
                          <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 mt-0.5 ${
                            isSelected ? 'border-primary' : 'border-white/40'
                          }`}>
                            {isSelected && <div className="w-2.5 h-2.5 bg-primary rounded-full" />}
                          </div>
                          <div className="ml-3 flex-1">
                            <p className="font-semibold text-on-surface">{option.label}. {option.value}</p>
                          </div>
                          {submitted && isCorrect && <CheckCircle className="w-5 h-5 text-emerald-500 flex-shrink-0" />}
                          {submitted && isUserWrong && <XCircle className="w-5 h-5 text-error flex-shrink-0" />}
                        </label>
                      )
                    })}
                  </div>
                </div>

                {submitted && result && (
                  <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
                    className={`mb-8 p-6 rounded-xl border ${
                      result.isCorrect ? 'bg-emerald-50/80 border-emerald-200' : 'bg-red-50/80 border-red-200/50'
                    }`}
                  >
                    <div className="flex items-center gap-3 mb-2">
                      {result.isCorrect ? <CheckCircle className="w-5 h-5 text-emerald-500" /> : <XCircle className="w-5 h-5 text-error" />}
                      <p className={`font-semibold ${result.isCorrect ? 'text-emerald-700' : 'text-error'}`}>
                        {result.isCorrect ? 'Correct!' : 'Incorrect'}
                      </p>
                    </div>
                    {result.explanation && <p className="text-on-surface-variant text-sm leading-relaxed">{result.explanation}</p>}
                  </motion.div>
                )}

                <div className="flex gap-4">
                  {!submitted ? (
                    <button onClick={handleSubmitQuiz} disabled={!selectedAnswer}
                      className="btn-glass-primary flex-1 disabled:opacity-50 disabled:cursor-not-allowed">
                      <CheckCircle className="w-5 h-5" /> Submit Answer
                    </button>
                  ) : (
                    <button onClick={handleNextQuiz} className="btn-glass-primary flex-1">
                      {isLastQuiz ? <><CheckCircle className="w-5 h-5" /> Complete Session</> : <>Next Question <ArrowRight className="w-5 h-5" /></>}
                    </button>
                  )}
                </div>
              </div>

              {quizStats.total > 0 && (
                <div className="mt-6 grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div className="glass-pane rounded-xl p-4 border border-black/8">
                    <p className="text-on-surface-variant/70 text-xs mb-1">Correct</p>
                    <p className="text-xl font-bold text-emerald-600">{quizStats.correct}</p>
                  </div>
                  <div className="glass-pane rounded-xl p-4 border border-black/8">
                    <p className="text-on-surface-variant/70 text-xs mb-1">Total</p>
                    <p className="text-xl font-bold text-on-surface">{quizStats.total}</p>
                  </div>
                  <div className="glass-pane rounded-xl p-4 border border-black/8">
                    <p className="text-on-surface-variant/70 text-xs mb-1">Accuracy</p>
                    <p className={`text-xl font-bold ${accuracy >= 70 ? 'text-emerald-600' : accuracy >= 50 ? 'text-yellow-600' : 'text-error'}`}>{accuracy}%</p>
                  </div>
                  <div className="glass-pane rounded-xl p-4 border border-black/8">
                    <p className="text-on-surface-variant/70 text-xs mb-1">Remaining</p>
                    <p className="text-xl font-bold text-primary">{allQuizzes.length - quizStats.total}</p>
                  </div>
                </div>
              )}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </motion.div>
  )
}
