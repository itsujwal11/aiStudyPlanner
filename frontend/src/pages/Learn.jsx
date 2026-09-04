import React, { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { ArrowLeft, ArrowRight, BookOpen, Brain, HelpCircle, Target } from 'lucide-react'
import { topicAPI } from '../api'

export const Learn = () => {
  const { pdfId } = useParams()
  const navigate = useNavigate()
  const [topics, setTopics] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const load = async () => {
      try {
        const response = pdfId ? await topicAPI.getByPdf(pdfId) : await topicAPI.getRanked()
        setTopics(response.data || [])
      } catch (error) {
        console.error(error)
        toast.error('Could not load your learning topics')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [pdfId])

  if (loading) {
    return <div className="min-h-[60vh] flex items-center justify-center"><div className="animate-spin rounded-full h-10 w-10 border-2 border-primary border-t-transparent" /></div>
  }

  if (!topics.length) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="glass-pane rounded-xl p-8 text-center max-w-md border border-black/8">
          <BookOpen className="w-12 h-12 text-primary mx-auto mb-4" />
          <h1 className="text-2xl font-bold text-on-surface">No learning content yet</h1>
          <p className="text-on-surface-variant/70 mt-2 mb-6">Upload a PDF first. Its generated topics will appear here as your learning path.</p>
          <button className="btn-glass-primary" onClick={() => navigate('/upload')}>Upload PDF</button>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-7">
      <button onClick={() => navigate('/dashboard')} className="flex items-center gap-2 text-sm text-on-surface-variant/70 hover:text-primary">
        <ArrowLeft className="w-4 h-4" /> Back to Dashboard
      </button>

      <div className="glass-pane rounded-xl p-7 border border-black/8">
        <div className="flex flex-col md:flex-row md:items-center gap-5">
          <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center"><BookOpen className="w-6 h-6 text-primary" /></div>
          <div className="flex-1">
            <h1 className="text-3xl font-bold text-on-surface">Learn</h1>
            <p className="text-on-surface-variant/70 mt-1">Review concepts from your PDF first, then practise the topics that need evidence or improvement.</p>
          </div>
          <button className="btn-glass-primary" onClick={() => navigate(pdfId ? `/practice/${pdfId}` : '/practice')}>
            Open Practice <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {topics.map((topic, index) => {
          const needsWork = (topic.weaknessScore ?? 1) >= 0.4
          return (
            <motion.article key={topic.id} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.04 }}
              className="glass-pane rounded-xl p-6 border border-black/8 flex flex-col">
              <div className="flex items-start gap-4">
                <div className="w-10 h-10 rounded-xl bg-primary/10 text-primary flex items-center justify-center font-bold">{index + 1}</div>
                <div className="flex-1 min-w-0">
                  <h2 className="text-lg font-bold text-on-surface">{topic.title}</h2>
                  <p className="text-sm text-on-surface-variant/70 mt-2 leading-relaxed">
                    {topic.description || 'Review this topic from the source PDF, focusing on its definitions, relationships, and practical examples.'}
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-3 gap-3 my-5">
                <div className="rounded-xl bg-white/40 p-3"><p className="text-xs text-on-surface-variant/60">Complexity</p><p className="font-bold text-on-surface">{Math.round((topic.complexityScore || 0) * 100)}%</p></div>
                <div className="rounded-xl bg-white/40 p-3"><p className="text-xs text-on-surface-variant/60">Importance</p><p className="font-bold text-on-surface">{Math.round((topic.importanceScore || 0) * 100)}%</p></div>
                <div className="rounded-xl bg-white/40 p-3"><p className="text-xs text-on-surface-variant/60">Questions</p><p className="font-bold text-on-surface">{topic.quizCount || 0}</p></div>
              </div>

              <div className="mt-auto flex flex-wrap gap-3">
                <button onClick={() => navigate('/quick-answers')} className="btn-glass-secondary text-sm">
                  <HelpCircle className="w-4 h-4" /> Quick Answers
                </button>
                <button onClick={() => navigate(`${pdfId ? `/practice/${pdfId}` : '/practice'}?topicId=${topic.id}`)} className="btn-glass-primary text-sm">
                  {needsWork ? <Target className="w-4 h-4" /> : <Brain className="w-4 h-4" />} Practise Topic
                </button>
              </div>
            </motion.article>
          )
        })}
      </div>
    </div>
  )
}
