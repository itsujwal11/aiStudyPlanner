import React, { useEffect, useState } from 'react'
import { ragAPI, pdfAPI } from '../api'
import { motion } from 'framer-motion'
import { AlertCircle, BookOpen, Database, FileText, Loader2, Sparkles } from 'lucide-react'

const categories = [
  ['OVERVIEW', 'PDF overview'],
  ['TOPIC_OVERVIEW', 'What is it?'],
  ['STUDY_GUIDANCE', 'How to study'],
]

const formatInline = (text) => (
  text.split(/(\*\*[^*]+\*\*)/g).map((part, index) => (
    part.startsWith('**') && part.endsWith('**')
      ? <strong key={index} className="font-semibold">{part.slice(2, -2)}</strong>
      : <React.Fragment key={index}>{part}</React.Fragment>
  ))
)

const FormattedAnswer = ({ content }) => (
  <div className="text-sm md:text-base leading-7 text-on-surface space-y-2">
    {content.split(/\r?\n/).map((rawLine, index) => {
      const line = rawLine.trim()
      if (!line) return <div key={index} className="h-1" />

      const numbered = line.match(/^(\d+)\.\s+(.+)$/)
      if (numbered) {
        return (
          <div key={index} className="flex items-start gap-2">
            <span className="font-semibold text-emerald-700 min-w-5">{numbered[1]}.</span>
            <span>{formatInline(numbered[2])}</span>
          </div>
        )
      }

      const bullet = line.match(/^[-*]\s+(.+)$/)
      if (bullet) {
        return (
          <div key={index} className="flex items-start gap-2">
            <span className="text-emerald-600">•</span>
            <span>{formatInline(bullet[1])}</span>
          </div>
        )
      }

      return <p key={index}>{formatInline(line)}</p>
    })}
  </div>
)

export const QuickAnswers = () => {
  const [pdfs, setPdfs] = useState([])
  const [selectedPdfId, setSelectedPdfId] = useState(null)
  const [answers, setAnswers] = useState([])
  const [category, setCategory] = useState('TOPIC_OVERVIEW')
  const [selectedAnswer, setSelectedAnswer] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    pdfAPI.list()
      .then((response) => setPdfs(response.data || []))
      .catch(() => setError('Failed to load your PDFs'))
  }, [])

  useEffect(() => {
    setLoading(true)
    setError('')
    setSelectedAnswer(null)
    ragAPI.getPredefinedAnswers(selectedPdfId)
      .then((response) => setAnswers(response.data || []))
      .catch((err) => {
        setAnswers([])
        setError(err.response?.data?.error || 'Failed to load quick answers')
      })
      .finally(() => setLoading(false))
  }, [selectedPdfId])

  const visibleAnswers = answers.filter((answer) => answer.type === category)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl md:text-[40px] font-semibold text-on-surface leading-[48px]">
          Quick Answers
        </h1>
        <p className="text-lg text-on-surface-variant/70 mt-1">
          Instant answers generated from your saved PDF topics—no additional AI quota used.
        </p>
      </div>

      {error && (
        <div className="glass-pane rounded-xl p-4 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}

      {pdfs.length > 0 && (
        <div className="glass-pane rounded-xl p-4 border border-black/8">
          <div className="flex items-center gap-3 flex-wrap">
            <BookOpen className="w-4 h-4 text-emerald-600" />
            <span className="text-sm font-medium text-on-surface">Study material:</span>
            <button
              type="button"
              onClick={() => setSelectedPdfId(null)}
              className={`text-xs px-3 py-1.5 rounded-full transition-all ${
                selectedPdfId === null
                  ? 'bg-emerald-600 text-white'
                  : 'bg-white/50 text-on-surface-variant hover:bg-white/80'
              }`}
            >
              All PDFs
            </button>
            {pdfs.map((pdf) => (
              <button
                key={pdf.id}
                type="button"
                onClick={() => setSelectedPdfId(pdf.id)}
                className={`text-xs px-3 py-1.5 rounded-full transition-all ${
                  selectedPdfId === pdf.id
                    ? 'bg-emerald-600 text-white'
                    : 'bg-white/50 text-on-surface-variant hover:bg-white/80'
                }`}
              >
                {pdf.fileName?.length > 32 ? `${pdf.fileName.slice(0, 32)}...` : pdf.fileName}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="glass-pane rounded-xl p-5 md:p-6 border border-black/8">
        <div className="flex items-start gap-3 mb-5">
          <div className="w-10 h-10 rounded-xl bg-emerald-50 flex items-center justify-center flex-shrink-0">
            <Database className="w-5 h-5 text-emerald-600" />
          </div>
          <div>
            <h2 className="font-semibold text-on-surface">Choose a quick question</h2>
            <p className="text-sm text-on-surface-variant/60 mt-0.5">
              Answers come from information already saved during PDF processing.
            </p>
          </div>
        </div>

        <div className="flex gap-2 flex-wrap mb-5">
          {categories.map(([type, label]) => (
            <button
              key={type}
              type="button"
              onClick={() => {
                setCategory(type)
                setSelectedAnswer(null)
              }}
              className={`text-xs px-3 py-1.5 rounded-full transition-all ${
                category === type
                  ? 'bg-emerald-600 text-white'
                  : 'bg-white/50 text-on-surface-variant hover:bg-white/80'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="py-12 flex items-center justify-center gap-3 text-on-surface-variant/60">
            <Loader2 className="w-5 h-5 animate-spin text-emerald-600" />
            <span className="text-sm">Loading saved answers...</span>
          </div>
        ) : visibleAnswers.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
            {visibleAnswers.map((item) => (
              <motion.button
                key={item.id}
                type="button"
                whileHover={{ y: -2 }}
                onClick={() => setSelectedAnswer(item)}
                className={`text-left p-4 rounded-xl border transition-all ${
                  selectedAnswer?.id === item.id
                    ? 'border-emerald-500/50 bg-emerald-50/80'
                    : 'border-black/8 bg-white/50 hover:bg-white/80'
                }`}
              >
                <span className="flex items-start gap-2">
                  <Sparkles className="w-4 h-4 text-emerald-600 flex-shrink-0 mt-0.5" />
                  <span className="text-sm leading-6 font-medium text-on-surface">{item.question}</span>
                </span>
              </motion.button>
            ))}
          </div>
        ) : (
          <div className="py-12 text-center text-on-surface-variant/60">
            <FileText className="w-10 h-10 mx-auto mb-3 opacity-40" />
            <p className="text-sm">Quick answers appear after PDF processing finishes.</p>
          </div>
        )}
      </div>

      {selectedAnswer && (
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          className="glass-pane rounded-xl p-5 md:p-7 border border-emerald-200/60"
        >
    
          <h2 className="text-lg font-semibold text-on-surface mb-4">{selectedAnswer.question}</h2>
          <div className="pt-4 border-t border-black/8">
            <FormattedAnswer content={selectedAnswer.answer} />
          </div>
        </motion.div>
      )}
    </div>
  )
}
