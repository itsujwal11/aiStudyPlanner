import React, { useEffect, useState } from 'react'
import { ragAPI, pdfAPI } from '../api'
import { motion } from 'framer-motion'
import { AlertCircle, BookOpen, FileText, Loader2, Send } from 'lucide-react'

const formatInline = (text) => (
  text.split(/(\*\*[^*]+\*\*)/g).map((part, index) =>
    part.startsWith('**') && part.endsWith('**')
      ? <strong key={index} className="font-semibold">{part.slice(2, -2)}</strong>
      : <React.Fragment key={index}>{part}</React.Fragment>
  )
)

/**
 * AI Chat — the demonstrable RAG pipeline UI:
 * question → pgvector retrieval → hybrid reranking → grounded Gemini answer + sources.
 * Every source shows its page number and cosine relevance so answers are verifiable.
 */
export const AiChat = () => {
  const [pdfs, setPdfs] = useState([])
  const [selectedPdfId, setSelectedPdfId] = useState(null)
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    pdfAPI.list()
      .then((res) => setPdfs(res.data || []))
      .catch(() => setError('Failed to load your PDFs'))
  }, [])

  const handleAsk = async () => {
    if (!question.trim() || loading) return
    setLoading(true)
    setError('')
    setResult(null)
    try {
      const res = await ragAPI.ask(question.trim(), selectedPdfId ?? undefined)
      setResult(res.data)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to get an answer')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl md:text-[40px] font-semibold text-on-surface leading-[48px]">AI Chat</h1>
        <p className="text-lg text-on-surface-variant/70 mt-1">
          Ask anything about your uploaded PDF. Answers are generated only from retrieved
          chunks of your own study material — every claim is cited.
        </p>
      </div>

      {error && (
        <div className="glass-pane rounded-xl p-4 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}

      <div className="glass-pane rounded-xl p-5 md:p-6 border border-black/8">
        <label className="text-sm font-medium text-on-surface-variant mb-2 block">
          Choose a PDF (optional — searches all your PDFs when none is selected)
        </label>
        <div className="flex gap-2 flex-wrap mb-4">
          {pdfs.map((pdf) => (
            <button key={pdf.id} type="button"
              onClick={() => setSelectedPdfId(selectedPdfId === pdf.id ? null : pdf.id)}
              className={`text-xs px-3 py-1.5 rounded-full transition-all border ${
                selectedPdfId === pdf.id
                  ? 'bg-primary text-white border-primary'
                  : 'bg-white/50 text-on-surface-variant border-black/8 hover:bg-white/80'
              }`}>
              <FileText className="w-3.5 h-3.5 inline mr-1" />
              {pdf.fileName.length > 32 ? pdf.fileName.slice(0, 32) + '...' : pdf.fileName}
            </button>
          ))}
          {pdfs.length === 0 && (
            <span className="text-sm text-on-surface-variant/60">Upload a PDF first.</span>
          )}
        </div>

        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          rows={3}
          placeholder="e.g. Explain the OSI model layers and what each one does"
          onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) handleAsk() }}
          className="w-full rounded-xl border border-black/10 bg-white/60 p-4 text-sm leading-6 text-on-surface placeholder:text-on-surface-variant/40 focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
        <div className="mt-3 flex justify-end">
          <button onClick={handleAsk} disabled={loading || !question.trim()}
            className="btn-glass-primary disabled:opacity-50 disabled:cursor-not-allowed">
            {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Send className="w-5 h-5" />}
            {loading ? 'Retrieving & generating...' : 'Ask'}
          </button>
        </div>
      </div>

      {result?.answer && (
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
          className="glass-pane rounded-xl p-5 md:p-7 border border-emerald-200/60">
          <div className="flex items-center gap-2 mb-4 text-emerald-700 font-semibold">
            <BookOpen className="w-5 h-5" /> Answer
          </div>
          <div className="pt-4 border-t border-black/8 text-sm md:text-base leading-7 text-on-surface space-y-2">
            {result.answer.split(/\r?\n/).map((line, i) => <p key={i}>{formatInline(line)}</p>)}
          </div>
        </motion.div>
      )}

      {result?.sources?.length > 0 && (
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
          className="glass-pane rounded-xl p-5 md:p-6 border border-black/8">
          <h3 className="text-sm font-semibold text-on-surface mb-3">Sources</h3>
          <ul className="space-y-1.5 text-sm text-on-surface-variant/80">
            {result.sources.map((s, i) => (
              <li key={i} className="flex items-start gap-2">
                <span className="text-emerald-600">•</span>
                <span>
                  <span className="font-medium text-on-surface">{s.pdfFileName || `PDF #${s.pdfId}`}</span>
                  {s.pageNumber != null && <> — page {s.pageNumber}</>}
                  {s.similarity != null && (
                    <span className="text-emerald-700"> — relevance {Number(s.similarity).toFixed(2)}</span>
                  )}
                  {s.rerankScore != null && (
                    <span className="text-on-surface-variant/60"> · rerank {Number(s.rerankScore).toFixed(2)}</span>
                  )}
                  {s.rank != null && (
                    <span className="text-on-surface-variant/60"> · rank #{s.rank}</span>
                  )}
                </span>
              </li>
            ))}
          </ul>
          <p className="text-xs text-on-surface-variant/50 mt-3 italic">
            rank = position after hybrid reranking; relevance = cosine similarity between your
            question and the chunk (pgvector).
          </p>
        </motion.div>
      )}

      {!result && !loading && !error && (
        <div className="text-center py-10 text-on-surface-variant/50">
          <BookOpen className="w-10 h-10 mx-auto mb-2 opacity-40" />
          <p className="text-sm">Ask any question about your study material.</p>
        </div>
      )}
    </div>
  )
}
