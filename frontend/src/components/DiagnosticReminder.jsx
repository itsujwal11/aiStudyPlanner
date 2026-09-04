import React, { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight, ClipboardCheck } from 'lucide-react'
import { dashboardAPI, pdfAPI, quizAPI } from '../api'
import { useAuth } from '../context/AuthContext'

export const DiagnosticReminder = () => {
  const { isAdmin } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [reminder, setReminder] = useState(null)

  useEffect(() => {
    if (isAdmin || location.pathname.startsWith('/diagnostic/')) {
      setReminder(null)
      return
    }

    let cancelled = false
    const load = async () => {
      try {
        const pdfResponse = await pdfAPI.list()
        const readyPdf = (pdfResponse.data || []).find(
          (pdf) => (pdf.isAnalyzed || pdf.processingStatus === 'COMPLETED') && pdf.topicCount > 0
        )
        if (!readyPdf) return

        const [dashboardResponse, progressResponse] = await Promise.all([
          dashboardAPI.getByPdf(readyPdf.id), quizAPI.getProgress(readyPdf.id),
        ])
        const dashboard = dashboardResponse.data
        const attempts = progressResponse.data?.attemptedCount || 0
        const target = Math.min(15, dashboard.totalQuizzes || 0)
        if (!cancelled && target > 0 && attempts < target) {
          setReminder({ pdfId: readyPdf.id, attempts, target })
        }
      } catch (error) {
        console.warn('Could not load diagnostic reminder', error)
      }
    }
    load()
    return () => { cancelled = true }
  }, [isAdmin, location.pathname])

  if (!reminder) return null

  return (
    <div className="mb-6 glass-pane rounded-xl p-4 border border-amber-200/70 bg-amber-50/70 flex flex-col sm:flex-row sm:items-center gap-4">
      <ClipboardCheck className="w-5 h-5 text-amber-700 flex-shrink-0" />
      <div className="flex-1">
        <p className="text-sm font-semibold text-amber-900">Finish your diagnostic quiz</p>
        <p className="text-sm text-amber-800/75">{reminder.attempts} of {reminder.target} answers saved. Your progress will continue from where you stopped.</p>
      </div>
      <button className="btn-glass-primary whitespace-nowrap" onClick={() => navigate(`/diagnostic/${reminder.pdfId}`)}>
        Continue <ArrowRight className="w-4 h-4" />
      </button>
    </div>
  )
}
