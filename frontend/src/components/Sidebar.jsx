import React, { useMemo, useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { motion, AnimatePresence } from 'framer-motion'
import {
  LogOut, LayoutDashboard, Database, Upload, BookOpen, LineChart,
  FileText, User, Menu, X, HelpCircle, Target
} from 'lucide-react'

const userNav = [
  { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/upload', label: 'Upload', icon: Upload },
  { path: '/study', label: 'Study', icon: BookOpen },
  { path: '/quick-answers', label: 'Quick Answers', icon: HelpCircle },
  { path: '/practice', label: 'Practice', icon: Target },
  { path: '/analytics', label: 'Analytics', icon: LineChart },
  // { path: '/flashcards', label: 'Flashcards', icon: Brain },
  { path: '/planner', label: 'Planner', icon: FileText },
  { path: '/profile', label: 'Profile', icon: User },
]

const adminNav = [
  { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/admin', label: 'Database', icon: Database },
]

export const Sidebar = () => {
  const navigate = useLocation()
  const navigateFn = useNavigate()
  const { user, logout, isAdmin } = useAuth()
  const navItems = useMemo(() => isAdmin ? adminNav : userNav, [isAdmin])
  const [mobileOpen, setMobileOpen] = useState(false)

  const handleLogout = () => {
    logout()
    navigateFn('/login')
    setMobileOpen(false)
  }

  const handleNav = (path) => {
    navigateFn(path)
    setMobileOpen(false)
  }

  useEffect(() => {
    setMobileOpen(false)
  }, [navigate.pathname])

  const sidebarContent = (
    <>
      <div className="px-6 pt-8 pb-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-primary-container flex items-center justify-center shadow-sm">
            <span className="text-white text-sm font-bold">AI</span>
          </div>
          <div>
            <h1 className="text-lg font-bold text-on-surface">AASA</h1>
            <p className="text-xs text-on-surface-variant/70 -mt-0.5">Adaptive AI Study Architect</p>
          </div>
        </div>
      </div>
      <div className="glass-divider mx-6" />
      <nav className="flex-1 px-4 py-6 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon
          const isActive = navigate.pathname === item.path
            || (item.path === '/study' && navigate.pathname.startsWith('/study/'))
            || (item.path === '/practice' && (navigate.pathname.startsWith('/practice/') || navigate.pathname.startsWith('/diagnostic/')))
          return (
            <button
              key={item.path}
              onClick={() => handleNav(item.path)}
              className={`relative w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 ${
                isActive
                  ? 'text-primary bg-primary/10'
                  : 'text-on-surface-variant hover:text-on-surface hover:bg-white/60'
              }`}
            >
              {isActive && (
                <motion.div
                  layoutId="activeNav"
                  className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 rounded-full bg-primary"
                  transition={{ type: 'spring', stiffness: 400, damping: 30 }}
                />
              )}
              <Icon className="w-5 h-5 flex-shrink-0" />
              <span>{item.label}</span>
            </button>
          )
        })}
      </nav>
      <div className="px-4 pb-6">
        <div className="glass-divider mb-4 mx-2" />
        <div className="flex items-center gap-3 px-4 py-3 rounded-xl bg-white/60">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary/20 to-secondary/20 flex items-center justify-center">
            <span className="text-xs font-bold text-primary">
              {user?.name?.charAt(0)?.toUpperCase() || 'U'}
            </span>
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-on-surface truncate">{user?.name || 'User'}</p>
            <p className="text-xs text-on-surface-variant/70 truncate">{user?.email || ''}</p>
          </div>
          <button
            onClick={handleLogout}
            className="p-2 rounded-lg text-on-surface-variant hover:text-error hover:bg-error/10 transition-all flex-shrink-0"
            title="Logout"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </>
  )

  return (
    <>
      <button
        onClick={() => setMobileOpen(true)}
        className="md:hidden fixed top-4 left-4 z-[60] p-2.5 rounded-xl glass-pane bg-white/80 border border-black/8 shadow-sm"
        aria-label="Open menu"
      >
        <Menu className="w-5 h-5 text-on-surface" />
      </button>

      <aside className="hidden md:flex glass-sidebar fixed left-0 top-0 h-screen w-sidebar z-50 flex-col">
        {sidebarContent}
      </aside>

      <AnimatePresence>
        {mobileOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 bg-black/30 z-[70] md:hidden"
              onClick={() => setMobileOpen(false)}
            />
            <motion.aside
              initial={{ x: -280 }}
              animate={{ x: 0 }}
              exit={{ x: -280 }}
              transition={{ type: 'spring', stiffness: 400, damping: 35 }}
              className="glass-sidebar fixed left-0 top-0 h-screen w-sidebar z-[80] flex flex-col md:hidden"
            >
              <button
                onClick={() => setMobileOpen(false)}
                className="absolute top-4 right-4 p-2 rounded-lg text-on-surface-variant hover:bg-white/60"
                aria-label="Close menu"
              >
                <X className="w-5 h-5" />
              </button>
              {sidebarContent}
            </motion.aside>
          </>
        )}
      </AnimatePresence>
    </>
  )
}
