import React, { useState, useEffect } from 'react'
import { Navigation } from '../components/Navigation'
import { reportAPI } from '../api'
import { Download, FileText, AlertCircle, CheckCircle } from 'lucide-react'

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
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-cyan-500 mx-auto mb-4"></div>
          <p className="text-slate-400">Generating report...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      <Navigation currentPage="reports" />

      <main className="max-w-6xl mx-auto px-6 py-8">
        <div className="flex items-center gap-3 mb-8">
          <FileText className="w-8 h-8 text-cyan-400" />
          <h1 className="text-4xl font-bold gradient-text">Study Report</h1>
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-red-500" />
            <p className="text-red-400">{error}</p>
          </div>
        )}

        {report && (
          <>
            <div className="mb-8 flex gap-3">
              <button
                onClick={exportAsJSON}
                disabled={exporting}
                className="flex items-center gap-2 px-6 py-3 bg-cyan-500/20 text-cyan-400 rounded-lg hover:bg-cyan-500/30 transition disabled:opacity-50 font-semibold"
              >
                <Download className="w-4 h-4" />
                {exporting ? 'Exporting...' : 'Export as JSON'}
              </button>
              <button
                onClick={exportAsCSV}
                disabled={exporting}
                className="flex items-center gap-2 px-6 py-3 bg-blue-500/20 text-blue-400 rounded-lg hover:bg-blue-500/30 transition disabled:opacity-50 font-semibold"
              >
                <Download className="w-4 h-4" />
                {exporting ? 'Exporting...' : 'Export as CSV'}
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
              <div className="card">
                <p className="text-slate-400 text-sm">Total Quizzes</p>
                <p className="text-3xl font-bold text-cyan-400 mt-2">{report.summary.totalQuizzes}</p>
              </div>
              <div className="card">
                <p className="text-slate-400 text-sm">Correct Answers</p>
                <p className="text-3xl font-bold text-emerald-400 mt-2">{report.summary.correctAnswers}</p>
              </div>
              <div className="card">
                <p className="text-slate-400 text-sm">Overall Accuracy</p>
                <p className="text-3xl font-bold text-blue-400 mt-2">{report.summary.accuracy}%</p>
              </div>
              <div className="card">
                <p className="text-slate-400 text-sm">Study Time</p>
                <p className="text-3xl font-bold text-orange-400 mt-2">{report.summary.totalStudyTimeMinutes}m</p>
              </div>
            </div>

            <div className="card mb-8">
              <h2 className="text-2xl font-bold mb-6 text-slate-200">Topic Breakdown</h2>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-slate-700">
                      <th className="text-left py-3 px-4 text-slate-300">Topic</th>
                      <th className="text-left py-3 px-4 text-slate-300">Attempts</th>
                      <th className="text-left py-3 px-4 text-slate-300">Correct</th>
                      <th className="text-left py-3 px-4 text-slate-300">Accuracy</th>
                      <th className="text-left py-3 px-4 text-slate-300">Weakness</th>
                      <th className="text-left py-3 px-4 text-slate-300">Best Score</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.topicBreakdown.map((topic, idx) => (
                      <tr key={idx} className="border-b border-slate-700/50 hover:bg-slate-800/30">
                        <td className="py-3 px-4 text-slate-200">{topic.topic}</td>
                        <td className="py-3 px-4 text-slate-400">{topic.attempts}</td>
                        <td className="py-3 px-4 text-slate-400">{topic.correct}</td>
                        <td className="py-3 px-4">
                          <span className={`px-3 py-1 rounded text-sm ${
                            topic.accuracy >= 75 ? 'bg-emerald-500/20 text-emerald-400' :
                            topic.accuracy >= 50 ? 'bg-yellow-500/20 text-yellow-400' :
                            'bg-red-500/20 text-red-400'
                          }`}>
                            {topic.accuracy}%
                          </span>
                        </td>
                        <td className="py-3 px-4 text-slate-400">{topic.weakness}</td>
                        <td className="py-3 px-4 text-slate-400">{topic.bestScore}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {report.recommendations.length > 0 && (
              <div className="card">
                <h2 className="text-2xl font-bold mb-6 text-slate-200">Recommendations</h2>
                <div className="space-y-4">
                  {report.recommendations.map((rec, idx) => (
                    <div key={idx} className="p-4 bg-slate-800/50 rounded-lg border border-slate-700/50">
                      <div className="flex items-start gap-3">
                        <AlertCircle className="w-5 h-5 text-orange-400 flex-shrink-0 mt-1" />
                        <div>
                          <h3 className="font-semibold text-slate-200">{rec.topic}</h3>
                          <p className="text-sm text-slate-400 mt-1">{rec.recommendation}</p>
                          <p className="text-xs text-slate-500 mt-2">Current Score: {rec.currentScore}%</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  )
}
