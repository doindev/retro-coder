import { useState, useEffect, useRef } from 'react'
import { X, Plus, Trash2, Edit2, Loader2, AlertCircle, Check, Bot } from 'lucide-react'
import {
  useAiAgents,
  useDeleteAiAgent,
  useProviders,
  useAgentDefaults,
  useUpdateAgentDefaults,
} from '../hooks/useAiAgents'
import type { AiAgentConfig, AgentDefaults } from '../lib/types'
import { AddAiAgentModal } from './AddAiAgentModal'
import { EditAiAgentModal } from './EditAiAgentModal'

interface AiAgentsModalProps {
  onClose: () => void
}

export function AiAgentsModal({ onClose }: AiAgentsModalProps) {
  const { data: agents, isLoading, isError, refetch } = useAiAgents()
  const { data: providers } = useProviders()
  const { data: defaults } = useAgentDefaults()
  const updateDefaults = useUpdateAgentDefaults()
  const deleteAgent = useDeleteAiAgent()

  const [showAddModal, setShowAddModal] = useState(false)
  const [editingAgent, setEditingAgent] = useState<AiAgentConfig | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const modalRef = useRef<HTMLDivElement>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const onCloseRef = useRef(onClose)
  const hasInitialFocus = useRef(false)

  // Keep onClose ref updated
  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  // Focus close button only on initial mount
  useEffect(() => {
    if (!hasInitialFocus.current) {
      closeButtonRef.current?.focus()
      hasInitialFocus.current = true
    }
  }, [])

  // Handle escape key
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !showAddModal && !editingAgent) {
        onCloseRef.current()
      }
    }

    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [showAddModal, editingAgent])

  const handleDelete = async (id: number) => {
    if (deletingId === id) {
      try {
        await deleteAgent.mutateAsync(id)
      } catch (error) {
        console.error('Failed to delete agent:', error)
      }
      setDeletingId(null)
    } else {
      setDeletingId(id)
      // Auto-cancel after 3 seconds
      setTimeout(() => setDeletingId(null), 3000)
    }
  }

  const handleDefaultChange = (role: keyof AgentDefaults, agentId: number | null) => {
    if (!defaults) return
    updateDefaults.mutate({
      ...defaults,
      [role]: agentId,
    })
  }

  const getProviderName = (type: string) => {
    return providers?.find(p => p.type === type)?.displayName || type
  }

  return (
    <div
      className="neo-modal-backdrop"
      onClick={onClose}
      role="presentation"
    >
      <div
        ref={modalRef}
        className="neo-modal w-full max-w-2xl p-6 max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-labelledby="ai-agents-title"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Bot size={24} />
            <h2 id="ai-agents-title" className="font-display text-xl font-bold">
              AI Agents
            </h2>
          </div>
          <button
            ref={closeButtonRef}
            onClick={onClose}
            className="neo-btn neo-btn-ghost p-2"
            aria-label="Close"
          >
            <X size={20} />
          </button>
        </div>

        {/* Loading State */}
        {isLoading && (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="animate-spin" size={24} />
            <span className="ml-2">Loading agents...</span>
          </div>
        )}

        {/* Error State */}
        {isError && (
          <div className="p-4 bg-[var(--color-neo-danger)] text-white border-3 border-[var(--color-neo-border)] mb-4">
            <div className="flex items-center gap-2">
              <AlertCircle size={18} />
              <span>Failed to load AI agents</span>
            </div>
            <button
              onClick={() => refetch()}
              className="mt-2 underline text-sm"
            >
              Retry
            </button>
          </div>
        )}

        {/* Content */}
        {!isLoading && !isError && (
          <div className="space-y-6">
            {/* Agent List */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-display font-bold">Configured Agents</h3>
                <button
                  onClick={() => setShowAddModal(true)}
                  className="neo-btn neo-btn-primary flex items-center gap-2"
                >
                  <Plus size={16} />
                  Add Agent
                </button>
              </div>

              {agents && agents.length === 0 && (
                <div className="p-6 border-3 border-dashed border-[var(--color-neo-border)] text-center text-[var(--color-neo-text-secondary)]">
                  <Bot size={32} className="mx-auto mb-2 opacity-50" />
                  <p>No AI agents configured yet.</p>
                  <p className="text-sm mt-1">Add an agent to use different LLM providers.</p>
                </div>
              )}

              {agents && agents.length > 0 && (
                <div className="space-y-3">
                  {agents.map((agent) => (
                    <div
                      key={agent.id}
                      className={`p-4 border-3 border-[var(--color-neo-border)] ${
                        !agent.enabled ? 'opacity-60 bg-gray-50' : 'bg-white'
                      }`}
                    >
                      <div className="flex items-start justify-between">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-display font-bold">{agent.name}</span>
                            {!agent.enabled && (
                              <span className="text-xs px-2 py-0.5 bg-gray-200 text-gray-600">
                                Disabled
                              </span>
                            )}
                          </div>
                          <p className="text-sm text-[var(--color-neo-text-secondary)]">
                            {getProviderName(agent.providerType)}
                            {agent.defaultModel && ` • ${agent.defaultModel}`}
                          </p>
                          {agent.endpointUrl && (
                            <p className="text-xs text-[var(--color-neo-text-secondary)] mt-1">
                              {agent.endpointUrl}
                            </p>
                          )}
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => setEditingAgent(agent)}
                            className="neo-btn neo-btn-ghost p-2"
                            title="Edit agent"
                          >
                            <Edit2 size={16} />
                          </button>
                          <button
                            onClick={() => handleDelete(agent.id)}
                            className={`neo-btn p-2 ${
                              deletingId === agent.id
                                ? 'neo-btn-danger'
                                : 'neo-btn-ghost'
                            }`}
                            title={deletingId === agent.id ? 'Click again to confirm' : 'Delete agent'}
                          >
                            {deletingId === agent.id ? (
                              <Check size={16} />
                            ) : (
                              <Trash2 size={16} />
                            )}
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Default Agent Settings */}
            {agents && agents.length > 0 && (
              <div className="border-t-3 border-[var(--color-neo-border)] pt-6">
                <h3 className="font-display font-bold mb-4">Default Agents by Role</h3>
                <p className="text-sm text-[var(--color-neo-text-secondary)] mb-4">
                  Configure which agent to use for each role. Leave unset to use the global default.
                </p>

                <div className="space-y-4">
                  {/* Global Default */}
                  <div className="flex items-center justify-between">
                    <div>
                      <label className="font-display font-bold text-sm">Global Default</label>
                      <p className="text-xs text-[var(--color-neo-text-secondary)]">
                        Used when no specific agent is set
                      </p>
                    </div>
                    <select
                      value={defaults?.defaultAgentId ?? ''}
                      onChange={(e) => handleDefaultChange('defaultAgentId', e.target.value ? parseInt(e.target.value) : null)}
                      className="neo-input w-48"
                    >
                      <option value="">Claude Code (Built-in)</option>
                      {agents.filter(a => a.enabled).map(agent => (
                        <option key={agent.id} value={agent.id}>{agent.name}</option>
                      ))}
                    </select>
                  </div>

                  {/* Spec Creation */}
                  <div className="flex items-center justify-between">
                    <div>
                      <label className="font-display font-bold text-sm">Spec Creation</label>
                      <p className="text-xs text-[var(--color-neo-text-secondary)]">
                        Used for generating app specs
                      </p>
                    </div>
                    <select
                      value={defaults?.specCreationAgentId ?? ''}
                      onChange={(e) => handleDefaultChange('specCreationAgentId', e.target.value ? parseInt(e.target.value) : null)}
                      className="neo-input w-48"
                    >
                      <option value="">Use Global Default</option>
                      {agents.filter(a => a.enabled).map(agent => (
                        <option key={agent.id} value={agent.id}>{agent.name}</option>
                      ))}
                    </select>
                  </div>

                  {/* Initialization */}
                  <div className="flex items-center justify-between">
                    <div>
                      <label className="font-display font-bold text-sm">Initialization</label>
                      <p className="text-xs text-[var(--color-neo-text-secondary)]">
                        Used for project initialization
                      </p>
                    </div>
                    <select
                      value={defaults?.initializationAgentId ?? ''}
                      onChange={(e) => handleDefaultChange('initializationAgentId', e.target.value ? parseInt(e.target.value) : null)}
                      className="neo-input w-48"
                    >
                      <option value="">Use Global Default</option>
                      {agents.filter(a => a.enabled).map(agent => (
                        <option key={agent.id} value={agent.id}>{agent.name}</option>
                      ))}
                    </select>
                  </div>

                  {/* Coding */}
                  <div className="flex items-center justify-between">
                    <div>
                      <label className="font-display font-bold text-sm">Coding Agent</label>
                      <p className="text-xs text-[var(--color-neo-text-secondary)]">
                        Used for implementing features
                      </p>
                    </div>
                    <select
                      value={defaults?.codingAgentId ?? ''}
                      onChange={(e) => handleDefaultChange('codingAgentId', e.target.value ? parseInt(e.target.value) : null)}
                      className="neo-input w-48"
                    >
                      <option value="">Use Global Default</option>
                      {agents.filter(a => a.enabled).map(agent => (
                        <option key={agent.id} value={agent.id}>{agent.name}</option>
                      ))}
                    </select>
                  </div>
                </div>

                {updateDefaults.isError && (
                  <div className="mt-3 p-2 bg-red-50 border border-red-200 text-red-700 text-sm">
                    Failed to save defaults. Please try again.
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Add Agent Modal */}
      {showAddModal && (
        <AddAiAgentModal
          onClose={() => setShowAddModal(false)}
          providers={providers || []}
        />
      )}

      {/* Edit Agent Modal */}
      {editingAgent && (
        <EditAiAgentModal
          agent={editingAgent}
          onClose={() => setEditingAgent(null)}
          providers={providers || []}
        />
      )}
    </div>
  )
}
