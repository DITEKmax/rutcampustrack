import { describe, expect, it } from 'vitest'
import { createGenerationBoundHeadmanLessonActionsApi, HeadmanLessonActionsApi } from './headman-journal-actions-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'

describe('lesson action log account ownership', () => {
  it('refuses to publish an old account response after the account changes', async () => {
    let generation = 1
    let finish!: (response: Response) => void
    const response = new Promise<Response>((resolve) => { finish = resolve })
    const api = createGenerationBoundHeadmanLessonActionsApi({ currentGeneration: () => generation, accessTokenFor: () => null, refreshFor: async () => undefined }, async () => response)
    const pending = api.getLessonActions(30)
    generation = 2
    finish(new Response(JSON.stringify({items:[],nextCursor:null})))
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })
  it('does not retry a forbidden old-account response with a new account token', async () => {
    let generation = 1
    let calls = 0
    const api = createGenerationBoundHeadmanLessonActionsApi({currentGeneration:()=>generation,accessTokenFor:()=>null,refreshFor:async()=>{generation=2}},async()=>{calls+=1;return new Response('{}',{status:401})})
    await expect(api.getLessonActions(30)).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(calls).toBe(1)
  })
  it('retains an unknown actual event without deriving a replacement history', async () => {
    const api = new HeadmanLessonActionsApi({accessToken:()=>null,fetcher:async()=>new Response(JSON.stringify({items:[{id:'event-1',occurredAt:'2026-10-06T15:10:00Z',action:'FUTURE_SERVER_EVENT',actor:null,student:null,before:null,after:null,geo:null,ticket:null,homework:null}],nextCursor:'opaque page'}))})
    const page = await api.getLessonActions(30)
    expect(page.items[0]?.action).toBe('FUTURE_SERVER_EVENT')
    expect(page.items[0]?.actor).toBeNull()
    expect(page.nextCursor).toBe('opaque page')
  })
})
