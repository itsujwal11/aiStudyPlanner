import React from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { LogOut, BarChart3, Lightbulb, LineChart as LineChartIcon, User, FileText } from 'lucide-react'

export const Navigation = ({ currentPage }) => {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const isActive = (page) => currentPage === page

  return (
    <nav className="glass-effect border-b border-slate-700 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-6 py-4">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h1 className="text-2xl font-bold gradient-text">AASA</h1>
            <p className="text-xs text-slate-400">Adaptive AI Study Architect</p>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-300">{user?.name}</span>
            <button
              onClick={handleLogout}
              className="flex items-center gap-2 px-4 py-2 text-sm bg-red-500/10 text-red-400 rounded-lg hover:bg-red-500/20 transition"
            >
              <LogOut className="w-4 h-4" />
              Logout
            </button>
          </div>
        </div>
        <div className="flex gap-2 overflow-x-auto">
          <button
            onClick={() => navigate('/dashboard')}
            className={`flex items-center gap-2 px-4 py-2 text-sm rounded-lg transition whitespace-nowrap ${
              isActive('dashboard')
                ? 'bg-cyan-500/20 text-cyan-400 hover:bg-cyan-500/30'
                : 'bg-slate-700/50 text-slate-300 hover:bg-slate-700'
            }`}
          >
            <BarChart3 className="w-4 h-4" />
            Dashboard
          </button>
          <button
            onClick={() => navigate('/analytics')}
            className={`flex items-center gap-2 px-4 py-2 text-sm rounded-lg transition whitespace-nowrap ${
              isActive('analytics')
                ? 'bg-cyan-500/20 text-cyan-400 hover:bg-cyan-500/30'
                : 'bg-slate-700/50 text-slate-300 hover:bg-slate-700'
            }`}
          >
            <LineChartIcon className="w-4 h-4" />
            Analytics
          </button>
          <button
            onClick={() => navigate('/recommendations')}
            className={`flex items-center gap-2 px-4 py-2 text-sm rounded-lg transition whitespace-nowrap ${
              isActive('recommendations')
                ? 'bg-cyan-500/20 text-cyan-400 hover:bg-cyan-500/30'
                : 'bg-slate-700/50 text-slate-300 hover:bg-slate-700'
            }`}
          >
            <Lightbulb className="w-4 h-4" />
            Recommendations
          </button>
          <button
            onClick={() => navigate('/profile')}
            className={`flex items-center gap-2 px-4 py-2 text-sm rounded-lg transition whitespace-nowrap ${
              isActive('profile')
                ? 'bg-cyan-500/20 text-cyan-400 hover:bg-cyan-500/30'
                : 'bg-slate-700/50 text-slate-300 hover:bg-slate-700'
            }`}
          >
            <User className="w-4 h-4" />
            Profile
          </button>
          <button
            onClick={() => navigate('/reports')}
            className={`flex items-center gap-2 px-4 py-2 text-sm rounded-lg transition whitespace-nowrap ${
              isActive('reports')
                ? 'bg-cyan-500/20 text-cyan-400 hover:bg-cyan-500/30'
                : 'bg-slate-700/50 text-slate-300 hover:bg-slate-700'
            }`}
          >
            <FileText className="w-4 h-4" />
            Reports
          </button>
          <button
            onClick={() => navigate('/planner')}
            className={`flex items-center gap-2 px-4 py-2 text-sm rounded-lg transition whitespace-nowrap ${
                isActive('planner')
                  ? 'bg-cyan-500/20 text-cyan-400 hover:bg-cyan-500/30'
                  : 'bg-slate-700/50 text-slate-300 hover:bg-slate-700'
            }`}
          >
            <BarChart3 className="w-4 h-4" />
            Planner
          </button>
        </div>
      </div>
    </nav>
  )
}
