import { onScopeDispose } from 'vue'
import { CampusMapClient } from '../../../../mobile-core/src/api/map-client'
import { createWebTransport } from '../../app/api'
import { useWebLiveRefresh } from '../../app/live'
import { createMapState } from './map-state'

export function useWebMap() {
  const transport = createWebTransport()
  const state = createMapState(new CampusMapClient(transport), transport.assertCurrent)
  onScopeDispose(state.dispose)
  useWebLiveRefresh(state.load)
  void state.load()
  return state
}
