import React, { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { reportAPI } from '../api'
import { Download, FileText, AlertCircle } from 'lucide-react'

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.1,
    },
  },
}

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.4 },
  },
}

export const Reports = () => {
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    fetchReport()
  }, [])

  const fetchReport = async () => {
    try {
      const response = await reportAPI.generateStudyReport()
      setReport(response.data)
      setError('')
    } catch (err) {
      setError('Failed to generate report')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  const exportAsJSON = () => {
    if (!report) return

    setExporting(true)
    const dataStr = JSON.stringify(report, null, 2)
    const dataBlob = new Blob([dataStr], { type: 'application/json' })
    const url = URL.createObjectURL(dataBlob)
    const link = document.createElement('a')
    link.href = url
    link.download = `study-report-${new Date().toISOString().split('T')[0]}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    setExporting(false)
  }

  const exportAsCSV = () => {
    if (!report) return

    setExporting(true)
    let csv = 'Study Report\n'
    csv += `Generated: ${report.generatedAt}\n`
    csv += `User: ${report.userName} (${report.userEmail})\n\n`

    csv += 'SUMMARY\n'
    csv += `Total Quizzes,${report.summary.totalQuizzes}\n`
    csv += `Correct Answers,${report.summary.correctAnswers}\n`
    csv += `Accuracy,${report.summary.accuracy}%\n`
    csv += `Total Topics,${report.summary.totalTopics}\n`
    csv += `Study Time (Minutes),${report.summary.totalStudyTimeMinutes}\n\n`

    csv += 'TOPIC BREAKDOWN\n'
    csv += 'Topic,Attempts,Correct,Accuracy,Weakness,Best Score\n'
    report.topicBreakdown.forEach(topic => {
      csv += `"${topic.topic}",${topic.attempts},${topic.correct},${topic.accuracy}%,${topic.weakness},${topic.bestScore}%\n`
    })

    csv += '\nRECOMMENDATIONS\n'
    csv += 'Topic,Current Score,Recommendation\n'
    report.recommendations.forEach(rec => {
      csv += `"${rec.topic}",${rec.currentScore}%,"${rec.recommendation}"\n`
    })

    const dataBlob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(dataBlob)
    const link = document.createElement('a')
    link.href = url
    link.download = `study-report-${new Date().toISOString().split('T')[0]}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    setExporting(false)
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="glass-pane rounded-xl p-8 border border-black/8 text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto mb-4"></div>
          <p className="text-on-surface-variant/70">Generating report...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-5xl">
      <div className="flex items-center gap-3 mb-8">
        <FileText className="w-8 h-8 text-primary" />
        <h1 className="text-4xl text-on-surface font-bold">Study Report</h1>
      </div>

      {error && (
        <div className="glass-pane rounded-xl p-4 border border-black/8 bg-red-50/80 border border-red-200/50 text-red-700 flex items-center gap-3 mb-6">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p>{error}</p>
        </div>
      )}

        {report && (
          <motion.div
            variants={containerVariants}
            initial="hidden"
            animate="visible"
          >
            <motion.div variants={itemVariants} className="mb-8 flex gap-3">
              <button
                onClick={exportAsJSON}
                disabled={exporting}
                className="btn-glass-primary flex items-center gap-2"
              >
                <Download className="w-4 h-4" />
                {exporting ? 'Exporting...' : 'Export as JSON'}
              </button>
              <button
                onClick={exportAsCSV}
                disabled={exporting}
                className="btn-glass-secondary flex items-center gap-2"
              >
                <Download className="w-4 h-4" />
                {exporting ? 'Exporting...' : 'Export as CSV'}
              </button>
            </motion.div>

            <motion.div variants={itemVariants} className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
              <div className="glass-pane rounded-xl p-6 border border-black/8">
                <p className="text-on-surface-variant/70 text-sm">Total Quizzes</p>
                <p className="text-3xl font-bold text-primary mt-2">{report.summary.totalQuizzes}</p>
              </div>
              <div className="glass-pane rounded-xl p-6 border border-black/8">
                <p className="text-on-surface-variant/70 text-sm">Correct Answers</p>
                <p className="text-3xl font-bold text-emerald-600 mt-2">{report.summary.correctAnswers}</p>
              </div>
              <div className="glass-pane rounded-xl p-6 border border-black/8">
                <p className="text-on-surface-variant/70 text-sm">Overall Accuracy</p>
                <p className="text-3xl font-bold text-blue-600 mt-2">{report.summary.accuracy}%</p>
              </div>
              <div className="glass-pane rounded-xl p-6 border border-black/8">
                <p className="text-on-surface-variant/70 text-sm">Study Time</p>
                <p className="text-3xl font-bold text-orange-600 mt-2">{report.summary.totalStudyTimeMinutes}m</p>
              </div>
            </motion.div>

            <motion.div variants={itemVariants} className="glass-pane rounded-xl p-6 border border-black/8 mb-8">
              <h2 className="text-2xl text-on-surface font-bold mb-6">Topic Breakdown</h2>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-black/8">
                      <th className="text-left py-3 px-4 text-on-surface-variant/70 font-semibold">Topic</th>
                      <th className="text-left py-3 px-4 text-on-surface-variant/70 font-semibold">Attempts</th>
                      <th className="text-left py-3 px-4 text-on-surface-variant/70 font-semibold">Correct</th>
                      <th className="text-left py-3 px-4 text-on-surface-variant/70 font-semibold">Accuracy</th>
                      <th className="text-left py-3 px-4 text-on-surface-variant/70 font-semibold">Weakness</th>
                      <th className="text-left py-3 px-4 text-on-surface-variant/70 font-semibold">Best Score</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.topicBreakdown.map((topic, idx) => (
                      <tr key={idx} className="border-b border-black/8 bg-white/40 backdrop-blur-sm">
                        <td className="py-3 px-4 text-on-surface font-medium">{topic.topic}</td>
                        <td className="py-3 px-4 text-on-surface-variant/70">{topic.attempts}</td>
                        <td className="py-3 px-4 text-on-surface-variant/70">{topic.correct}</td>
                        <td className="py-3 px-4">
                          <span className={`px-3 py-1 rounded text-sm font-medium ${
                            topic.accuracy >= 75 ? 'bg-emerald-50/80 text-emerald-700 border border-emerald-200/50' :
                            topic.accuracy >= 50 ? 'bg-yellow-50/80 text-yellow-700 border border-yellow-200/50' :
                            'bg-red-50/80 text-red-700 border border-red-200/50'
                          }`}>
                            {topic.accuracy}%
                          </span>
                        </td>
                        <td className="py-3 px-4 text-on-surface-variant/70">{topic.weakness}</td>
                        <td className="py-3 px-4 text-on-surface-variant/70">{topic.bestScore}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </motion.div>

            {report.recommendations.length > 0 && (
              <motion.div variants={itemVariants} className="glass-pane rounded-xl p-6 border border-black/8">
                <h2 className="text-2xl text-on-surface font-bold mb-6">Recommendations</h2>
                <div className="space-y-4">
                  {report.recommendations.map((rec, idx) => (
                    <div key={idx} className="bg-white/40 backdrop-blur-sm border border-black/8 rounded-xl p-4">
                      <div className="flex items-start gap-3">
                        <AlertCircle className="w-5 h-5 text-orange-500 flex-shrink-0 mt-1" />
                        <div>
                          <h3 className="font-semibold text-on-surface">{rec.topic}</h3>
                          <p className="text-sm text-on-surface-variant/70 mt-1">{rec.recommendation}</p>
                          <p className="text-xs text-on-surface-variant/70 mt-2">Current Score: {rec.currentScore}%</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </motion.div>
            )}
          </motion.div>
        )}
    </div>
  )
}
