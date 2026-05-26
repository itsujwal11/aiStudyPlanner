import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { pdfAPI, topicAPI } from '../api'
import { Upload, AlertCircle, CheckCircle, Loader, RotateCcw } from 'lucide-react'
import { Navigation } from '../components/Navigation'

export const UploadPdf = () => {
  const [file, setFile] = useState(null)
  const [examDate, setExamDate] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  const [progress, setProgress] = useState({
    stage: '',
    percentage: 0
  })
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
      setSuccess('PDF analyzed successfully! Redirecting to planner...')

      // Quizzes are fetched by the Study page, so redirect there (not the Planner page)
      setTimeout(() => {
        setProgress({ stage: '', percentage: 0 })
        navigate('/study')
      }, 1500)
    } catch (err) {
      // Extract error message from response body (which now contains the actual error)
      const errorMsg = err.response?.data || err.response?.data?.message || err.message || 'Upload failed'
      setError(typeof errorMsg === 'string' ? errorMsg : JSON.stringify(errorMsg))
      setProgress({ stage: '', percentage: 0 })
    } finally {
      setLoading(false)
      setAnalyzing(false)
    }
  }

  const handleReset = async () => {
    if (!window.confirm('Start a fresh session? This will delete all current data including PDFs, topics, quizzes, and progress.')) {
      return
    }
    setLoading(true)
    setError('')
    try {
      await pdfAPI.reset()
      setSuccess('Session reset successfully! Upload a new PDF to start fresh.')
      setFile(null)
      setExamDate('')
    } catch (err) {
      setError('Failed to reset: ' + (err.response?.data?.message || err.message))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950">
      <Navigation currentPage="upload" />
      <div className="flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-md">
          <div className="card">
            <div className="flex items-center justify-between mb-4">
              <h1 className="text-3xl font-bold gradient-text">Upload Study Material</h1>
              <button
                onClick={handleReset}
                disabled={loading || analyzing}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-red-500/10 text-red-400 rounded-lg hover:bg-red-500/20 transition-all disabled:opacity-50"
                title="Start a new session"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                New Session
              </button>
            </div>

            {error && (
              <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg flex items-center gap-3">
                <AlertCircle className="w-5 h-5 text-red-500" />
                <p className="text-red-400 text-sm">{error}</p>
              </div>
            )}

            {success && (
              <div className="mb-6 p-4 bg-emerald-500/10 border border-emerald-500/20 rounded-lg flex items-center gap-3">
                <CheckCircle className="w-5 h-5 text-emerald-500" />
                <p className="text-emerald-400 text-sm">{success}</p>
              </div>
            )}

            {(loading || analyzing) && (
              <div className="mb-6 p-4 bg-cyan-500/10 border border-cyan-500/20 rounded-lg">
                <div className="flex items-center gap-3 mb-3">
                  <Loader className="w-5 h-5 text-cyan-500 animate-spin" />
                  <p className="text-cyan-400 font-semibold">{progress.stage}</p>
                </div>
                <div className="w-full bg-slate-700 rounded-full h-2 overflow-hidden">
                  <div
                    className="bg-gradient-to-r from-cyan-500 to-blue-500 h-2 rounded-full transition-all duration-300"
                    style={{ width: `${progress.percentage}%` }}
                  ></div>
                </div>
                <p className="text-xs text-slate-400 mt-2">{progress.percentage}% complete</p>
              </div>
            )}

            {!loading && !analyzing && (
              <form onSubmit={handleSubmit} className="space-y-6">
                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-3">PDF File</label>
                  <label className="flex items-center justify-center w-full px-4 py-8 border-2 border-dashed border-slate-600 rounded-lg cursor-pointer hover:border-cyan-500 transition">
                    <div className="text-center">
                      <Upload className="w-8 h-8 text-slate-400 mx-auto mb-2" />
                      <p className="text-sm font-medium text-slate-300">
                        {file ? file.name : 'Click to upload or drag and drop'}
                      </p>
                      <p className="text-xs text-slate-500 mt-1">PDF files only</p>
                    </div>
                    <input
                      type="file"
                      accept=".pdf"
                      onChange={handleFileChange}
                      className="hidden"
                      disabled={loading || analyzing}
                    />
                  </label>
                </div>

                <div>
                  <label className="block text-sm font-medium text-slate-300 mb-2">Exam Date</label>
                  <input
                    type="date"
                    value={examDate}
                    onChange={(e) => setExamDate(e.target.value)}
                    className="input-field"
                    disabled={loading || analyzing}
                    required
                  />
                </div>

                <button
                  type="submit"
                  disabled={loading || analyzing || !file || !examDate}
                  className="btn-primary w-full disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                >
                  <Upload className="w-4 h-4" />
                  Upload & Analyze
                </button>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}