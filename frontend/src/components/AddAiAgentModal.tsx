import { useState, useEffect, useRef } from 'react'
import { X, Loader2, AlertCircle, CheckCircle2, ChevronRight, ChevronLeft } from 'lucide-react'
import { useCreateAiAgent, useDiscoverModels } from '../hooks/useAiAgents'
import type { ProviderInfo, ModelInfo, AiAgentConfigCreate } from '../lib/types'

interface AddAiAgentModalProps {
  onClose: () => void
  providers: ProviderInfo[]
}

type Step = 'provider' | 'credentials' | 'model' | 'confirm'

export function AddAiAgentModal({ onClose, providers }: AddAiAgentModalProps) {
  const [step, setStep] = useState<Step>('provider')
  const [name, setName] = useState('')
  const [selectedProvider, setSelectedProvider] = useState<ProviderInfo | null>(null)
  const [credentials, setCredentials] = useState<Record<string, string>>({})
  const [endpointUrl, setEndpointUrl] = useState('')
  const [discoveredModels, setDiscoveredModels] = useState<ModelInfo[]>([])
  const [selectedModel, setSelectedModel] = useState('')
  const [error, setError] = useState<string | null>(null)

  const createAgent = useCreateAiAgent()
  const discoverModels = useDiscoverModels()

  const modalRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [onClose])

  const handleProviderSelect = (provider: ProviderInfo) => {
    setSelectedProvider(provider)
    setCredentials({})
    setEndpointUrl('')
    setError(null)
    setStep('credentials')
  }

  const handleCredentialChange = (field: string, value: string) => {
    setCredentials(prev => ({ ...prev, [field]: value }))
  }

  const handleDiscoverModels = async () => {
    if (!selectedProvider) return

    setError(null)

    try {
      const endpoint = selectedProvider.supportsCustomEndpoint ? endpointUrl || undefined : undefined
      const models = await discoverModels.mutateAsync({
        providerType: selectedProvider.type,
        credentials,
        endpointUrl: endpoint,
      })
      setDiscoveredModels(models)
      if (models.length > 0) {
        setSelectedModel(models[0].id)
      }
      setStep('model')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to discover models')
    }
  }

  const handleCreate = async () => {
    if (!selectedProvider || !name.trim() || !selectedModel) return

    setError(null)

    try {
      const config: AiAgentConfigCreate = {
        name: name.trim(),
        providerType: selectedProvider.type,
        credentials,
        defaultModel: selectedModel,
      }

      if (selectedProvider.supportsCustomEndpoint && endpointUrl) {
        config.endpointUrl = endpointUrl
      }

      await createAgent.mutateAsync(config)
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create agent')
    }
  }

  const canProceedFromCredentials = () => {
    if (!selectedProvider) return false
    const requiredFields = selectedProvider.requiredCredentials.filter(f => f.required)
    return requiredFields.every(f => credentials[f.name]?.trim())
  }

  const renderProviderStep = () => (
    <div className="space-y-4">
      <p className="text-sm text-[var(--color-neo-text-secondary)]">
        Select the LLM provider you want to configure.
      </p>
      <div className="grid grid-cols-2 gap-3 max-h-[400px] overflow-y-auto">
        {providers.filter(p => p.type !== 'CLAUDE_CODE').map((provider) => (
          <button
            key={provider.type}
            onClick={() => handleProviderSelect(provider)}
            className="p-4 border-3 border-[var(--color-neo-border)] bg-white hover:bg-gray-50 text-left transition-colors"
          >
            <div className="font-display font-bold">{provider.displayName}</div>
            <div className="text-xs text-[var(--color-neo-text-secondary)] mt-1 line-clamp-2">
              {provider.description}
            </div>
            {provider.supportsStreaming && (
              <div className="text-xs text-green-600 mt-2">Supports streaming</div>
            )}
          </button>
        ))}
      </div>
    </div>
  )

  const renderCredentialsStep = () => {
    if (!selectedProvider) return null

    return (
      <div className="space-y-4">
        <div className="flex items-center gap-2 mb-4">
          <button onClick={() => setStep('provider')} className="neo-btn neo-btn-ghost p-1">
            <ChevronLeft size={20} />
          </button>
          <h3 className="font-display font-bold">{selectedProvider.displayName}</h3>
        </div>

        {/* Agent Name */}
        <div>
          <label className="block text-sm font-bold mb-1">Agent Name *</label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g., My OpenAI Agent"
            className="neo-input w-full"
          />
        </div>

        {/* Credential Fields */}
        {selectedProvider.requiredCredentials.map((field) => (
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

        {/* Custom Endpoint */}
        {selectedProvider.supportsCustomEndpoint && (
          <div>
            <label className="block text-sm font-bold mb-1">Endpoint URL (optional)</label>
            <input
              type="url"
              value={endpointUrl}
              onChange={(e) => setEndpointUrl(e.target.value)}
              placeholder="https://..."
              className="neo-input w-full"
            />
          </div>
        )}

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm flex items-center gap-2">
            <AlertCircle size={16} />
            {error}
          </div>
        )}

        <div className="flex justify-end gap-3 pt-4">
          <button onClick={onClose} className="neo-btn neo-btn-ghost">
            Cancel
          </button>
          <button
            onClick={handleDiscoverModels}
            disabled={!canProceedFromCredentials() || !name.trim() || discoverModels.isPending}
            className="neo-btn neo-btn-primary flex items-center gap-2"
          >
            {discoverModels.isPending ? (
              <>
                <Loader2 className="animate-spin" size={16} />
                Validating...
              </>
            ) : (
              <>
                Discover Models
                <ChevronRight size={16} />
              </>
            )}
          </button>
        </div>
      </div>
    )
  }

  const renderModelStep = () => {
    if (!selectedProvider) return null

    return (
      <div className="space-y-4">
        <div className="flex items-center gap-2 mb-4">
          <button onClick={() => setStep('credentials')} className="neo-btn neo-btn-ghost p-1">
            <ChevronLeft size={20} />
          </button>
          <h3 className="font-display font-bold">Select Model</h3>
        </div>

        <div className="p-3 bg-green-50 border border-green-200 text-green-700 text-sm flex items-center gap-2">
          <CheckCircle2 size={16} />
          Credentials validated successfully!
        </div>

        <div>
          <label className="block text-sm font-bold mb-1">Default Model *</label>
          <select
            value={selectedModel}
            onChange={(e) => setSelectedModel(e.target.value)}
            className="neo-input w-full"
          >
            {discoveredModels.map((model) => (
              <option key={model.id} value={model.id}>
                {model.name}
              </option>
            ))}
          </select>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm flex items-center gap-2">
            <AlertCircle size={16} />
            {error}
          </div>
        )}

        <div className="flex justify-end gap-3 pt-4">
          <button onClick={onClose} className="neo-btn neo-btn-ghost">
            Cancel
          </button>
          <button
            onClick={handleCreate}
            disabled={!selectedModel || createAgent.isPending}
            className="neo-btn neo-btn-primary flex items-center gap-2"
          >
            {createAgent.isPending ? (
              <>
                <Loader2 className="animate-spin" size={16} />
                Creating...
              </>
            ) : (
              'Create Agent'
            )}
          </button>
        </div>
      </div>
    )
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
        aria-labelledby="add-agent-title"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <h2 id="add-agent-title" className="font-display text-xl font-bold">
            Add AI Agent
          </h2>
          <button
            onClick={onClose}
            className="neo-btn neo-btn-ghost p-2"
            aria-label="Close"
          >
            <X size={20} />
          </button>
        </div>

        {/* Step Content */}
        {step === 'provider' && renderProviderStep()}
        {step === 'credentials' && renderCredentialsStep()}
        {step === 'model' && renderModelStep()}
      </div>
    </div>
  )
}
