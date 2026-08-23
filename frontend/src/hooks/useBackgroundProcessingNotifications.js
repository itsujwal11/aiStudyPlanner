import { useEffect, useRef } from 'react'
import toast from 'react-hot-toast'
import { pdfAPI } from '../api'
import { useAuth } from '../context/AuthContext'

/**
 * Background-processing notifications.
 *
 * PDF analysis runs server-side (@Async). This watcher polls the lightweight
 * /pdfs list (which carries processingStatus/topicCount per PDF), diffs each
 * poll against the previous one, and notifies the user the moment a document
 * finishes analyzing — even if they navigated away from the upload page:
 *
 *   PROCESSING ──► COMPLETED  →  success toast + optional desktop notification
 *   PROCESSING ──► FAILED     →  error toast (+ failure reason)
 *
 * Design notes:
 * - Polling pauses while the tab is hidden and resumes instantly on focus.
 * - The first fetch only seeds state, so pre-existing PDFs never fire toasts.
 * - Desktop notifications (Notification API) fire only when the tab is hidden,
 *   complementing in-app toasts rather than duplicating them.
 */

const POLL_INTERVAL_MS = 5000

export const useBackgroundProcessingNotifications = () => {
  const { token } = useAuth()
  const statusMapRef = useRef(new Map()) // pdfId -> { status }
  const seededRef = useRef(false)

  useEffect(() => {
    if (!token) return undefined

    let intervalId = null

    const notifyDesktop = (title, body, tag) => {
      if (typeof window === 'undefined' || !('Notification' in window)) return
      if (Notification.permission !== 'granted' || !document.hidden) return
      try {
        new Notification(title, { body, tag })
      } catch {
        // Some browsers restrict constructors; never break the app over this.
      }
    }

    const poll = async () => {
      if (document.hidden) return
      try {
        const res = await pdfAPI.list()
        const pdfs = Array.isArray(res.data) ? res.data : []
        const previous = statusMapRef.current
        const next = new Map()

        for (const pdf of pdfs) {
          next.set(pdf.id, { status: pdf.processingStatus })
          const before = seededRef.current ? previous.get(pdf.id) : null
          const wasActive =
            before && (before.status === 'PROCESSING' || before.status === 'PENDING')

          if (wasActive && pdf.processingStatus === 'COMPLETED') {
            const count = pdf.topicCount ?? 0
            toast.success(
              `"${pdf.fileName}" is ready — ${count} topic${count === 1 ? '' : 's'} generated`,
              { duration: 6000 },
            )
            notifyDesktop(
              'Study material ready',
              `"${pdf.fileName}" finished analyzing — ${count} topics are available.`,
              `pdf-${pdf.id}`,
            )
          }

          if (wasActive && pdf.processingStatus === 'FAILED') {
            const reason = pdf.processingError || 'unknown error'
            toast.error(`Analysis failed for "${pdf.fileName}": ${reason}`, { duration: 8000 })
            notifyDesktop(
              'Analysis failed',
              `"${pdf.fileName}" could not be analyzed: ${reason}`,
              `pdf-${pdf.id}`,
            )
          }
        }

        statusMapRef.current = next
        seededRef.current = true
      } catch {
        // Transient network errors are ignored; 401 is handled by the axios interceptor.
      }
    }

    const onVisibilityChange = () => {
      if (!document.hidden) poll() // refresh immediately when the user returns
    }

    document.addEventListener('visibilitychange', onVisibilityChange)
    poll()
    intervalId = setInterval(poll, POLL_INTERVAL_MS)

    return () => {
      clearInterval(intervalId)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [token])
}

/** Renders nothing; mount once globally so notifications work on every page. */
export const BackgroundProcessingWatcher = () => {
  useBackgroundProcessingNotifications()
  return null
}
