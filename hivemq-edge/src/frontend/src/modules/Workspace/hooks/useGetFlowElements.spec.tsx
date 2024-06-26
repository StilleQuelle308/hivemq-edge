import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, beforeEach, afterEach, vi } from 'vitest'
import { WithCSSVar } from '@chakra-ui/react'
import { Dict } from '@chakra-ui/utils'

import { server } from '@/__test-utils__/msw/mockServer.ts'
import { handlers } from '@/__test-utils__/msw/handlers.ts'
import { MOCK_THEME } from '@/__test-utils__/react-flow/utils.ts'
import { SimpleWrapper } from '@/__test-utils__/hooks/SimpleWrapper.tsx'
import queryClient from '@/api/queryClient.ts'

import '@/config/i18n.config.ts'

import { EdgeFlowProvider } from './FlowContext.tsx'
import useGetFlowElements from './useGetFlowElements.ts'

// [Vitest] Mocking hooks
vi.mock('@chakra-ui/react', async () => {
  const actual = await vi.importActual('@chakra-ui/react')

  // @ts-ignore
  return { ...actual, useTheme: vi.fn<[], Partial<WithCSSVar<Dict>>>(() => MOCK_THEME) }
})

const wrapper: React.JSXElementConstructor<{ children: React.ReactElement }> = ({ children }) => (
  <SimpleWrapper>
    <EdgeFlowProvider>{children}</EdgeFlowProvider>
  </SimpleWrapper>
)

describe('useGetFlowElements', () => {
  beforeEach(() => {
    window.localStorage.clear()
    server.use(...handlers)
  })

  afterEach(() => {
    server.resetHandlers()
    queryClient.clear()
  })

  it('should be used in the right context', async () => {
    const { result } = renderHook(() => useGetFlowElements(), { wrapper })

    // [Vitest] Need to make sure the async requests have been intercepted
    await waitFor(() => {
      const { nodes } = result.current
      expect(!!nodes.length).toBeTruthy()
    })

    expect(result.current.nodes).toHaveLength(3)
    expect(result.current.edges).toHaveLength(2)
  })

  it('should be used in the right context', async () => {
    const { result } = renderHook(() => useGetFlowElements(), { wrapper })

    expect(result.current.nodes).toHaveLength(0)
    expect(result.current.edges).toHaveLength(0)
  })
})
