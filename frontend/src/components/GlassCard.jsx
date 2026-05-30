import React from 'react'
import { motion } from 'framer-motion'

const glassMap = {
  sm: 'glass-pane-sm',
  DEFAULT: 'glass-pane',
  lg: 'glass-pane-lg',
}

export const GlassCard = ({ children, className = '', glass = 'DEFAULT', hover = true, ...props }) => {
  const base = glassMap[glass] || glassMap.DEFAULT
  return (
    <motion.div
      variants={{
        hidden: { opacity: 0, y: 16 },
        show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: 'easeOut' } },
      }}
      className={`${base} rounded-xl p-6 ${hover ? 'hover:bg-white/85 transition-all duration-200' : ''} ${className}`}
      {...props}
    >
      {children}
    </motion.div>
  )
}

export const GlassStatCard = ({ icon: Icon, label, value, color = 'text-primary', children }) => (
  <GlassCard>
    <div className="flex items-start justify-between">
      <div>
        <p className="text-sm text-on-surface-variant/70 font-medium mb-1">{label}</p>
        <p className={`text-3xl font-bold ${color}`}>{value}</p>
        {children}
      </div>
      <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
        <Icon className={`w-5 h-5 ${color}`} />
      </div>
    </div>
  </GlassCard>
)
