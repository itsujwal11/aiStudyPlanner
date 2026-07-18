import React, { useState, useRef, useEffect } from 'react'
import { useAuth } from '../context/AuthContext'
import { ragAPI, pdfAPI } from '../api'
import { motion, AnimatePresence } from 'framer-motion'
import { Send, Bot, User, BookOpen, Loader2, AlertCircle, FileText, RefreshCw } from 'lucide-react'

export const AiChat = () => {
  const [messages, setMessages] = useState([
    { role: 'assistant', content: 'Hi! I\'m your AI study assistant. Ask me anything about your uploaded study materials, and I\'ll find the relevant information to help you understand better.' }
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [pdfs, setPdfs] = useState([])
  const [selectedPdfId, setSelectedPdfId] = useState(null)
  const [error, setError] = useState('')
  const [reprocessingPdfId, setReprocessingPdfId] = useState(null)
  const messagesEndRef = useRef(null)
  const { user } = useAuth()

  useEffect(() => {
    fetchPdfs()
  }, [])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const fetchPdfs = async () => {
    try {
      const res = await pdfAPI.list()
      setPdfs(res.data || [])
    } catch (err) {
      console.error('Failed to fetch PDFs:', err)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!input.trim() || loading) return

    const question = input.trim()
    setInput('')
    setError('')

    setMessages(prev => [...prev, { role: 'user', content: question }])
    setLoading(true)

    try {
      const res = await ragAPI.askQuestion({
        question,
        pdfId: selectedPdfId || undefined
      })

      const answer = res.data.answer || 'No answer generated.'
      const sources = res.data.sources || []

      let responseText = answer
      if (sources.length > 0) {
        responseText += '\n\n**Sources:**'
        sources.forEach((s, i) => {
          const pdfName = s.pdfFileName || `PDF #${s.pdfId}`
          responseText += `\n${i + 1}. ${pdfName} (Page ~${s.pageNumber || 'N/A'}, Relevance: ${(s.similarity * 100).toFixed(1)}%)`
        })
      }

      setMessages(prev => [...prev, { role: 'assistant', content: responseText, sources }])
    } catch (err) {
      const errorMsg = err.response?.data?.error || 'Failed to get an answer. Please try again.'
      setError(errorMsg)
      setMessages(prev => [...prev, { role: 'assistant', content: `Sorry, I encountered an error: ${errorMsg}` }])
    } finally {
      setLoading(false)
    }
  }

  const handleReprocess = async (pdfId) => {
    if (reprocessingPdfId !== null) return
    setReprocessingPdfId(pdfId)
    setError('')
    try {
      await ragAPI.reprocessPdf(pdfId)
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: '✅ PDF has been reprocessed for RAG. You can now ask questions about it!'
      }])
    } catch (err) {
      setError('Failed to reprocess PDF: ' + (err.response?.data?.error || err.message))
    } finally {
      setReprocessingPdfId(null)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl md:text-[40px] font-semibold text-on-surface leading-[48px]">
          AI Study Assistant
        </h1>
        <p className="text-lg text-on-surface-variant/70 mt-1">
          Ask questions about your study materials — powered by RAG
        </p>
      </div>

      {error && (
        <div className="glass-pane rounded-xl p-4 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}

      {/* PDF Selector */}
      {pdfs.length > 0 && (
        <div className="glass-pane rounded-xl p-4 border border-black/8">
          <div className="flex items-center gap-3 flex-wrap">
            <BookOpen className="w-4 h-4 text-primary" />
            <span className="text-sm font-medium text-on-surface">Filter by PDF:</span>
            <button
              onClick={() => setSelectedPdfId(null)}
              className={`text-xs px-3 py-1.5 rounded-full transition-all ${
                selectedPdfId === null
                  ? 'bg-primary text-white'
                  : 'bg-white/40 text-on-surface-variant hover:bg-white/60'
              }`}
            >
              All PDFs
            </button>
            {pdfs.map(pdf => (
              <div key={pdf.id} className="flex items-center gap-1">
                <button
                  onClick={() => setSelectedPdfId(pdf.id)}
                  className={`text-xs px-3 py-1.5 rounded-full transition-all ${
                    selectedPdfId === pdf.id
                      ? 'bg-primary text-white'
                      : 'bg-white/40 text-on-surface-variant hover:bg-white/60'
                  }`}
                >
                  {pdf.fileName?.length > 20 ? pdf.fileName.substring(0, 20) + '...' : pdf.fileName}
                </button>
                <button
                  onClick={() => handleReprocess(pdf.id)}
                  disabled={reprocessingPdfId !== null}
                  className="p-1 rounded-full hover:bg-white/40 text-on-surface-variant/50 hover:text-primary transition-all disabled:opacity-40"
                  title="Reprocess for RAG"
                >
                  <RefreshCw className={`w-3 h-3 ${reprocessingPdfId === pdf.id ? 'animate-spin' : ''}`} />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Chat Messages */}
      <div className="glass-pane rounded-xl border border-black/8 flex flex-col h-[60vh]">
        <div className="flex-1 overflow-y-auto p-4 md:p-6 space-y-4">
          <AnimatePresence>
            {messages.map((msg, i) => (
              <motion.div
                key={i}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.05 }}
                className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                {msg.role === 'assistant' && (
                  <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0 mt-1">
                    <Bot className="w-4 h-4 text-primary" />
                  </div>
                )}
                <div className={`max-w-[80%] md:max-w-[70%] ${
                  msg.role === 'user'
                    ? 'bg-primary text-white rounded-2xl rounded-tr-md px-4 py-3'
                    : 'bg-white/40 backdrop-blur-sm border border-black/8 rounded-2xl rounded-tl-md px-4 py-3'
                }`}>
                  <p className="text-sm whitespace-pre-wrap">{msg.content}</p>
                  {msg.sources && msg.sources.length > 0 && (
                    <div className="mt-3 pt-3 border-t border-black/8">
                      <p className="text-xs font-medium text-on-surface-variant/60 mb-2">Sources:</p>
                      {msg.sources.map((s, j) => (
                        <div key={j} className="flex items-center gap-2 text-xs text-on-surface-variant/70 mb-1">
                          <FileText className="w-3 h-3 flex-shrink-0" />
                          <span className="truncate">{s.pdfFileName || `PDF #${s.pdfId}`}</span>
                          <span className="text-on-surface-variant/40">·</span>
                          <span>Page {s.pageNumber || 'N/A'}</span>
                          <span className="text-on-surface-variant/40">·</span>
                          <span className="text-emerald-600">{(s.similarity * 100).toFixed(0)}% match</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
                {msg.role === 'user' && (
                  <div className="w-8 h-8 rounded-xl bg-secondary/10 flex items-center justify-center flex-shrink-0 mt-1">
                    <User className="w-4 h-4 text-secondary" />
                  </div>
                )}
              </motion.div>
            ))}
          </AnimatePresence>
          {loading && (
            <div className="flex gap-3">
              <div className="w-8 h-8 rounded-xl bg-primary/10 flex items-center justify-center">
                <Bot className="w-4 h-4 text-primary" />
              </div>
              <div className="bg-white/40 backdrop-blur-sm border border-black/8 rounded-2xl rounded-tl-md px-4 py-3">
                <Loader2 className="w-5 h-5 animate-spin text-primary" />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input */}
        <form onSubmit={handleSubmit} className="p-4 border-t border-black/8">
          <div className="flex gap-3">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              maxLength={2000}
              placeholder="Ask a question about your study materials..."
              disabled={loading}
              className="flex-1 px-4 py-3 rounded-xl bg-white/40 backdrop-blur-sm border border-black/8 focus:outline-none focus:ring-2 focus:ring-primary/30 text-sm text-on-surface placeholder:text-on-surface-variant/40"
            />
            <button
              type="submit"
              disabled={loading || !input.trim()}
              className="btn-glass-primary px-5 py-3 rounded-xl disabled:opacity-40"
            >
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
