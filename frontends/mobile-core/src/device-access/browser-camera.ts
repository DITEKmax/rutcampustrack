import { browserDeviceContextAllows } from './browser-policy'

/** Synchronous entry point: callers invoke this directly in the button's click turn. */
export function requestBrowserCamera(
  video: boolean | MediaTrackConstraints = true,
): Promise<MediaStream> {
  if (!browserDeviceContextAllows('camera'))
    throw new DOMException('Camera blocked by document policy', 'SecurityError')
  if (!globalThis.navigator?.mediaDevices?.getUserMedia)
    throw new DOMException('Camera unsupported', 'NotSupportedError')
  return navigator.mediaDevices.getUserMedia({ video, audio: false })
}
