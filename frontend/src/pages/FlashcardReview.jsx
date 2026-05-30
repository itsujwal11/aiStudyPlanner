import React, { useState, useEffect } from 'react'
import { flashcardAPI } from '../api'
import { Brain, RotateCcw, ThumbsUp, ThumbsDown, ChevronRight, BookOpen } from 'lucide-react'

export const FlashcardReview = () => {
  const [cards, setCards] = useState([])
  const [index, setIndex] = useState(0)
  const [showAnswer, setShowAnswer] = useState(false)
  const [loading, setLoading] = useState(true)
  const [done, setDone] = useState(false)
  const [err, setErr] = useState('')
  const [stats, setStats] = useState({ reviewed: 0, remembered: 0 })

  useEffect(() => { fetchDueCards() }, [])

  const fetchDueCards = async () => {
    try {
      const res = await flashcardAPI.getDue()
      setCards(res.data || [])
    } catch (e) {
      setErr('Failed to load flashcards. Restart backend and try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleRate = async (rating) => {
    if (!cards[index]) return
    try {
      await flashcardAPI.review(cards[index].id, rating)
      setStats(s => ({ reviewed: s.reviewed + 1, remembered: s.remembered + (rating >= 3 ? 1 : 0) }))
      if (index < cards.length - 1) {
        setIndex(index + 1)
        setShowAnswer(false)
      } else {
        setDone(true)
      }
    } catch (e) {
      console.error(e)
    }
  }

  if (loading) return <div className="flex items-center justify-center min-h-[80vh]"><div className="animate-spin rounded-full h-10 w-10 border-2 border-primary border-t-transparent" /></div>

  if (err) return <div className="flex items-center justify-center min-h-[80vh]"><div className="text-center"><BookOpen className="w-16 h-16 text-on-surface-variant/30 mx-auto mb-4" /><h2 className="text-xl font-bold text-on-surface">Error</h2><p className="text-on-surface-variant/70">{err}</p></div></div>

  if (done) {
    const accuracy = stats.reviewed > 0 ? Math.round((stats.remembered / stats.reviewed) * 100) : 0
    return (
      <div className="flex items-center justify-center min-h-[80vh]">
        <div className="text-center max-w-md">
          <div className="w-20 h-20 rounded-full bg-emerald-100 flex items-center justify-center mx-auto mb-6"><Brain className="w-10 h-10 text-emerald-600" /></div>
          <h2 className="text-2xl font-bold text-on-surface mb-2">Review Complete!</h2>
          <div className="glass-pane rounded-xl p-6 border border-black/8 mt-6">
            <div className="grid grid-cols-3 gap-4">
              <div><p className="text-sm text-on-surface-variant/70">Reviewed</p><p className="text-2xl font-bold text-on-surface">{stats.reviewed}</p></div>
              <div><p className="text-sm text-on-surface-variant/70">Remembered</p><p className="text-2xl font-bold text-emerald-600">{stats.remembered}</p></div>
              <div><p className="text-sm text-on-surface-variant/70">Accuracy</p><p className="text-2xl font-bold text-primary">{accuracy}%</p></div>
            </div>
          </div>
          <button onClick={() => { setDone(false); setIndex(0); setShowAnswer(false); setStats({ reviewed: 0, remembered: 0 }); fetchDueCards() }} className="btn-glass-primary mt-6"><RotateCcw className="w-4 h-4" /> Review Again</button>
        </div>
      </div>
    )
  }

  if (cards.length === 0) return <div className="flex items-center justify-center min-h-[80vh]"><div className="text-center max-w-md"><BookOpen className="w-16 h-16 text-on-surface-variant/30 mx-auto mb-4" /><h2 className="text-xl font-bold text-on-surface mb-2">All Caught Up!</h2><p className="text-on-surface-variant/70">No flashcards due for review. Upload and analyze a PDF to generate cards.</p></div></div>

  const current = cards[index]

  return (
    <div className="max-w-2xl mx-auto py-8 px-4">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-on-surface">Flashcard Review</h1>
        <span className="text-sm text-on-surface-variant/70">{index + 1} / {cards.length}</span>
      </div>
      <div className="w-full bg-white/40 rounded-full h-1.5 mb-8">
        <div className="h-1.5 rounded-full bg-gradient-to-r from-primary to-primary-container" style={{ width: `${(index / cards.length) * 100}%` }} />
      </div>
      <div className="glass-pane rounded-xl p-8 border border-black/8 mb-6">
        <p className="text-xl text-on-surface font-semibold text-center mb-6">{current.frontText}</p>
        <hr className="border-white/20 max-w-xs mx-auto" />
        {showAnswer ? (
          <p className="text-lg text-on-surface text-center mt-6">{current.backText}</p>
        ) : (
          <button onClick={() => setShowAnswer(true)} className="btn-glass-secondary w-full mt-6 flex items-center justify-center gap-2"><ChevronRight className="w-4 h-4" /> Show Answer</button>
        )}
      </div>
      {showAnswer && (
        <div className="flex justify-center gap-3 flex-wrap">
          <button onClick={() => handleRate(1)} className="btn-glass-secondary flex items-center gap-2 px-5 py-2.5"><ThumbsDown className="w-4 h-4 text-error" /> Again</button>
          <button onClick={() => handleRate(2)} className="btn-glass-secondary flex items-center gap-2 px-5 py-2.5"><ChevronRight className="w-4 h-4 text-yellow-600" /> Hard</button>
          <button onClick={() => handleRate(3)} className="btn-glass-primary flex items-center gap-2 px-5 py-2.5"><ThumbsUp className="w-4 h-4" /> Good</button>
          <button onClick={() => handleRate(4)} className="btn-glass-primary flex items-center gap-2 px-5 py-2.5"><ThumbsUp className="w-4 h-4 text-emerald-500" /> Easy</button>
        </div>
      )}
    </div>
  )
}
