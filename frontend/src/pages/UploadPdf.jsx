import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { pdfAPI, topicAPI } from '../api'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'
import { Upload, AlertCircle, CheckCircle, Loader } from 'lucide-react'

export const UploadPdf = () => {
  const [file, setFile] = useState(null)
  const [examDate, setExamDate] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  const [progress, setProgress] = useState({ stage: '', percentage: 0 })
  const navigate = useNavigate()

  const handleFileChange = (e) => {
    const selectedFile = e.target.files[0]
    if (selectedFile && selectedFile.type === 'application/pdf') {
      setFile(selectedFile)
      setError('')
    } else {
      setError('Please select a valid PDF file')
      setFile(null)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!file || !examDate) {
      setError('Please select a file and exam date')
      return
    }

    setLoading(true)
    setError('')
    setProgress({ stage: 'Uploading PDF...', percentage: 10 })

    try {
      const response = await pdfAPI.upload(file, examDate)
      setProgress({ stage: 'PDF uploaded successfully', percentage: 30 })
      setFile(null)
      setExamDate('')

      setAnalyzing(true)
      setProgress({ stage: 'Analyzing content with AI...', percentage: 50 })
      await topicAPI.analyze(response.data.id)

      setProgress({ stage: 'Extracting topics...', percentage: 70 })
      await new Promise(resolve => setTimeout(resolve, 500))

      setProgress({ stage: 'Generating quizzes...', percentage: 85 })
      await new Promise(resolve => setTimeout(resolve, 500))

      setProgress({ stage: 'Finalizing study plan...', percentage: 95 })
      setSuccess('PDF analyzed successfully!')

      toast.success('PDF uploaded and analyzed!')

      setTimeout(() => {
        setProgress({ stage: '', percentage: 0 })
        navigate('/study')
      }, 1500)
    } catch (err) {
      const errorMsg = err.response?.data || err.response?.data?.message || err.message || 'Upload failed'
      setError(typeof errorMsg === 'string' ? errorMsg : JSON.stringify(errorMsg))
      toast.error('Upload failed')
      setProgress({ stage: '', percentage: 0 })
    } finally {
      setLoading(false)
      setAnalyzing(false)
    }
  }

  return (
    <div className="flex items-center justify-center px-4 py-12">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="w-full max-w-md"
      >
        <div className="glass-pane rounded-xl p-8 border border-black/8">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-on-surface">Upload Study Material</h1>
            <p className="text-sm text-on-surface-variant/70 mt-1">Upload a PDF to start learning</p>
          </div>

          {error && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} className="glass-pane rounded-xl p-4 border border-black/8 bg-red-50/80 border border-red-200/50 text-red-700 mb-6 flex items-center gap-3">
              <AlertCircle className="w-5 h-5 flex-shrink-0" />
              <p className="text-sm">{error}</p>
            </motion.div>
          )}

          {success && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} className="glass-pane rounded-xl p-4 border border-black/8 bg-emerald-50/80 border border-emerald-200/50 text-emerald-700 mb-6 flex items-center gap-3">
              <CheckCircle className="w-5 h-5 flex-shrink-0" />
              <p className="text-sm">{success}</p>
            </motion.div>
          )}

          {(loading || analyzing) && (
            <div className="glass-pane-sm rounded-xl p-4 mb-6">
              <div className="flex items-center gap-3 mb-3">
                <Loader className="w-5 h-5 text-on-surface animate-spin" />
                <p className="text-on-surface font-semibold text-sm">{progress.stage}</p>
              </div>
              <div className="w-full bg-white/40 rounded-full h-2 overflow-hidden">
                <motion.div
                  className="bg-gradient-to-r from-primary to-primary-container h-2 rounded-full"
                  initial={{ width: 0 }}
                  animate={{ width: `${progress.percentage}%` }}
                  transition={{ duration: 0.5 }}
                />
              </div>
              <p className="text-xs text-on-surface-variant/70 mt-2">{progress.percentage}% complete</p>
            </div>
          )}

          {!loading && !analyzing && (
            <form onSubmit={handleSubmit} className="space-y-6">
              <div>
                <label className="block text-sm font-medium text-on-surface-variant/70 mb-2">PDF File</label>
                <label className="flex items-center justify-center w-full px-4 py-8 bg-white/40 backdrop-blur-sm border-2 border-dashed border-white/40 rounded-xl cursor-pointer hover:border-primary/40 transition-all">
                  <div className="text-center">
                    <Upload className="w-8 h-8 text-on-surface-variant/70 mx-auto mb-2" />
                    <p className="text-sm font-medium text-on-surface">
                      {file ? file.name : 'Click to upload or drag and drop'}
                    </p>
                    <p className="text-xs text-on-surface-variant/70 mt-1">PDF files only</p>
                  </div>
                  <input type="file" accept=".pdf" onChange={handleFileChange} className="hidden" disabled={loading || analyzing} />
                </label>
              </div>

              <div>
                <label className="block text-sm font-medium text-on-surface-variant/70 mb-2">Exam Date</label>
                <input type="date" value={examDate} onChange={(e) => setExamDate(e.target.value)} className="input-glass px-4" disabled={loading || analyzing} required />
              </div>

              <button type="submit" disabled={loading || analyzing || !file || !examDate} className="btn-glass-primary w-full disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2">
                <Upload className="w-4 h-4" />
                Upload & Analyze
              </button>
            </form>
          )}
        </div>
      </motion.div>
    </div>
  )
}
