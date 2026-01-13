import { useState, useEffect, useRef } from 'react'
import { X, Loader2, AlertCircle, RefreshCw } from 'lucide-react'
import { useUpdateAiAgent, useRefreshAgentModels } from '../hooks/useAiAgents'
import type { AiAgentConfig, ProviderInfo, AiAgentConfigUpdate } from '../lib/types'

interface EditAiAgentModalProps {
  agent: AiAgentConfig
  onClose: () => void
  providers: ProviderInfo[]
}

export function EditAiAgentModal({ agent, onClose, providers }: EditAiAgentModalProps) {
  const [name, setName] = useState(agent.name)
  const [selectedModel, setSelectedModel] = useState(agent.defaultModel)
  const [endpointUrl, setEndpointUrl] = useState(agent.endpointUrl || '')
  const [enabled, setEnabled] = useState(agent.enabled)
  const [updateCredentials, setUpdateCredentials] = useState(false)
  const [credentials, setCredentials] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  const updateAgent = useUpdateAiAgent()
  const refreshModels = useRefreshAgentModels()

  const modalRef = useRef<HTMLDivElement>(null)

  const provider = providers.find(p => p.type === agent.providerType)
  const models = agent.cachedModels || []

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [onClose])

  const handleCredentialChange = (field: string, value: string) => {
    setCredentials(prev => ({ ...prev, [field]: value }))
  }

  const handleRefreshModels = async () => {
    setError(null)
    try {
      await refreshModels.mutateAsync(agent.id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to refresh models')
    }
  }

  const handleSave = async () => {
    setError(null)

    try {
      const config: AiAgentConfigUpdate = {
        name: name.trim(),
        defaultModel: selectedModel,
        enabled,
      }

      if (provider?.supportsCustomEndpoint) {
        config.endpointUrl = endpointUrl || undefined
      }

      if (updateCredentials && Object.keys(credentials).length > 0) {
        config.credentials = credentials
      }

      await updateAgent.mutateAsync({ id: agent.id, config })
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update agent')
    }
  }

  return (
    <div
      className="neo-modal-backdrop"
      onClick={onClose}
      role="presentation"
    >
      <div
        ref={modalRef}
        className="neo-modal w-full max-w-lg p-6"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-labelledby="edit-agent-title"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <h2 id="edit-agent-title" className="font-display text-xl font-bold">
            Edit Agent
          </h2>
          <button
            onClick={onClose}
            className="neo-btn neo-btn-ghost p-2"
            aria-label="Close"
          >
            <X size={20} />
          </button>
        </div>

        <div className="space-y-4">
          {/* Provider Info (Read-only) */}
          <div className="p-3 bg-gray-50 border-3 border-[var(--color-neo-border)]">
            <div className="text-sm font-bold">{provider?.displayName || agent.providerType}</div>
            <div className="text-xs text-[var(--color-neo-text-secondary)]">
              {provider?.description}
            </div>
          </div>

          {/* Agent Name */}
          <div>
            <label className="block text-sm font-bold mb-1">Agent Name *</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="neo-input w-full"
            />
          </div>

          {/* Model Selection */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-sm font-bold">Default Model *</label>
              <button
                onClick={handleRefreshModels}
                disabled={refreshModels.isPending}
                className="neo-btn neo-btn-ghost text-xs flex items-center gap-1 p-1"
              >
                <RefreshCw size={12} className={refreshModels.isPending ? 'animate-spin' : ''} />
                Refresh
              </button>
            </div>
            <select
              value={selectedModel}
              onChange={(e) => setSelectedModel(e.target.value)}
              className="neo-input w-full"
            >
              {models.length > 0 ? (
                models.map((model) => (
                  <option key={model.id} value={model.id}>
                    {model.name}
                  </option>
                ))
              ) : (
                <option value={agent.defaultModel}>{agent.defaultModel}</option>
              )}
            </select>
            {models.length === 0 && (
              <p className="text-xs text-[var(--color-neo-text-secondary)] mt-1">
                Click "Refresh" to load available models
              </p>
            )}
          </div>

          {/* Custom Endpoint */}
          {provider?.supportsCustomEndpoint && (
            <div>
              <label className="block text-sm font-bold mb-1">Endpoint URL</label>
              <input
                type="url"
                value={endpointUrl}
                onChange={(e) => setEndpointUrl(e.target.value)}
                placeholder="https://..."
                className="neo-input w-full"
              />
            </div>
          )}

          {/* Enabled Toggle */}
          <div className="flex items-center justify-between">
            <div>
              <label className="font-bold text-sm">Enabled</label>
              <p className="text-xs text-[var(--color-neo-text-secondary)]">
                Disabled agents won't appear in selection
              </p>
            </div>
            <button
              onClick={() => setEnabled(!enabled)}
              className={`relative w-14 h-8 rounded-none border-3 border-[var(--color-neo-border)] transition-colors ${
                enabled ? 'bg-[var(--color-neo-success)]' : 'bg-white'
              }`}
              role="switch"
              aria-checked={enabled}
            >
              <span
                className={`absolute top-1 w-5 h-5 bg-[var(--color-neo-border)] transition-transform ${
                  enabled ? 'left-7' : 'left-1'
                }`}
              />
            </button>
          </div>

          {/* Update Credentials */}
          <div className="border-t-3 border-[var(--color-neo-border)] pt-4">
            <div className="flex items-center gap-2 mb-3">
              <input
                type="checkbox"
                id="update-credentials"
                checked={updateCredentials}
                onChange={(e) => setUpdateCredentials(e.target.checked)}
                className="w-4 h-4"
              />
              <label htmlFor="update-credentials" className="text-sm font-bold">
                Update Credentials
              </label>
            </div>

            {updateCredentials && provider && (
              <div className="space-y-3 pl-6">
                {provider.requiredCredentials.map((field) => (
                  <div key={field.name}>
                    <label className="block text-sm font-bold mb-1">
                      {field.label} {field.required && '*'}
                    </label>
                    <input
                      type={field.type === 'password' ? 'password' : 'text'}
                      value={credentials[field.name] || ''}
                      onChange={(e) => handleCredentialChange(field.name, e.target.value)}
                      placeholder={field.placeholder}
                      className="neo-input w-full"
                    />
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Error */}
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm flex items-center gap-2">
              <AlertCircle size={16} />
              {error}
            </div>
          )}

          {/* Actions */}
          <div className="flex justify-end gap-3 pt-4">
            <button onClick={onClose} className="neo-btn neo-btn-ghost">
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={!name.trim() || !selectedModel || updateAgent.isPending}
              className="neo-btn neo-btn-primary flex items-center gap-2"
            >
              {updateAgent.isPending ? (
                <>
                  <Loader2 className="animate-spin" size={16} />
                  Saving...
                </>
              ) : (
                'Save Changes'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
