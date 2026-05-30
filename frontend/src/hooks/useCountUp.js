import { useState, useEffect, useRef } from 'react'

export function useCountUp(end, duration = 800, startOnMount = true) {
  const [count, setCount] = useState(0)
  const startedRef = useRef(false)

  useEffect(() => {
    if (!startOnMount || startedRef.current) return
    startedRef.current = true

    if (end === 0) { setCount(0); return }

    const startTime = performance.now()
    const startVal = 0

    function step(currentTime) {
      const elapsed = currentTime - startTime
      const progress = Math.min(elapsed / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3)
      setCount(Math.floor(startVal + (end - startVal) * eased))
      if (progress < 1) requestAnimationFrame(step)
    }

    requestAnimationFrame(step)
  }, [end, duration, startOnMount])

  return count
}
