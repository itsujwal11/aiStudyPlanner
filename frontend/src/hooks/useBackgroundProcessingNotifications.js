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
 * - **Polling STOPS when no PDFs are PENDING/PROCESSING** to avoid wasted requests.
 * - Polling RESTARTS on visibility change (user returns to tab) or new upload.
 */

const POLL_INTERVAL_MS = 10000 // 10 seconds - less aggressive, server-friendly

export const useBackgroundProcessingNotifications = () => {
  const { token } = useAuth()
  const statusMapRef = useRef(new Map()) // pdfId -> { status }
  const seededRef = useRef(false)
  const intervalIdRef = useRef(null)

  useEffect(() => {
    if (!token) return undefined

    const notifyDesktop = (title, body, tag) => {
      if (typeof window === 'undefined' || !('Notification' in window)) return
      if (Notification.permission !== 'granted' || !document.hidden) return
      try {
        new Notification(title, { body, tag })
      } catch {
        // Some browsers restrict constructors; never break the app over this.
      }
    }

    const checkAndSchedulePoll = () => {
      const hasActive = Array.from(statusMapRef.current.values()).some(
        s => s.status === 'PROCESSING' || s.status === 'PENDING'
      )

      // Clear existing interval if no active processing and we've seeded
      if (!hasActive && seededRef.current && intervalIdRef.current) {
        clearInterval(intervalIdRef.current)
        intervalIdRef.current = null
        return
      }

      // Start interval if there's active processing OR we haven't seeded yet
      if (!intervalIdRef.current && (hasActive || !seededRef.current)) {
        intervalIdRef.current = setInterval(poll, POLL_INTERVAL_MS)
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

        // Check if we should continue polling
        checkAndSchedulePoll()
      } catch {
        // Transient network errors are ignored; 401 is handled by the axios interceptor.
      }
    }

    const onVisibilityChange = () => {
      if (!document.hidden) {
        poll() // refresh immediately when the user returns
        checkAndSchedulePoll() // re-evaluate if we need to restart polling
      }
    }

    // Listen for new uploads to restart polling
    const onPdfUpload = () => {
      seededRef.current = false // reset seed so we re-check all PDFs
      poll()
      checkAndSchedulePoll()
    }

    document.addEventListener('visibilitychange', onVisibilityChange)
    window.addEventListener('pdf-upload-complete', onPdfUpload)
    poll() // initial poll
    checkAndSchedulePoll() // schedule based on initial state

    return () => {
      if (intervalIdRef.current) clearInterval(intervalIdRef.current)
      document.removeEventListener('visibilitychange', onVisibilityChange)
      window.removeEventListener('pdf-upload-complete', onPdfUpload)
    }
  }, [token])
}

/** Renders nothing; mount once globally so notifications work on every page. */
export const BackgroundProcessingWatcher = () => {
  useBackgroundProcessingNotifications()
  return null
}

// Export a function to manually trigger a poll (e.g., after new upload)
export const triggerBackgroundPoll = () => {
  // This will be called by UploadPdf after successful upload
  window.dispatchEvent(new CustomEvent('pdf-upload-complete'))
}
