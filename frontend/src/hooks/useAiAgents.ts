/**
 * React Query hooks for AI Agent configuration management
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import * as api from '@/lib/api'
import type {
  AiAgentConfig,
  AiAgentConfigCreate,
  AiAgentConfigUpdate,
  DiscoverModelsRequest,
  AgentDefaults,
} from '@/lib/types'

// Query keys
export const aiAgentKeys = {
  all: ['ai-agents'] as const,
  lists: () => [...aiAgentKeys.all, 'list'] as const,
  list: () => [...aiAgentKeys.lists()] as const,
  enabled: () => [...aiAgentKeys.all, 'enabled'] as const,
  details: () => [...aiAgentKeys.all, 'detail'] as const,
  detail: (id: number) => [...aiAgentKeys.details(), id] as const,
  providers: () => [...aiAgentKeys.all, 'providers'] as const,
  defaults: () => [...aiAgentKeys.all, 'defaults'] as const,
}

export const securityKeys = {
  all: ['security'] as const,
  status: () => [...securityKeys.all, 'status'] as const,
}

// ============================================================================
// AI Agent Queries
// ============================================================================

export function useAiAgents() {
  return useQuery({
    queryKey: aiAgentKeys.list(),
    queryFn: api.listAiAgents,
  })
}

export function useEnabledAiAgents() {
  return useQuery({
    queryKey: aiAgentKeys.enabled(),
    queryFn: api.listEnabledAiAgents,
  })
}

export function useAiAgent(id: number) {
  return useQuery({
    queryKey: aiAgentKeys.detail(id),
    queryFn: () => api.getAiAgent(id),
    enabled: id > 0,
  })
}

export function useProviders() {
  return useQuery({
    queryKey: aiAgentKeys.providers(),
    queryFn: api.getProviders,
    staleTime: Infinity, // Providers don't change
  })
}

export function useAgentDefaults() {
  return useQuery({
    queryKey: aiAgentKeys.defaults(),
    queryFn: api.getAgentDefaults,
  })
}

// ============================================================================
// AI Agent Mutations
// ============================================================================

export function useCreateAiAgent() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (config: AiAgentConfigCreate) => api.createAiAgent(config),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: aiAgentKeys.lists() })
      queryClient.invalidateQueries({ queryKey: aiAgentKeys.enabled() })
    },
  })
}

export function useUpdateAiAgent() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, config }: { id: number; config: AiAgentConfigUpdate }) =>
      api.updateAiAgent(id, config),
    onSuccess: (data) => {
      queryClient.setQueryData(aiAgentKeys.detail(data.id), data)
      queryClient.invalidateQueries({ queryKey: aiAgentKeys.lists() })
      queryClient.invalidateQueries({ queryKey: aiAgentKeys.enabled() })
    },
  })
}

export function useDeleteAiAgent() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => api.deleteAiAgent(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: aiAgentKeys.lists() })
      queryClient.invalidateQueries({ queryKey: aiAgentKeys.enabled() })
    },
  })
}

export function useDiscoverModels() {
  return useMutation({
    mutationFn: (request: DiscoverModelsRequest) => api.discoverModels(request),
  })
}

export function useRefreshAgentModels() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => api.refreshAgentModels(id),
    onSuccess: (models, id) => {
      // Update the cached models in the agent detail
      queryClient.setQueryData<AiAgentConfig | undefined>(
        aiAgentKeys.detail(id),
        (old) => old ? { ...old, cachedModels: models } : old
      )
      queryClient.invalidateQueries({ queryKey: aiAgentKeys.lists() })
    },
  })
}

export function useUpdateAgentDefaults() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (defaults: AgentDefaults) => api.updateAgentDefaults(defaults),
    onSuccess: (data) => {
      queryClient.setQueryData(aiAgentKeys.defaults(), data)
    },
  })
}

// ============================================================================
// Security Queries & Mutations
// ============================================================================

export function useSecurityStatus() {
  return useQuery({
    queryKey: securityKeys.status(),
    queryFn: api.getSecurityStatus,
    staleTime: 30000, // Check every 30 seconds
  })
}

export function useUnlockEncryption() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (password: string) => api.unlockEncryption(password),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: securityKeys.status() })
      queryClient.invalidateQueries({ queryKey: aiAgentKeys.all })
    },
  })
}

export function useLockEncryption() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => api.lockEncryption(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: securityKeys.status() })
    },
  })
}
