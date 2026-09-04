import React, { useEffect, useRef } from 'react'

const SCRIPT_ID = 'google-identity-services'

export const GoogleSignInButton = ({ onCredential, onError }) => {
  const containerRef = useRef(null)
  const credentialHandler = useRef(onCredential)
  const errorHandler = useRef(onError)
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID

  credentialHandler.current = onCredential
  errorHandler.current = onError

  useEffect(() => {
    if (!clientId) return undefined

    const renderButton = () => {
      if (!window.google?.accounts?.id || !containerRef.current) return
      containerRef.current.innerHTML = ''
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: ({ credential }) => {
          if (credential) credentialHandler.current(credential)
          else errorHandler.current?.('Google did not return a credential')
        },
      })
      window.google.accounts.id.renderButton(containerRef.current, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        shape: 'rectangular',
        text: 'continue_with',
        width: containerRef.current.offsetWidth,
      })
    }

    if (window.google?.accounts?.id) {
      renderButton()
      return undefined
    }

    let script = document.getElementById(SCRIPT_ID)
    if (!script) {
      script = document.createElement('script')
      script.id = SCRIPT_ID
      script.src = 'https://accounts.google.com/gsi/client'
      script.async = true
      script.defer = true
      document.head.appendChild(script)
    }
    script.addEventListener('load', renderButton)
    script.addEventListener('error', () => errorHandler.current?.('Unable to load Google Sign-In'))
    return () => script?.removeEventListener('load', renderButton)
  }, [clientId])

  if (!clientId) return null
  return <div ref={containerRef} className="w-full min-h-[44px]" />
}
