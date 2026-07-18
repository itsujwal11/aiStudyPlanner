import React from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
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
import { AiChat } from './pages/AiChat'
import './index.css'

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

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/dashboard" element={<ProtectedRoute><PageLayout><Dashboard /></PageLayout></ProtectedRoute>} />
      <Route path="/pdf/:pdfId" element={<ProtectedRoute><PageLayout><PdfDetail /></PageLayout></ProtectedRoute>} />
      <Route path="/upload" element={<ProtectedRoute><PageLayout><UploadPdf /></PageLayout></ProtectedRoute>} />
      <Route path="/study" element={<ProtectedRoute><PageLayout><Study /></PageLayout></ProtectedRoute>} />
      <Route path="/study/:pdfId" element={<ProtectedRoute><PageLayout><Study /></PageLayout></ProtectedRoute>} />
      <Route path="/analytics" element={<ProtectedRoute><PageLayout><Analytics /></PageLayout></ProtectedRoute>} />
      <Route path="/recommendations" element={<ProtectedRoute><PageLayout><Recommendations /></PageLayout></ProtectedRoute>} />
      <Route path="/profile" element={<ProtectedRoute><PageLayout><Profile /></PageLayout></ProtectedRoute>} />
      <Route path="/reports" element={<ProtectedRoute><PageLayout><Reports /></PageLayout></ProtectedRoute>} />
      <Route path="/planner" element={<ProtectedRoute><PageLayout><Planner /></PageLayout></ProtectedRoute>} />
      <Route path="/ai-chat" element={<ProtectedRoute><PageLayout><AiChat /></PageLayout></ProtectedRoute>} />
      <Route path="/admin" element={<ProtectedRoute><PageLayout><Admin /></PageLayout></ProtectedRoute>} />
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
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
        <AppRoutes />
      </AuthProvider>
    </Router>
  )
}

export default App
