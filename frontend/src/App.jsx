import React from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Toaster } from 'react-hot-toast'
import { AuthProvider } from './context/AuthContext'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Sidebar } from './components/Sidebar'
import { Login } from './pages/Login'
import { Register } from './pages/Register'
import { Dashboard } from './pages/Dashboard'
import { PdfDetail } from './pages/PdfDetail'
import { UploadPdf } from './pages/UploadPdf'
import { Study } from './pages/Study'
import { Analytics } from './pages/Analytics'
import { Recommendations } from './pages/Recommendations'
import { Profile } from './pages/Profile'
import { Reports } from './pages/Reports'
import { Planner } from './pages/Planner'
import { Admin } from './pages/Admin'
import { FlashcardReview } from './pages/FlashcardReview'
import './index.css'

const pageVariants = {
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.3, ease: 'easeOut' } },
  exit: { opacity: 0, y: -8, transition: { duration: 0.2, ease: 'easeIn' } },
}

function PageLayout({ children }) {
  return (
    <>
      <div className="mesh-bg" />
      <Sidebar />
      <main className="md:ml-sidebar min-h-screen">
        <div className="max-w-container mx-auto px-4 md:px-gutter py-8 pt-16 md:pt-8">
          {children}
        </div>
      </main>
    </>
  )
}

function AnimatedRoutes() {
  const location = useLocation()
  const isAuthPage = ['/login', '/register'].includes(location.pathname)

  if (isAuthPage) {
    return (
      <Routes location={location}>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
      </Routes>
    )
  }

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={location.pathname}
        variants={pageVariants}
        initial="initial"
        animate="animate"
        exit="exit"
      >
        <Routes location={location}>
          <Route path="/dashboard" element={<ProtectedRoute><PageLayout><Dashboard /></PageLayout></ProtectedRoute>} />
          <Route path="/pdf/:pdfId" element={<ProtectedRoute><PageLayout><PdfDetail /></PageLayout></ProtectedRoute>} />
          <Route path="/upload" element={<ProtectedRoute><PageLayout><UploadPdf /></PageLayout></ProtectedRoute>} />
          <Route path="/study" element={<ProtectedRoute><PageLayout><Study /></PageLayout></ProtectedRoute>} />
          <Route path="/study/:pdfId" element={<ProtectedRoute><PageLayout><Study /></PageLayout></ProtectedRoute>} />
          <Route path="/analytics" element={<ProtectedRoute><PageLayout><Analytics /></PageLayout></ProtectedRoute>} />
          <Route path="/recommendations" element={<ProtectedRoute><PageLayout><Recommendations /></PageLayout></ProtectedRoute>} />
          <Route path="/profile" element={<ProtectedRoute><PageLayout><Profile /></PageLayout></ProtectedRoute>} />
          <Route path="/reports" element={<ProtectedRoute><PageLayout><Reports /></PageLayout></ProtectedRoute>} />
          <Route path="/flashcards" element={<ProtectedRoute><PageLayout><FlashcardReview /></PageLayout></ProtectedRoute>} />
          <Route path="/planner" element={<ProtectedRoute><PageLayout><Planner /></PageLayout></ProtectedRoute>} />
          <Route path="/admin" element={<ProtectedRoute><PageLayout><Admin /></PageLayout></ProtectedRoute>} />
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </motion.div>
    </AnimatePresence>
  )
}

function App() {
  return (
    <Router>
      <AuthProvider>
        <Toaster position="top-right" toastOptions={{
          duration: 3000,
          style: {
            background: 'rgba(255,255,255,0.9)',
            backdropFilter: 'blur(24px)',
            color: '#191c1e',
            border: '1px solid rgba(255,255,255,0.2)',
            boxShadow: '0 8px 32px rgba(0,0,0,0.08)',
            borderRadius: '12px',
            fontSize: '14px',
            fontFamily: 'Hanken Grotesk, system-ui, sans-serif',
          },
          success: { iconTheme: { primary: '#0058bc', secondary: '#fff' } },
          error: { iconTheme: { primary: '#ba1a1a', secondary: '#fff' } },
        }} />
        <AnimatedRoutes />
      </AuthProvider>
    </Router>
  )
}

export default App
