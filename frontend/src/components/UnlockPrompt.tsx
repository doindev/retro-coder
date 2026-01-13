import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { X, Loader2, AlertCircle, Lock, Key } from 'lucide-react'
import { useUnlockEncryption } from '../hooks/useAiAgents'

interface UnlockPromptProps {
  onClose: () => void
  onUnlocked: () => void
}

export function UnlockPrompt({ onClose, onUnlocked }: UnlockPromptProps) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const unlock = useUnlockEncryption()
  const inputRef = useRef<HTMLInputElement>(null)
  const onCloseRef = useRef(onClose)

  // Keep the ref updated with latest onClose
  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  // Focus input only on mount
  useEffect(() => {
    // Small delay to ensure portal is mounted
    const timer = setTimeout(() => {
      inputRef.current?.focus()
    }, 0)
    return () => clearTimeout(timer)
  }, [])

  // Handle escape key - use ref to avoid re-running effect
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCloseRef.current()
      }
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [])

  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!password.trim()) return

    setError(null)

    try {
      await unlock.mutateAsync(password)
      onUnlocked()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid password')
      setPassword('')
      inputRef.current?.focus()
    }
  }

  // Use portal to render outside parent DOM hierarchy to avoid focus interference
  return createPortal(
    <div
      className="neo-modal-backdrop"
      onClick={onClose}
      role="presentation"
      style={{ zIndex: 1000 }}
    >
      <div
        className="neo-modal w-full max-w-sm p-6"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-labelledby="unlock-title"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Lock size={24} />
            <h2 id="unlock-title" className="font-display text-xl font-bold">
              Unlock Credentials
            </h2>
          </div>
          <button
            onClick={onClose}
            className="neo-btn neo-btn-ghost p-2"
            aria-label="Close"
          >
            <X size={20} />
          </button>
        </div>

        <p className="text-sm text-[var(--color-neo-text-secondary)] mb-6">
          Enter your encryption password to access AI agent credentials.
          This is required once per session.
        </p>

        <form onSubmit={handleUnlock} className="space-y-4">
          <div>
            <label className="block text-sm font-bold mb-1">Password</label>
            <input
              ref={inputRef}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter encryption password"
              className="neo-input w-full"
              autoComplete="current-password"
              autoFocus
            />
          </div>

          {error && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm flex items-center gap-2">
              <AlertCircle size={16} />
              {error}
            </div>
          )}

          <div className="text-xs text-[var(--color-neo-text-secondary)]">
            <strong>Tip:</strong> You can set the RETROCODER_ENCRYPTION_KEY environment
            variable to auto-unlock on startup.
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="neo-btn neo-btn-ghost">
              Cancel
            </button>
            <button
              type="submit"
              disabled={!password.trim() || unlock.isPending}
              className="neo-btn neo-btn-primary flex items-center gap-2"
            >
              {unlock.isPending ? (
                <>
                  <Loader2 className="animate-spin" size={16} />
                  Unlocking...
                </>
              ) : (
                <>
                  <Key size={16} />
                  Unlock
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  )
}
